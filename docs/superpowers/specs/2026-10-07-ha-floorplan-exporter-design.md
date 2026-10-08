# HA Floorplan Exporter — Design

Date: 2026-10-07
Status: approved, amended 2026-10-07 (level isolation, JSON instructions file,
Blender GPU renderer); implementation not started

## 1. Purpose

A Sweet Home 3D plugin that batch-renders a home into a set of images meant to
be stacked as layers in Home Assistant with
[ha-floorplan](https://experiencelovelace.github.io/ha-floorplan/).

For each selected floor it produces:

- **Base images** for every selected date and time of day, with all lights off.
- **Light overlays**: one transparent PNG per selected light, holding only that
  light's contribution, to be shown on top of a base image when the light is on.

All images of one floor share the same camera and pixel size, so they align
pixel-for-pixel.

### Success criteria

- From the Tools menu the user can select floors, a date range, a time range
  and lights (with All / None), and get the output folder described in §6.
- The user can choose to render each floor on its own, with every other level
  hidden.
- The dialog can save its settings as a JSON instructions file (§5) and load
  one back.
- The same export can be run from the command line with nothing but that
  instructions file, and produces the same output as the dialog.
- The open home document is never modified by an export.
- `scripts/test.sh` and `scripts/export-sample.sh` pass on a machine with a JDK
  and Sweet Home 3D 7.5 installed.

### Out of scope

- Generating the ha-floorplan SVG or Home Assistant YAML. `manifest.json` (§6)
  exists so a later tool can do this.
- Per-date or per-time light overlays. Lights are rendered once, at night.
- The two non-ray-traced quality levels of Sweet Home 3D's photo dialog.
- Automatic camera placement. The user supplies viewpoints.

## 2. Decisions

| Topic | Decision |
|---|---|
| Viewpoint | One stored camera per floor, chosen by the user from the home's stored viewpoints, or "current 3D view". |
| Light output | Difference overlay per light against an all-lights-off base. The base is the "off" state. |
| Combination | Base = floor × date × time. Overlays = one per light, on the light's own floor, rendered at night. |
| Renderer | User chooses among `AbstractPhotoRenderer.getAvailableRenderers()` and quality `LOW` or `HIGH`. That list is SunFlow and YafaRay, plus our own "Blender Cycles (GPU)" renderer (`/work/sh3d/gpu-renderer`) when its Java agent is loaded. The exporter has no compile-time or packaging dependency on it. |
| Renderer reuse | One renderer instance per scene state, not per job: all of a floor's lights-off renders share one instance, each light render gets its own (§4.2). |
| Level isolation | One global switch, `isolateLevel`, default off. Off: a floor is rendered with the levels below it. On: only that floor's level is rendered. |
| Instructions | One JSON format (§5) for the CLI input, the file saved and loaded by the dialog, and the dialog state stored in the home. No `.properties` config. |
| Home path | The instructions file may name its `.sh3d`; a home given on the command line overrides it. |
| Structure | UI-free engine with two front ends: plugin dialog and headless CLI. |
| Build | Plain shell scripts over `javac` and `jar`. No Maven or Gradle. |
| Repo | Public GitHub repo `valsr/ha-floorplan-exporter`, licence GPL-2.0-or-later. Created during implementation. |

## 3. Environment and constraints

- Target: Sweet Home 3D 7.5. Developed against the Arch/Manjaro package:
  - Jars in `/usr/share/java/sweethome3d/` (`SweetHome3D.jar`,
    `sunflow-0.07.3i.jar`, `Furniture.jar`, …).
  - Java 3D in `/usr/lib/sweethome3d/java3d-1.5/` (`j3dcore.jar`,
    `j3dutils.jar`, `vecmath.jar`, native `.so`).
  - YafaRay natives in `/usr/lib/sweethome3d/yafaray/`.
  - User plugins in `~/.eteks/sweethome3d/plugins/`.
- **Blender GPU renderer** (`sh3d.gpurenderer.BlenderRenderer`, sibling
  project `/work/sh3d/gpu-renderer`, jar `build/gpu-renderer.jar`, installed
  location `/usr/lib/sweethome3d/gpu-renderer/gpu-renderer.jar`):
  - It registers itself through `-javaagent:gpu-renderer.jar`, which appends
    its class to the system property
    `com.eteks.sweethome3d.j3d.rendererClassNames` and puts the jar on the
    class path. Inside Sweet Home 3D the user's launcher already does this, so
    the plugin sees the renderer with no extra work. The CLI launcher must add
    the agent itself (§4.4).
  - It needs Blender ≥ 4.0 on `PATH` (or `-Dsh3d.gpurenderer.blender=…`) and
    reports `isAvailable() == false` otherwise.
  - It exports the home once and keeps a Blender process loaded until
    `dispose()`; a later `render` call with a different camera or camera time
    reuses the loaded scene. Creating a new instance per render would restart
    Blender and re-export the home every time.
  - Quality maps to Cycles samples (`LOW` 64, `HIGH` 256). It requires a JVM
    17 or later, which does not constrain our own `--release 8` classes.
- Renderers are identified by **class name**; the display name comes from
  `getName()`. `AbstractPhotoRenderer.createInstance` silently falls back to
  SunFlow for a class it cannot load, so availability must be checked
  explicitly (§4.2).
- JDK 26 is installed. `javac --release 8` still works (with an "obsolete"
  warning); compile with `--release 8 -Xlint:-options` so the plugin loads on
  any JVM that Sweet Home 3D 7.5 supports. Therefore: **no Java language
  features newer than Java 8** in `src/main` (no `var`, records, text blocks,
  `List.of`).
- No third-party runtime dependencies. JSON is read and written by a small
  hand-rolled `Json` class (§4.1); it serves both the instructions file and
  the manifest.
- Sweet Home 3D on JDK 17+ needs
  `--add-opens=java.desktop/sun.awt=ALL-UNNAMED` (the system launcher adds it;
  our headless script must add it too).

## 4. Architecture

Package root: `io.github.valsr.hafloorplan`.

```
plan/     pure logic, no Sweet Home 3D imports
engine/   runs a plan against a Home
plugin/   Sweet Home 3D plugin + Swing dialog
cli/      headless entry point
```

Dependencies point one way: `plugin` → `engine` → `plan`, `cli` → `engine` → `plan`.

### 4.1 `plan` — pure logic

| Type | Responsibility |
|---|---|
| `ExportConfig` | Immutable, fully resolved value: floors (level id + camera id, or "current view"), date schedule, time schedule, light ids, width, height, renderer class name, quality, hide-ceilings flag, isolate-level flag, noise threshold, output dir. |
| `DateSchedule` | start date, end date, interval in days → ordered list of `LocalDate`, both ends inclusive. |
| `TimeSchedule` | start time, end time, interval in minutes → ordered list of `LocalTime`, both ends inclusive. |
| `RenderJob` | One render: floor id, kind (`BASE`, `NIGHT_BASE`, `LIGHT`), date + time (for `BASE`), light id (for `LIGHT`), relative output path. |
| `ExportPlanner` | `plan(ExportConfig, HomeSummary) → List<RenderJob>` in execution order. |
| `HomeSummary` | Plain data the resolver and planner need: floors (id, name, elevation index), lights (id, name, floor id), stored cameras (id, name) and available renderers (class name, display name). Built by the engine from a `Home` and a `RenderBackend`; built by hand in tests. |
| `Slugs` | Name → filesystem-safe slug, with numeric suffixes for collisions (`lamp`, `lamp-2`). |
| `Json` | Minimal JSON reader and writer. Reads into `Map` / `List` / `String` / `Double` / `Boolean` / `null`; parse errors carry line and column. |
| `Instructions` | Immutable value mirroring the instructions file (§5): the same settings as `ExportConfig`, but floors, cameras and lights are still unresolved references, and with the optional home path. |
| `InstructionsJson` | `Instructions` ⇄ JSON text. Applies defaults, rejects unknown keys and wrong types. |
| `InstructionsResolver` | `resolve(Instructions, HomeSummary)` → `ExportConfig` plus a list of problems (unknown or ambiguous references). `toInstructions(ExportConfig, HomeSummary)` for the reverse direction, writing both id and name. |
| `ManifestWriter` | Writes `manifest.json` from the config, summary and job list, using `Json`. |

Schedule rules:

- Interval must be ≥ 1; end must not be before start. Violations throw
  `IllegalArgumentException` with a user-readable message.
- If the interval does not land on the end, the last value is the last step
  not after the end (00:00–23:00 every 4 h → 0, 4, 8, 12, 16, 20).
- Start = end yields one value.

Job order produced by `ExportPlanner`, per floor: all `BASE` jobs (date major,
time minor), then one `NIGHT_BASE` job if the floor has at least one selected
light, then one `LIGHT` job per selected light on that floor. A light is
planned only for the floor it sits on; selected lights on unselected floors
are skipped and listed in the manifest's `skippedLights`. A selected light
whose power is 0 in the home is skipped the same way: it would cost a render
and give an empty overlay. Each entry of `skippedLights` has the light's `id`,
`name` and a `reason` (`"off"` or `"not on an exported floor"`).

### 4.2 `engine`

| Type | Responsibility |
|---|---|
| `HomeInspector` | `Home` → `HomeSummary`. Collects levels and all `HomeLight`s, recursing into `HomeFurnitureGroup`s. Homes without levels are treated as one floor with id `default`. |
| `SceneConfigurer` | Mutates the **cloned** home for a job: visible floor, ceilings, light powers, camera. |
| `RenderBackend` | Interface: `List<RendererInfo> availableRenderers()` (class name + display name) and `RenderSession open(Home, rendererClassName, quality)`. Lets tests substitute a fake renderer. |
| `RenderSession` | One renderer instance bound to one scene state: `BufferedImage render(Camera, int width, int height)`, `stop()`, `close()`. The home must not be mutated while a session is open, apart from the camera passed to `render`. |
| `Sh3dRenderBackend` | Real implementation: `AbstractPhotoRenderer.createInstance(className, home, null, quality)`, `render(image, camera, null)`, and `dispose()` on close. |
| `OverlayDiff` | Pure image function: `(nightBase, lit, threshold) → ARGB overlay`. |
| `Exporter` | Orchestrates: clone home, iterate jobs, write PNGs, write manifest, report progress, honour cancellation. |
| `ExportListener` | `jobStarted(index, total, job)`, `jobFinished(...)`; `Exporter.cancel()` is thread-safe. |

**Cloning.** `Exporter` calls `home.clone()` once and only ever touches the
clone. Original light powers are read from the clone before they are zeroed.

**Floor visibility.** Sweet Home 3D does not hide levels inside the
renderers; `LevelController` sets the transient `Level.setVisible` flag and
all three renderers test `Level.isViewableAndVisible()`. The exporter does the
same on the clone. For a floor F, walking `home.getLevels()` in order: levels
up to and including F get `setVisible(true)`, the ones after it
`setVisible(false)`. `Level.setViewable` is never changed, so a level the user
marked as not viewable stays hidden. `setSelectedLevel(F)` is also called so
anything reading the selection agrees.

**Level isolation.** When `isolateLevel` is true, F gets `setVisible(true)`
and every other level `setVisible(false)`, so that nothing below F is rendered
either (no lower floor seen through a stairwell, no furniture or light from
below). Consequences, all intended:

- "Other levels" means every other `Level` object, including one that shares
  F's elevation.
- With the levels below hidden, whatever F has no room floor over (a
  stairwell, a void) shows the ground or sky instead of the floor below.
- The switch applies to all job kinds of the floor (`BASE`, `NIGHT_BASE`,
  `LIGHT`), so overlays still align with and difference correctly against
  their night base.
- Homes without levels ignore the switch.

**Ceilings.** When `hideCeilings` is true, `Room.setCeilingVisible(false)` on
every room of floor F.

**Lights.** Identified by `HomeObject.getId()`. For `BASE` and `NIGHT_BASE`
every `HomeLight` has power 0. For a `LIGHT` job the target light gets its
original power, all others 0.

**Camera.** The camera for a floor is a clone of the chosen stored camera (or
of `home.getCamera()` for "current view"). Per job, set its time with
`Camera.setTime`. The time value follows Sweet Home 3D's convention: build the
local date + time in the compass time zone (`home.getCompass().getTimeZone()`)
and convert the way the photo dialog does (`Camera.convertTimeToTimeZone`).
Implementation must verify against the photo dialog that 12:00 in the dialog
and 12:00 in an export give the same sun position.

**Night time.** `NIGHT_BASE` and `LIGHT` jobs use local 00:00 on the first
date of the schedule. If `Compass.getSunElevation(time) ≥ 0` there (polar
summer), step through that day in 1-hour increments and use the time with the
lowest elevation; record `nightSunElevation` in the manifest.

**Renderer selection.** The config's renderer is validated before any render:
its class must be in `getAvailableRenderers()`, `createInstance` must return
an instance of exactly that class (not the SunFlow fallback), and that
instance's `isAvailable()` must be true. Otherwise the export fails up front
with a message naming the renderer (for the Blender renderer: "is the
gpu-renderer agent loaded and Blender installed?").

**Renderer lifetime.** A renderer loads the scene at its first render and
then only follows the camera, including the camera's time (all three
recompute the sun from it on every render). Updating light sources through
`render`'s `updatedItems` argument is not reliable across renderers (SunFlow
re-exports the lamp's model but not its light sources), so a changed light
means a new instance. Per floor the exporter therefore opens:

1. one `RenderSession` with every light off, used for all `BASE` jobs and the
   `NIGHT_BASE` job, then closed;
2. one `RenderSession` per `LIGHT` job, opened after that light's power is
   set, closed after its single render.

With the Blender renderer this means one Blender start and one scene export
for all of a floor's base images, and one per light. Image type is
`BufferedImage.TYPE_INT_ARGB`; base images are written as opaque PNG.

**Overlay maths.** For each pixel, base `B` and lit `L` per channel (0–255):

1. `d = max over channels of (L − B)`. If `d ≤ threshold` (default 6), the
   pixel is fully transparent.
2. `a = max over channels of (L − B) / (255 − B)`, treating a channel with
   `B = 255` as contributing 0; clamp `a` to (0, 1].
3. Overlay colour per channel `C = clamp(B + (L − B) / a, 0, 255)`.
4. Overlay pixel = `C` with alpha `round(a × 255)`.

This is the minimal-alpha solution of `B·(1 − a) + C·a = L`, so compositing
the overlay on the night base reproduces the lit render. Over a daytime base
it is an approximation; this is an accepted trade-off.

**Errors and cancellation.**

- Config problems (unknown floor, camera or light id; unavailable renderer;
  unwritable output dir) are detected before any render starts and reported as
  one `ExportException` listing all problems.
- Sessions are closed in a `finally`, so a failed or cancelled export never
  leaves a Blender process or its temporary folder behind.
- A failure during a render aborts the export; files already written are
  kept; no manifest is written, so a missing manifest marks an incomplete
  export.
- An export starts by deleting the `manifest.json` of a previous export into
  the same folder, so a manifest never describes a folder that a failed or
  cancelled run left half-updated.
- On the command line the only way to cancel is to interrupt the process: a
  shutdown hook stops and closes the open session, and the exit code is 1.
- Cancel calls `RenderSession.stop()` on the open session, stops after the current job, same
  outcome as a failure but reported as cancelled.
- Existing files in the output dir are overwritten. The dialog warns if the
  directory is not empty.

### 4.3 `plugin`

- `HaFloorplanPlugin extends Plugin`, returns one `PluginAction` in the Tools
  menu: "Export for HA Floorplan…".
- `ApplicationPlugin.properties` at the jar root: `name`, `class`,
  `description`, `version`, `license=GPL-2.0-or-later`, `provider`,
  `applicationMinimumVersion=7.0`, `javaMinimumVersion=1.8`.
- `ExportDialog` (Swing):
  - Floors: checklist; each row has a viewpoint combo (stored cameras +
    "Current 3D view").
  - Dates: start, end, interval (days). Times: start, end, interval (minutes).
  - Lights: checklist grouped by floor, with **All** and **None** buttons.
  - Renderer combo listing `RenderBackend.availableRenderers()` by display
    name (so "Blender Cycles (GPU)" appears whenever Sweet Home 3D was started
    with the agent); a renderer whose `isAvailable()` is false is left out.
  - Width, height, quality (Low / High), "Hide ceilings"
    (default on), "Hide other levels" (default off; disabled for a home
    without levels).
  - Output folder chooser.
  - **Save instructions…** and **Load instructions…** buttons (below).
  - Live summary: "N base renders + M light renders".
  - Validation errors shown inline; Export disabled while invalid.
- `ExportProgressDialog`: progress bar, current job label, Cancel. The export
  runs on a background thread (`SwingWorker`); completion, failure and
  cancellation each show a message.
- **Save instructions…** writes the current settings as an instructions file
  (§5) through a file chooser (default name `<home name>.ha-floorplan.json`).
  It is enabled under the same condition as Export, so a saved file is always
  runnable. Floors, cameras and lights are written with both id and name;
  `home` is the absolute path of the open home, omitted if the home has never
  been saved; `output` is absolute. A floor using "Current 3D view" is written
  without a `camera`. If the home is unsaved or has unsaved changes, the
  confirmation message says that the command line reads the home from disk, so
  the home must be saved first for the two to match.
- **Load instructions…** reads an instructions file and replaces the dialog's
  settings. References that do not resolve in the open home (the file may come
  from another home) are dropped and listed in one warning; a file that fails
  to parse changes nothing and shows the parse error. The file's `home` field
  is ignored.
- Dialog state is saved to the real home as one property,
  `haFloorplanExporter.instructions` (the same JSON, without `home`), via
  `Home.setProperty`, and restored on next open with the same leniency as
  Load. This is the only write to the open home; it marks the home modified,
  which is intended so the settings are saved with the file.
- User-visible strings live in a `ResourceBundle` (English only for now).

### 4.4 `cli`

`HeadlessExport` main:

```
HeadlessExport <instructions.json | -> [--home <home.sh3d>] [--output <dir>]
```

- The instructions come from the named file, or from standard input when the
  argument is `-`.
- The home is `--home` if given, otherwise the file's `home` field; having
  neither is an argument error. `--output` likewise overrides `output`.
- Relative `home` and `output` paths inside the file resolve against the
  file's directory (the working directory when reading standard input).
  Paths given on the command line resolve against the working directory.

It loads the home with `HomeFileRecorder.readHome`, resolves the instructions
against it, runs `Exporter` and prints one line per job. Unlike the dialog's
Load, resolution here is strict: every unknown or ambiguous reference is an
error, and all of them are reported together before anything is rendered.
Exit codes: 0 success, 1 export failure, 2 bad arguments or instructions.

`scripts/export.sh` wraps this for real use: it sets the classpath, the Java
3D library path and the `--add-opens` flag, then passes its arguments through.

It also loads the Blender GPU renderer when it can find it. `scripts/env.sh`
sets `SH3D_GPU_RENDERER_JAR` to the first of these that exists, unless the
variable is already set (set it empty to disable):

1. `/usr/lib/sweethome3d/gpu-renderer/gpu-renderer.jar` (installed),
2. `../gpu-renderer/build/gpu-renderer.jar` relative to this repo (sibling
   checkout).

If a jar is found and `JAVA_TOOL_OPTIONS` does not already name a
`gpu-renderer.jar` agent, `export.sh` adds `-javaagent:<jar>`. Without the jar
only SunFlow and YafaRay are available, and instructions asking for the
Blender renderer fail with the message from §4.2.

```
scripts/export.sh house.ha-floorplan.json
scripts/export.sh job.json --home other.sh3d --output /tmp/out
```

The export needs a display even though it opens no window: Java 3D, which
every renderer uses to build the scene, does not load under
`-Djava.awt.headless=true` (§9). `scripts/export.sh` therefore runs with the
current `DISPLAY`; on a machine without one, `xvfb-run scripts/export.sh …`
works.

## 5. Instructions file format

One JSON object. The dialog writes it, the CLI and the dialog read it.

```json
{
  "version": 1,
  "home": "house.sh3d",
  "output": "out",
  "floors": [
    {
      "level": {"id": "level-…", "name": "Ground floor"},
      "camera": {"id": "camera-…", "name": "Top ground"}
    },
    {"level": "First floor", "camera": "Top first"}
  ],
  "dates": {"start": "2026-01-01", "end": "2026-12-31", "intervalDays": 30},
  "times": {"start": "00:00", "end": "23:00", "intervalMinutes": 240},
  "lights": "*",
  "width": 1920,
  "height": 1080,
  "renderer": "sh3d.gpurenderer.BlenderRenderer",
  "quality": "LOW",
  "hideCeilings": true,
  "isolateLevel": false,
  "noiseThreshold": 6
}
```

| Key | Required | Default | Notes |
|---|---|---|---|
| `version` | yes | | Must be `1`. |
| `home` | no | | Path of the `.sh3d`. See §4.4 for precedence and relative paths. |
| `output` | unless `--output` | | Output directory. |
| `floors` | yes | | Array of `{level, camera}`, or `"*"` for every floor with the home's current view. A missing `camera` means the current view (the camera saved in the home file). |
| `dates` | yes | | `start`, `end` as `YYYY-MM-DD`, `intervalDays` ≥ 1. |
| `times` | yes | | `start`, `end` as `HH:mm`, `intervalMinutes` ≥ 1. |
| `lights` | no | `"*"` | Array of references, or `"*"` for all lights. `[]` for none. |
| `width`, `height` | no | 1920, 1080 | Pixels. |
| `renderer` | no | first available | Matched, ignoring case, against each available renderer's class name, simple class name and display name: `"sh3d.gpurenderer.BlenderRenderer"`, `"BlenderRenderer"` and `"Blender Cycles (GPU)"` are the same renderer. The dialog writes the full class name. |
| `quality` | no | `"LOW"` | `"LOW"` or `"HIGH"`. |
| `hideCeilings` | no | `true` | |
| `isolateLevel` | no | `false` | Render each floor with every other level hidden (§4.2). |
| `noiseThreshold` | no | `6` | 0–255, see overlay maths. |

**References.** A level, camera or light is given either as a string or as
`{"id": …, "name": …}` (either member optional, at least one present).

- A string matches an object's id, and failing that its name. This is the
  form for hand-written files, since ids are not visible in Sweet Home 3D.
- An object is matched by `id` first, then by `name` if the id is absent from
  the home. The dialog writes this form, so a file keeps working after an
  object is renamed and still makes sense when loaded into another home.
- A name that matches more than one object is an error (CLI) or a dropped
  reference (dialog Load).

**Strictness.** Unknown keys, wrong types and an unsupported `version` are
errors, so a misspelt key is reported rather than silently ignored.

## 6. Output layout

```
<output>/
  manifest.json
  <floor-slug>/
    base/<YYYY-MM-DD>_<HHmm>.png
    night.png                      # all-off night base, only if the floor has lights
    lights/<light-slug>.png
```

`manifest.json`:

```json
{
  "generator": "ha-floorplan-exporter 0.1.0",
  "width": 1920, "height": 1080,
  "renderer": "sh3d.gpurenderer.BlenderRenderer",
  "rendererName": "Blender Cycles (GPU)", "quality": "LOW",
  "hideCeilings": true, "isolateLevel": false,
  "dates": ["2026-01-01", "2026-01-31"],
  "times": ["00:00", "04:00"],
  "nightTime": "2026-01-01T00:00", "nightSunElevation": -58.2,
  "floors": [
    {
      "id": "level-…", "name": "Ground floor", "slug": "ground-floor",
      "camera": "Top ground",
      "base": [{"date": "2026-01-01", "time": "00:00", "file": "ground-floor/base/2026-01-01_0000.png"}],
      "night": "ground-floor/night.png",
      "lights": [{"id": "light-…", "name": "Kitchen lamp", "slug": "kitchen-lamp", "file": "ground-floor/lights/kitchen-lamp.png"}]
    }
  ],
  "skippedLights": []
}
```

## 7. Repository layout

```
LICENSE                     GPL-2.0-or-later
README.md                   install, usage, output layout, development
.gitignore                  build/, lib/, out/
src/main/java/io/github/valsr/hafloorplan/{plan,engine,plugin,cli}/
src/main/resources/ApplicationPlugin.properties
src/main/resources/io/github/valsr/hafloorplan/plugin/Messages.properties
src/test/java/io/github/valsr/hafloorplan/...
scripts/env.sh              shared: SH3D_JARS, SH3D_LIB, classpath, version
scripts/build.sh            compile + package build/HaFloorplanExporter-<version>.sh3p
scripts/test.sh             fetch JUnit console jar to lib/ if missing, run unit tests
scripts/install.sh          copy the .sh3p to ~/.eteks/sweethome3d/plugins (replacing older versions)
scripts/run.sh              build + install + launch sweethome3d [file]
scripts/export.sh           run HeadlessExport: export.sh <instructions.json> [--home …] [--output …]
scripts/export-sample.sh    end-to-end export of a generated sample home, through export.sh
docs/superpowers/specs/     this document
```

`scripts/env.sh` defaults `SH3D_JARS=/usr/share/java/sweethome3d` and
`SH3D_LIB=/usr/lib/sweethome3d`, both overridable from the environment, and
fails with a clear message if `SweetHome3D.jar` is not found. The version
lives in one place (`VERSION` file) and is substituted into
`ApplicationPlugin.properties` and the manifest generator string at build.

The `.sh3p` is a jar containing the compiled `plan`, `engine`, `plugin` and
`cli` classes plus resources. Sweet Home 3D's own jars are compile-only and
are not bundled.

## 8. Testing

**Unit tests** (JUnit 5 via the console standalone jar, downloaded by
`test.sh` into the git-ignored `lib/`; no Sweet Home 3D rendering involved):

- `DateSchedule`, `TimeSchedule`: inclusive ends, non-aligned end, single
  value, invalid input.
- `ExportPlanner`: job count and order; lights only on their own floor; no
  `NIGHT_BASE` for floors without selected lights; skipped lights.
- `Slugs`: unsafe characters, collisions, empty names.
- `Json`: round trip of every value type, string escapes, nested structures,
  malformed input reports line and column.
- `InstructionsJson`: round trip, defaults, `*` wildcards, both reference
  forms, missing required keys, unknown keys, wrong types, bad `version`.
- `InstructionsResolver`: renderer matched by class name, simple name and
  display name; id match, name match, id-then-name fallback,
  ambiguous name, unknown reference, all problems reported together;
  `toInstructions` followed by `resolve` returns the original config.
- `ManifestWriter`: output parses as JSON and matches the plan (string
  escaping included).
- `OverlayDiff`: identical images → fully transparent; known brightening →
  compositing overlay on base reproduces lit image within ±1; threshold
  behaviour; saturated base channel.
- `Exporter` with a fake `RenderBackend` and a small in-memory `Home`: files
  written to the planned paths, light powers set as specified per job,
  original home unchanged, cancellation leaves no manifest. With
  `isolateLevel` on, the home handed to the backend for each job has only
  that job's level visible; with it off, that level and the ones before it.
  Session use: one session per floor for all `BASE` + `NIGHT_BASE` jobs, one
  per `LIGHT` job, every session closed, also after a failure or a cancel. An
  unavailable renderer fails before any session is opened.

**End-to-end** (`scripts/export-sample.sh`): `SampleHomeFactory` (test source)
builds a two-level home with one room per level, walls, a stored top-down
camera per level and two lights taken from the default furniture catalog; the
upper room has a floor opening so the lower level is visible through it. The
script writes an instructions file naming the sample home, runs
`scripts/export.sh` on it at 320×240, `LOW` quality, 1 date × 2 times, then
asserts:

- every file listed in `manifest.json` exists and is 320×240;
- each light overlay has both fully transparent pixels and non-transparent
  pixels;
- a noon base image is brighter on average than a midnight one;
- a second run with `isolateLevel` set to `true` produces an upper-floor noon
  image that differs from the first run's, and a lower-floor one that does
  not (the lower floor has nothing below it).

`export-sample.sh` uses SunFlow by default so it runs anywhere;
`RENDERER=BlenderRenderer scripts/export-sample.sh` runs the same assertions
through the GPU renderer and is part of the acceptance run on this machine.

**Manual** (`scripts/run.sh`): open a real home, run the dialog, check
All/None, the render count, cancel, and that settings persist after save and
reopen. Save an instructions file from the dialog, run it with
`scripts/export.sh`, and compare the output with the dialog's own export.
Load a file saved from a different home and check the warning.

## 9. Open points, as settled during implementation

1. **Level hiding.** `Level.setVisible(false)` hides a level in SunFlow,
   YafaRay and the Blender renderer (checked with each on the sample home:
   the ground floor shows beside the first floor, and disappears with
   `isolateLevel`).
2. **Camera time.** `Camera.time` holds the local wall-clock time as if it
   were UTC; renderers convert it with `Camera.convertTimeToTimeZone` and the
   compass time zone. The exporter builds it with
   `LocalDateTime.toInstant(ZoneOffset.UTC)`.
3. **Headless.** Not possible: Java 3D classes throw `HeadlessException` when
   loaded with `java.awt.headless=true`, for every renderer. `export.sh` needs
   a display (`xvfb-run` is enough); `HeadlessExport` says so instead of
   printing a stack trace.
4. **SunFlow at night.** SunFlow adds a default light under the ceiling of
   every room whose ceiling is visible. With "Hide ceilings" on, the exported
   floor has none and its night base is black. With it off, that default
   light is in the base images and in the night base alike, so overlays are
   unaffected.
5. **Blender at night.** The lights-off night render stays under 12/255 (a
   faint sky glow) and two renders of the same scene differ by a handful of
   pixels. The default `noiseThreshold` of 6 suits both renderers.
6. **Sample home.** No floor opening is needed: the sample's first floor
   covers half of the ground floor, which shows beside it from above.

## 10. Suggested build order

1. Repo skeleton, `env.sh`, `build.sh`, `test.sh`, empty plugin that shows a
   menu item; `install.sh`, `run.sh`.
2. `plan` package with unit tests.
3. `OverlayDiff` with unit tests.
4. `engine` with fake-backend tests.
5. `Sh3dRenderBackend`, `SampleHomeFactory`, `HeadlessExport`, `export.sh`,
   `export-sample.sh`; resolve the open points in §9.
6. `ExportDialog`, progress dialog, settings persistence, Save / Load
   instructions.
7. README, licence headers, create the public GitHub repo and push.
