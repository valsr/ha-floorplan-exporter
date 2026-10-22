# Focus box — Design

Date: 2026-10-21
Status: implemented.
Builds on: `2026-10-07-ha-floorplan-exporter-design.md` (referred to below as "the main spec").

## 1. Purpose

The Home Assistant card showing the images crops them to the house: it scales an image so the
house fills the height of the screen and hides what overflows on the sides. To do that it needs
the rectangle of the image the house occupies, the **focus box**. Today that rectangle is measured
by hand on a render and typed into the card's CSS; when the point of view changes, the numbers are
silently wrong and the card is off-centre.

The exporter knows the camera and the walls, so it can work the rectangle out itself and write it
in `manifest.json` next to the images it applies to.

### Success criteria

- `manifest.json` gives, for every exported floor, the focus box in pixels of its images, or
  `null` where none can be computed.
- By default the box is computed: the projection of the walls of the exported floor through the
  camera of the floor, enlarged by a margin and clipped to the image.
- A floor can be given a box by hand, in the dialog and in the instructions file; that box is
  written as is, and wins over the computed one.
- With the `HA: 2nd floor` point of view of the author's home and a margin of 0, the computed box
  lies on the outer walls measured on its render: about x 605–1170, y 175–965 at 1920×1080
  (a few pixels of tolerance).
- `scripts/test.sh` passes.

### Out of scope

- Generating the Home Assistant card from the manifest.
- Computing the box in the dialog or showing it there: the dialog has no access to the home's
  geometry (main spec §4.3) and keeps none.
- Taking hidden parts into account: the box is that of the wall outlines, seen or not.
- Fisheye and spherical lenses.

## 2. Decisions

| Question | Decision |
|---|---|
| Where the box comes from | Computed by default; a manual box per floor overrides it |
| What the computed box covers | The walls of the exported floor only: both their bottom and top, with their thickness |
| How it's computed | Analytic projection of the wall corners (not an extra render, not Java 3D) |
| Margin | One global setting, `focusMargin`, in % of the computed box's size, 0–50, default 5; never applied to a manual box |
| Where the override is edited | The dialog and the instructions file, like the camera of a floor |
| Shape in files | `{"x": [left, right], "y": [top, bottom]}`, integer pixels |

## 3. Formats

### 3.1 Instructions file

A floor takes an optional `focus`; the instructions take an optional `focusMargin`.

```json
"floors": [
  {"level": "Second Floor", "camera": "HA: 2nd floor", "focus": {"x": [550, 1250], "y": [150, 1050]}}
],
"focusMargin": 5
```

| Key | Required | Default | Meaning |
|---|---|---|---|
| `floors[].focus` | no | computed | Focus box of the floor's images: `x` is `[left, right]`, `y` is `[top, bottom]`, in pixels |
| `focusMargin` | no | `5` | Margin added around a computed box, in % of its width (left and right) and of its height (top and bottom), from 0 to 50 |

Validation, with the problems reported like the other ones of an instructions file:

- `focus` must be an object with exactly the keys `x` and `y`, each an array of two integers.
- `left < right`, `top < bottom`, `0 ≤ left`, `0 ≤ top`, `right ≤ width`, `bottom ≤ height`, where
  `width` and `height` are those of the instructions (after defaults).
- `focusMargin` must be a number from 0 to 50.
- With `"floors": "*"` there's no floor object to carry a box: every floor is computed.

The dialog writes `focus` only for a floor that has a manual box, and always writes `focusMargin`.

### 3.2 manifest.json

```json
{
  "focusMargin": 5,
  "floors": [
    {
      "id": "level-…", "name": "Second Floor", "slug": "second-floor", "camera": "HA: 2nd floor",
      "focus": {"x": [550, 1250], "y": [150, 1050], "source": "manual"},
      "base": […], "night": "…", "lights": […]
    }
  ]
}
```

- `focusMargin` sits after `exposure` among the settings; `focus` sits after `camera` in a floor.
- `source` is `"manual"` or `"auto"`.
- A computed box has its left and top rounded down and its right and bottom rounded up, after the
  margin, then is clipped to `[0, width]` × `[0, height]`.
- `focus` is `null` when no box is computed (§4.3); a reader then uses the whole image.

The manifest stays written last (main spec §6): the boxes are worked out before it's written and
don't change that order.

## 4. Computation

### 4.1 Geometry — `engine/HomeInspector`

`HomeInspector.getWallCorners(Home home, String floorId)` returns the corners as a list of
`double[] {x, y, elevation}` in centimetres, Sweet Home 3D plan coordinates:

- Walls: those whose level is the floor `floorId`; in a home without levels
  (`HomeSummary.DEFAULT_FLOOR_ID`), every wall, at elevation 0.
- Outline: `wall.getPoints()`, which includes the thickness and approximates an arc wall.
- Each outline point gives a bottom corner at the level's elevation and a top corner at that
  elevation plus the wall height: `wall.getHeight()`, or `home.getWallHeight()` when it's `null`.
  A sloping wall (`getHeightAtEnd()` not `null`) uses the larger of its two heights for all of its
  top corners, which can only make the box larger.

### 4.2 Projection — `plan/FocusProjector`

Pure Java, no Sweet Home 3D class, so it's unit-tested on its own. It reproduces the pinhole camera
the Blender GPU renderer builds from a Sweet Home 3D camera (`BlenderRenderer.createRenderCommand`),
which is also the model of Sweet Home 3D's other renderers:

