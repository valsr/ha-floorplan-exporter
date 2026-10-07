# HA Floorplan Exporter — Design

Date: 2026-10-07
Status: approved design, implementation not started

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
- The open home document is never modified by an export.
- The same export can be run headlessly from a script against a `.sh3d` file.
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
| Renderer | User chooses among `AbstractPhotoRenderer.getAvailableRenderers()` (SunFlow, YafaRay) and quality `LOW` or `HIGH`. |
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
- JDK 26 is installed. `javac --release 8` still works (with an "obsolete"
  warning); compile with `--release 8 -Xlint:-options` so the plugin loads on
  any JVM that Sweet Home 3D 7.5 supports. Therefore: **no Java language
  features newer than Java 8** in `src/main` (no `var`, records, text blocks,
  `List.of`).
- No third-party runtime dependencies. JSON is written by a small hand-rolled
  writer; config files are Java `.properties`.
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
| `ExportConfig` | Immutable value: floors (level id + camera ref), date schedule, time schedule, light ids, width, height, renderer name, quality, hide-ceilings flag, noise threshold, output dir. |
| `DateSchedule` | start date, end date, interval in days → ordered list of `LocalDate`, both ends inclusive. |
| `TimeSchedule` | start time, end time, interval in minutes → ordered list of `LocalTime`, both ends inclusive. |
| `RenderJob` | One render: floor id, kind (`BASE`, `NIGHT_BASE`, `LIGHT`), date + time (for `BASE`), light id (for `LIGHT`), relative output path. |
| `ExportPlanner` | `plan(ExportConfig, HomeSummary) → List<RenderJob>` in execution order. |
| `HomeSummary` | Plain data the planner needs about the home: floors (id, name, elevation index) and lights (id, name, floor id). Built by the engine from a `Home`; built by hand in tests. |
| `Slugs` | Name → filesystem-safe slug, with numeric suffixes for collisions (`lamp`, `lamp-2`). |
| `ConfigProperties` | `ExportConfig` ⇄ `java.util.Properties` (used by the CLI and for saving dialog state). |
| `ManifestWriter` | Writes `manifest.json` from the config, summary and job list. |

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
are skipped and listed in the manifest's `skippedLights`.

### 4.2 `engine`

| Type | Responsibility |
|---|---|
| `HomeInspector` | `Home` → `HomeSummary`. Collects levels and all `HomeLight`s, recursing into `HomeFurnitureGroup`s. Homes without levels are treated as one floor with id `default`. |
| `SceneConfigurer` | Mutates the **cloned** home for a job: visible floor, ceilings, light powers, camera. |
| `RenderBackend` | Interface: `BufferedImage render(Home, Camera, int width, int height)`, plus `stop()`. Lets tests substitute a fake renderer. |
| `Sh3dRenderBackend` | Real implementation using `AbstractPhotoRenderer.createInstance(name, home, null, quality)`, `render(image, camera, null)`, `dispose()`. |
| `OverlayDiff` | Pure image function: `(nightBase, lit, threshold) → ARGB overlay`. |
| `Exporter` | Orchestrates: clone home, iterate jobs, write PNGs, write manifest, report progress, honour cancellation. |
| `ExportListener` | `jobStarted(index, total, job)`, `jobFinished(...)`; `Exporter.cancel()` is thread-safe. |

**Cloning.** `Exporter` calls `home.clone()` once and only ever touches the
clone. Original light powers are read from the clone before they are zeroed.

**Floor visibility.** For a floor F on the clone: `setSelectedLevel(F)` and
`getEnvironment().setAllLevelsVisible(false)`, which shows F and the levels
below it. Implementation must confirm that the photo renderers honour this; if
they do not, fall back to `Level.setViewable(false)` on every level above F
(restoring it between floors).

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

**Renderer lifetime.** A photo renderer snapshots the scene when constructed,
so a new renderer instance is created (and disposed) for every job. Image
type is `BufferedImage.TYPE_INT_ARGB`; base images are written as opaque PNG.

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
- A failure during a render aborts the export; files already written are
  kept; no manifest is written, so a missing manifest marks an incomplete
  export.