- 3D space is (x, elevation, y) of the plan. The camera is at `(camera.x, camera.z, camera.y)`.
- Looking direction `f = (−sin yaw·cos pitch, −sin pitch, cos yaw·cos pitch)`;
  up `u = (−sin yaw·sin pitch, cos pitch, cos yaw·sin pitch)`; right `r` is the cross product of
  `f` and `u` taken in the order that puts positive `x_c` on the right of the image (fixed by test).
- The field of view is horizontal and pixels are square, so with
  `k = (W/2) / tan(fov/2)`, a point `d` from the camera with
  `x_c = d·r`, `y_c = d·u`, `z_c = d·f` lands at
  `px = W/2 + k·x_c/z_c`, `py = H/2 − k·y_c/z_c`.
- The box is the min/max of the corners' `px` and `py`; the margin then adds
  `margin/100 × (right − left)` on each side horizontally and `margin/100 × (bottom − top)`
  vertically; then rounding and clipping as in §3.2.

Interface:

```java
public final class FocusProjector {
    /** Returns the box, or null if it can't be computed. */
    public static FocusBox project(double x, double y, double z, double yaw, double pitch,
                                   double fieldOfView, int width, int height,
                                   List<double[]> corners, double marginPercent);
}
```

`FocusBox` (in `plan`) is an immutable value: `left`, `top`, `right`, `bottom` as `int`, with
`equals`/`hashCode`, used for both manual and computed boxes.

### 4.3 No box

`project` returns `null`, and the manifest has `"focus": null`, when:

- there are no corners (no walls on the floor);
- a corner has `z_c ≤ 0`, i.e. is level with or behind the camera, where the projection doesn't
  hold;
- the box clipped to the image is empty.

The lens is checked by the exporter: a camera with a `FISHEYE` or `SPHERICAL` lens gets no
computed box.

### 4.4 Where it runs — `engine/Exporter`

When it writes the manifest, the exporter takes for each floor its manual box if it has one
(`source: "manual"`), otherwise projects the wall corners of the floor through the very camera
copy the floor's images were rendered with (`SceneConfigurer.camera`), with the configured margin
(`source: "auto"`). It hands `ManifestWriter.write` a map from floor id to a box and its source;
`ManifestWriter` only writes it.

## 5. Settings plumbing

- `Instructions.Floor` gains a nullable `FocusBox focus` (part of `equals`/`hashCode`);
  `Instructions` gains `focusMargin` (`double`, default 5).
- `InstructionsJson` reads and writes both (§3.1); `focus` and `focusMargin` join the allowed keys.
- `InstructionsResolver` checks a manual box against the resolved `width`/`height` and copies
  both into `ExportConfig`. Loading instructions made for another home keeps a floor's box when
  its level matches, like its camera.
- `ExportConfig.Floor` gains the nullable `FocusBox focus`; `ExportConfig` gains `focusMargin`.
- The settings stored in the home are the instructions written by the dialog (main spec §4.3), so
  the box and the margin are kept there without other change.

## 6. Dialog — `plugin/ExportDialog`

- **Floors**: each floor row gets a third column, a button reading **Focus: Auto** or
  **Focus: 550–1250 × 150–1050**. It opens a small modal dialog with a **Set the box by hand**
  check box and four spinners, Left, Right, Top and Bottom, bounded by the current width and
  height and enabled only when the box is ticked. They start from the floor's last manual box, or
  the whole image. OK keeps the choice, Cancel drops it; unticking returns the floor to Auto.
  Like the camera list, the button is enabled only while its floor is ticked.
- **Options**: a **Focus margin (%)** spinner, 0 to 50 by 1, after Exposure, with a tooltip saying
  it applies only to computed boxes.
- **Errors**: a selected floor whose manual box doesn't fit the current width and height adds an
  error to the list the dialog shows, which disables **Export** (and **Save instructions…**) like
  the other errors.
- New texts go in `Messages.properties`.

## 7. Testing

- `FocusProjectorTest`: a corner straight ahead lands in the image centre; a corner to the right
  of the camera lands right of centre and one above it lands above (fixes the orientation); a
  yaw of π/2 gives the same box as rotating the corners; margin arithmetic; outward rounding;
  clipping; `null` for no corners, a corner behind the camera, and an empty clipped box.
- `HomeInspectorTest`: corners of a straight wall with thickness on a raised level, default wall
  height, sloping wall, walls of other levels left out, home without levels.
- `InstructionsJsonTest`: reading and writing `focus` and `focusMargin`; refusing a malformed box,
  an inverted box and an out-of-range margin.
- `InstructionsResolverTest`: a box outside the image is refused; a box survives resolution.
- `ManifestWriterTest`: `focusMargin`, a manual box, an auto box and a `null` box are written where
  §3.2 says.
- `ExporterTest` (fake backend): a manual box wins with `source: "manual"`; a floor without one
  gets `source: "auto"`; a fisheye camera gets `null`.
- `ExportDialogTest`: a floor with a manual box comes back unchanged from the dialog's
  instructions; a box larger than the image size shows an error and disables Export.
- Manual check against the real home (§1 success criteria), computed with margin 0.

## 8. Documentation

README: the `focus` and `focusMargin` rows of the instructions table, the `focus` key in the
description of `manifest.json`, and the two new dialog controls.