- Cancel calls `RenderBackend.stop()`, stops after the current job, same
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
  - Width, height, renderer combo, quality (Low / High), "Hide ceilings"
    (default on).
  - Output folder chooser.
  - Live summary: "N base renders + M light renders".
  - Validation errors shown inline; Export disabled while invalid.
- `ExportProgressDialog`: progress bar, current job label, Cancel. The export
  runs on a background thread (`SwingWorker`); completion, failure and
  cancellation each show a message.
- Dialog state is saved to the real home as one property,
  `haFloorplanExporter.config` (the `ConfigProperties` text), via
  `Home.setProperty`, and restored on next open. This is the only write to the
  open home; it marks the home modified, which is intended so the settings are
  saved with the file.
- User-visible strings live in a `ResourceBundle` (English only for now).

### 4.4 `cli`

`HeadlessExport` main:

```
HeadlessExport <home.sh3d> <config.properties> [--output <dir>]
```

Loads the home with `HomeFileRecorder.readHome`, parses the config, runs
`Exporter`, prints one line per job, exits 0 on success, 1 on export failure,
2 on bad arguments or config. In the config file, floors, cameras and lights
may be referenced by **name** as well as id, since ids are not visible to a
user; an ambiguous name is a config error. The special values `floors=*`
and `lights=*` select all.

Whether the photo renderers work under `-Djava.awt.headless=true` is unknown.
`scripts/export-sample.sh` first tries headless; if Java 3D needs a display,
the script runs with the current `DISPLAY` and the README says so.

## 5. Config file format

```properties
floors=Ground floor,First floor
camera.Ground\ floor=Top ground
camera.First\ floor=Top first
dates.start=2026-01-01
dates.end=2026-12-31
dates.intervalDays=30
times.start=00:00
times.end=23:00
times.intervalMinutes=240
lights=*
width=1920
height=1080
renderer=SunFlow
quality=LOW
hideCeilings=true
noiseThreshold=6
output=/path/to/out
```

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
  "renderer": "SunFlow", "quality": "LOW",
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
scripts/export-sample.sh    end-to-end headless export on a generated sample home
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
- `ConfigProperties`: round trip, `*` wildcards, missing and malformed keys.
- `ManifestWriter`: output parses as JSON and matches the plan (string
  escaping included).
- `OverlayDiff`: identical images → fully transparent; known brightening →
  compositing overlay on base reproduces lit image within ±1; threshold
  behaviour; saturated base channel.
- `Exporter` with a fake `RenderBackend` and a small in-memory `Home`: files
  written to the planned paths, light powers set as specified per job,
  original home unchanged, cancellation leaves no manifest.

**End-to-end** (`scripts/export-sample.sh`): `SampleHomeFactory` (test source)
builds a two-level home with one room per level, walls, a stored top-down
camera per level and two lights taken from the default furniture catalog. The
script runs `HeadlessExport` at 320×240, `LOW` quality, 1 date × 2 times, then
asserts:

- every file listed in `manifest.json` exists and is 320×240;
- each light overlay has both fully transparent pixels and non-transparent
  pixels;
- a noon base image is brighter on average than a midnight one.

**Manual** (`scripts/run.sh`): open a real home, run the dialog, check
All/None, the render count, cancel, and that settings persist after save and
reopen.

## 9. Open points to settle during implementation

1. Whether `setAllLevelsVisible(false)` + selected level is honoured by both
   photo renderers (§4.2, fallback defined).
2. Exact `Camera.setTime` time-zone convention (§4.2, verification defined).
3. Whether rendering works with `java.awt.headless=true` (§4.4, fallback
   defined).
4. Whether a `LOW`-quality SunFlow night render is fully dark with all lights
   off, or has ambient light; the overlay maths does not depend on it, but the
   default `noiseThreshold` may need tuning from sample output.

## 10. Suggested build order

1. Repo skeleton, `env.sh`, `build.sh`, `test.sh`, empty plugin that shows a
   menu item; `install.sh`, `run.sh`.
2. `plan` package with unit tests.
3. `OverlayDiff` with unit tests.
4. `engine` with fake-backend tests.
5. `Sh3dRenderBackend`, `SampleHomeFactory`, `HeadlessExport`,
   `export-sample.sh`; resolve the open points in §9.
6. `ExportDialog`, progress dialog, settings persistence.
7. README, licence headers, create the public GitHub repo and push.
