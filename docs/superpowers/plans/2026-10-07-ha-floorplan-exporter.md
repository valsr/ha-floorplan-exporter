# HA Floorplan Exporter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Sweet Home 3D 7.5 plugin and command-line tool that batch-renders each floor of a home into aligned base images and per-light transparent overlays for ha-floorplan.

**Architecture:** A pure-logic `plan` package (schedules, JSON instructions, job planning, manifest) feeds a UI-free `engine` that runs render jobs against a cloned `Home` through a `RenderBackend` interface. Two front ends drive the engine: a Swing dialog in the plugin and `HeadlessExport` on the command line. Both read and write the same JSON instructions file.

**Tech Stack:** Java (source level 8), Sweet Home 3D 7.5 jars (compile-only), Swing, JUnit 5 console standalone, bash scripts over `javac` / `jar`. Optional at run time: the Blender GPU renderer agent from `/work/sh3d/gpu-renderer`.

**Spec:** `docs/superpowers/specs/2026-10-07-ha-floorplan-exporter-design.md`. Read it first; section numbers below (§) refer to it.

## Global Constraints

- Package root `io.github.valsr.hafloorplan`, sub-packages `plan`, `engine`, `plugin`, `cli`. Dependencies only `plugin → engine → plan` and `cli → engine → plan`. `plan` imports nothing from `com.eteks`.
- `src/main` compiles with `javac --release 8 -Xlint:-options`: no `var`, records, text blocks, `List.of`, `String.isBlank`, `readAllBytes`. Tests may use any Java the installed JDK accepts.
- No third-party runtime dependencies. Sweet Home 3D jars are compile-only and never bundled.
- Sweet Home 3D paths come from `scripts/env.sh`: `SH3D_JARS` (default `/usr/share/java/sweethome3d`), `SH3D_LIB` (default `/usr/lib/sweethome3d`); Java 3D is `$SH3D_LIB/java3d-1.5`.
- Every JVM that touches Sweet Home 3D classes is started with `--add-opens=java.desktop/sun.awt=ALL-UNNAMED -Djava.library.path=$SH3D_LIB/java3d-1.5`.
- The open home is never mutated by an export; the only write to it is the `haFloorplanExporter.instructions` property.
- No reference to `sh3d.gpurenderer` classes anywhere in `src/main`; the GPU renderer is reached only through `AbstractPhotoRenderer`.
- Licence GPL-2.0-or-later; every Java file starts with the standard GPL notice header.
- Version lives only in the `VERSION` file (`0.1.0`).
- Commit after every task with the trailer lines the session's attribution rules require.

## Review Focus

1. **Names that slug badly**: two lights both called "Lamp", a light named "Лампа" or "", a floor named "../x". Each must get a distinct, non-empty, path-safe slug. (Task 2)
2. **Lights the obvious loop misses**: a light inside a furniture group, and a light with no level in a home that has levels. Both must appear in the summary; the level-less one is attached to no floor and ends up in `skippedLights`. (Task 8)
3. **Bad numbers in a hand-edited file**: `width` 0, a negative `height`, `noiseThreshold` 300, `intervalMinutes` 0, `width` 1920.5. Each is reported by key name, not as a stack trace or a silently rounded value. (Task 4)
4. **Output directory state**: a directory that does not exist yet is created; a path that is an existing regular file is reported before any render. (Task 8)
5. **Running from another directory**: `export.sh /elsewhere/job.json` with a relative `home` and `output` inside the file resolves both against `/elsewhere`, not the shell's working directory. (Task 9)

---

## File Structure

```
VERSION  LICENSE  README.md  .gitignore
scripts/{env,build,test,install,run,export,export-sample}.sh
src/main/resources/ApplicationPlugin.properties
src/main/resources/io/github/valsr/hafloorplan/plugin/Messages.properties
src/main/java/io/github/valsr/hafloorplan/
  plan/    DateSchedule TimeSchedule Slugs Json JsonException Quality HomeSummary
           ExportConfig Ref Instructions InstructionsJson InstructionsException
           InstructionsResolver RenderJob ExportPlan ExportPlanner ManifestWriter
  engine/  RenderBackend RenderSession Sh3dRenderBackend HomeInspector SceneConfigurer
           OverlayDiff Exporter ExportListener ExportException
  plugin/  HaFloorplanPlugin ExportDialog ExportProgressDialog
  cli/     HeadlessExport
src/test/java/io/github/valsr/hafloorplan/
  plan/*Test  engine/{OverlayDiffTest,HomeInspectorTest,ExporterTest,FakeRenderBackend,TestHomes}
  cli/HeadlessExportTest  sample/{SampleHomeFactory,SampleAssertions}
```

---

### Task 1: Repository skeleton, build scripts and an empty plugin

**Files:**
- Create: `VERSION`, `LICENSE` (GPL-2.0 text), `.gitignore` (`build/`, `lib/`, `out/`), `scripts/env.sh`, `scripts/build.sh`, `scripts/test.sh`, `scripts/install.sh`, `scripts/run.sh`
- Create: `src/main/resources/ApplicationPlugin.properties`, `src/main/resources/io/github/valsr/hafloorplan/plugin/Messages.properties`
- Create: `src/main/java/io/github/valsr/hafloorplan/plugin/HaFloorplanPlugin.java`
- Test: `src/test/java/io/github/valsr/hafloorplan/plugin/PluginDescriptorTest.java`

**Interfaces:**
- Produces: `scripts/env.sh` exporting `SH3D_JARS`, `SH3D_LIB`, `SH3D_CP` (all jars of `$SH3D_JARS` and `$SH3D_LIB/java3d-1.5`, colon-joined), `SH3D_JAVA_OPTS` (the two flags from Global Constraints), `VERSION`, `PLUGIN_FILE=build/HaFloorplanExporter-$VERSION.sh3p`, `SH3D_GPU_RENDERER_JAR` (§4.4 lookup order; empty when nothing found; a value already set in the environment, including empty, is kept). It exits 1 with `SweetHome3D.jar not found in $SH3D_JARS` when that jar is missing.
- Produces: `scripts/test.sh [junit-console-args…]`. With no arguments it runs every test under `src/test/java`; arguments are passed to the console launcher (for example `--select-class io.github…SlugsTest`). It calls `build.sh` first, compiles tests to `build/test-classes`, and downloads `junit-platform-console-standalone-1.10.2.jar` from Maven Central into `lib/` if absent. Tests run with `SH3D_JAVA_OPTS` and class path `build/test-classes:build/classes:$SH3D_CP`.
- Produces: `build/classes/` and the `.sh3p`; `build.sh` replaces the literal `@VERSION@` in copied resources with the `VERSION` file's content.

- [ ] **Step 1: Write the failing test** `PluginDescriptorTest`:
  - `descriptorNamesThePluginClass`: load `/ApplicationPlugin.properties` from the class path; assert `class` = `io.github.valsr.hafloorplan.plugin.HaFloorplanPlugin`, `license` = `GPL-2.0-or-later`, `applicationMinimumVersion` = `7.0`, `javaMinimumVersion` = `1.8`, and `version` equals the content of the `VERSION` file (trimmed).
  - `pluginExposesOneToolsAction`: `new HaFloorplanPlugin().getActions()` has length 1, and its `PluginAction.Property.MENU` value is the Tools menu name and `NAME` is `Export for HA Floorplan…`.
- [ ] **Step 2: Write the five scripts** as described in Interfaces and §7. `install.sh` removes `~/.eteks/sweethome3d/plugins/HaFloorplanExporter-*.sh3p` before copying. `run.sh [file]` = build, install, `exec sweethome3d "$@"`.
- [ ] **Step 3: Run** `scripts/test.sh`. Expected: compilation fails, `HaFloorplanPlugin` not found.
- [ ] **Step 4: Implement** `HaFloorplanPlugin extends com.eteks.sweethome3d.plugin.Plugin` with one `PluginAction` (enabled, Tools menu, name from `Messages.properties` key `action.name`). `execute()` shows a `JOptionPane` saying the exporter is not implemented yet. Write `ApplicationPlugin.properties` with the keys of §4.3 and `version=@VERSION@`.
- [ ] **Step 5: Run** `scripts/test.sh`. Expected: 2 tests successful. Then `unzip -l build/HaFloorplanExporter-0.1.0.sh3p` lists `ApplicationPlugin.properties` at the root and no `com/eteks` entries.
- [ ] **Step 6: Commit** `Add build scripts and empty plugin`.

---

### Task 2: Schedules and slugs

**Files:**
- Create: `plan/DateSchedule.java`, `plan/TimeSchedule.java`, `plan/Slugs.java`
- Test: `plan/DateScheduleTest.java`, `plan/TimeScheduleTest.java`, `plan/SlugsTest.java`

**Interfaces:**
- Produces: `DateSchedule(LocalDate start, LocalDate end, int intervalDays)`, `List<LocalDate> dates()`, getters `getStart()`, `getEnd()`, `getIntervalDays()`, value `equals`/`hashCode`.
- Produces: `TimeSchedule(LocalTime start, LocalTime end, int intervalMinutes)`, `List<LocalTime> times()`, the matching getters and equality.
- Produces: `Slugs` instance, one per namespace: `String unique(String name)`. Static `String slug(String name)`.

- [ ] **Step 1: Write the failing tests.**
  - `DateScheduleTest`: `2026-01-01..2026-01-03` every 1 → three dates in order; `2026-01-01..2026-01-10` every 4 → `01, 05, 09`; start = end → one date; interval 0 → `IllegalArgumentException` whose message contains `interval`; end before start → `IllegalArgumentException` whose message contains `before`.
  - `TimeScheduleTest`: `00:00..23:00` every 240 → `00:00, 04:00, 08:00, 12:00, 16:00, 20:00`; `08:00..08:00` → one; `22:00..02:00` → `IllegalArgumentException` containing `before`; interval 0 → exception; `00:00..23:59` every 1 → 1440 values, last `23:59` (no wrap past midnight).
  - `SlugsTest`: `slug("Kitchen Lamp")` = `kitchen-lamp`; `slug("  Été / 2 ")` = `ete-2`; `slug("../x")` = `x`; `slug("")`, `slug(null)` and `slug("Лампа")` = `item`; on one `Slugs` instance `unique("Lamp")`, `unique("Lamp")`, `unique("lamp")` = `lamp`, `lamp-2`, `lamp-3`; `unique("lamp-2")` after those = `lamp-2-2`.
- [ ] **Step 2: Run** `scripts/test.sh`. Expected: compilation fails.
- [ ] **Step 3: Implement.** `slug`: NFD-normalise, drop combining marks, lower-case with `Locale.ROOT`, replace every run of characters outside `[a-z0-9]` with `-`, trim `-`, and return `item` if empty.
- [ ] **Step 4: Run** `scripts/test.sh`. Expected: all pass.
- [ ] **Step 5: Commit** `Add date and time schedules and slugs`.

---

### Task 3: JSON reader and writer

**Files:**
- Create: `plan/Json.java`, `plan/JsonException.java`
- Test: `plan/JsonTest.java`

**Interfaces:**
- Produces: `static Object Json.parse(String text)` returning `LinkedHashMap<String,Object>`, `ArrayList<Object>`, `String`, `Double`, `Boolean` or `null`; throws `JsonException`.
- Produces: `static String Json.write(Object value)`: accepts `Map`, `List`, `String`, any `Number`, `Boolean`, `null`; 2-space indented, keys in map iteration order, trailing newline. A `Number` with an integral value is written without a fraction (`6`, not `6.0`).
- Produces: `JsonException extends RuntimeException` with `getLine()`, `getColumn()` (1-based); message ends with ` at line L, column C`.

- [ ] **Step 1: Write the failing tests** in `JsonTest`:
  - `roundTrip`: a map holding a nested map, a list, `"a\"b\\c\né\u0001"`, `1`, `1.5`, `-2e3`, `true`, `false`, `null` survives `parse(write(x))` (numbers compared as doubles).
  - `integralNumbersHaveNoFraction`: `write(6.0)` and `write(6)` are both `6\n`.
  - `parsesEscapes`: `"é\t\/"` parses to `é`, tab, `/`.
  - `toleratesBomAndWhitespace`: a leading U+FEFF and surrounding blank lines parse.
  - `reportsPosition`: `{\n  "a": 1,\n}` throws with line 3, column 1; `{"a" 1}` throws with line 1, column 6.
  - `rejects`: trailing characters after the value, a duplicate key in one object, an unterminated string, `NaN`, a `//` comment. Each throws `JsonException`.
  - `writeRejectsUnknownTypes`: `write(new Object())` throws `IllegalArgumentException`.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.plan.JsonTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** a recursive-descent parser over a `char` index that tracks line and column.
- [ ] **Step 4: Run** the same command. Expected: all pass.
- [ ] **Step 5: Commit** `Add minimal JSON reader and writer`.

---

### Task 4: Config values and the instructions file

**Files:**
- Create: `plan/Quality.java`, `plan/HomeSummary.java`, `plan/ExportConfig.java`, `plan/Ref.java`, `plan/Instructions.java`, `plan/InstructionsJson.java`, `plan/InstructionsException.java`
- Test: `plan/InstructionsJsonTest.java`

**Interfaces:**
- Consumes: `Json`, `DateSchedule`, `TimeSchedule`.
- Produces: `enum Quality { LOW, HIGH }`.
- Produces: `HomeSummary(List<Floor> floors, List<Light> lights, List<Camera> cameras, List<Renderer> renderers)`, all lists unmodifiable, floors in elevation order. Nested immutable classes with public final fields: `Floor(String id, String name)`, `Light(String id, String name, String floorId)` (`floorId` null = on no floor), `Camera(String id, String name)`, `Renderer(String className, String displayName)`. Constant `HomeSummary.DEFAULT_FLOOR_ID = "default"`. Lookups `floor(String id)`, `light(String id)`, `camera(String id)`, `renderer(String className)` returning null when absent.
- Produces: `ExportConfig`, immutable, built with `ExportConfig.builder()` → `floors(List<ExportConfig.Floor>)`, `dates(DateSchedule)`, `times(TimeSchedule)`, `lightIds(List<String>)`, `width(int)`, `height(int)`, `rendererClassName(String)`, `quality(Quality)`, `hideCeilings(boolean)`, `isolateLevel(boolean)`, `noiseThreshold(int)`, `outputDir(File)` → `build()`; a getter per field. `ExportConfig.Floor(String levelId, String cameraId)`, `cameraId` null = current view.
- Produces: `Ref(String id, String name)` with public final fields, either may be null but not both; `Ref.of(String text)` = `new Ref(text, text)`.
- Produces: `Instructions`, immutable, built with `Instructions.builder()`; fields and getters: `String home` (nullable), `String output` (nullable), `boolean allFloors`, `List<Instructions.Floor> floors` (`Floor(Ref level, Ref camera)`, camera nullable), `DateSchedule dates`, `TimeSchedule times`, `boolean allLights`, `List<Ref> lights`, `int width`, `int height`, `String renderer` (nullable = first available), `Quality quality`, `boolean hideCeilings`, `boolean isolateLevel`, `int noiseThreshold`. Copy methods `withHome(String)`, `withOutput(String)`. Value equality.
- Produces: `static Instructions InstructionsJson.parse(String json)` throwing `InstructionsException`; `static String InstructionsJson.write(Instructions)`.
- Produces: `InstructionsException extends RuntimeException` with `List<String> getProblems()`; `getMessage()` is the problems joined by newlines.

Decisions the tests pin: defaults are those of the §5 table (`lights` `"*"`, 1920×1080, `renderer` absent, `LOW`, `hideCeilings` true, `isolateLevel` false, `noiseThreshold` 6). The writer emits every key in the order of the §5 example, omits `home`, `output`, `renderer` and a floor's `camera` when null, writes `"*"` for all floors or all lights, and writes a `Ref` as a plain string when `id` equals `name` or `id` is null, otherwise as `{"id", "name"}` (omitting a null `name`). The parser collects every problem before throwing; a JSON syntax error is a single problem carrying the `JsonException` message.

- [ ] **Step 1: Write the failing tests** in `InstructionsJsonTest`:
  - `parsesSpecExample`: the §5 example text parses; assert `home` `house.sh3d`, floor 0 level `Ref("level-…", "Ground floor")`, floor 1 level `Ref.of("First floor")` and camera `Ref.of("Top first")`, `allLights` true, dates `2026-01-01..2026-12-31/30`, times `00:00..23:00/240`, renderer `sh3d.gpurenderer.BlenderRenderer`.
  - `appliesDefaults`: `{"version":1,"floors":"*","dates":{…},"times":{…}}` gives the defaults above, `allFloors` true, `home` and `output` null.
  - `roundTrips`: for the parsed §5 example and for a builder-made value with `lights` `[]` and one camera-less floor, `parse(write(x)).equals(x)`.
  - `reportsAllProblemsTogether`: input with `version` 2, a misspelt key `isolateLevels`, `width` `"wide"` and missing `dates` throws one `InstructionsException` with 4 problems, each naming its key.
  - `rejectsBadNumbers` (Review Focus 3): `width` 0, `height` -1, `noiseThreshold` 300, `width` 1920.5, `times.intervalMinutes` 0, `dates.end` before `dates.start`: each alone gives exactly one problem that contains the key name.
  - `rejectsBadShapes`: `floors` `[]`; a floor without `level`; a reference `{}`; a reference `{"id":1}`; `quality` `"MEDIUM"`; `dates.start` `"01/02/2026"`; a top-level array.
  - `wrapsSyntaxErrors`: `{"version":1,` gives one problem containing `line 1`.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.plan.InstructionsJsonTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** the value classes, then `InstructionsJson`.
- [ ] **Step 4: Run** the same command. Expected: all pass.
- [ ] **Step 5: Commit** `Add export config and JSON instructions format`.

---

### Task 5: Resolving instructions against a home

**Files:**
- Create: `plan/InstructionsResolver.java`
- Test: `plan/InstructionsResolverTest.java`

**Interfaces:**
- Consumes: Task 4 types.
- Produces: `static Resolution InstructionsResolver.resolve(Instructions instructions, HomeSummary summary, File baseDir)`. `Resolution` has `ExportConfig getConfig()` (never null) and `List<String> getProblems()`. It never throws for unresolvable references: each one is dropped from the config and added to `problems`. Callers decide: the CLI fails on any problem, the dialog shows them as a warning.
- Produces: `static Instructions InstructionsResolver.toInstructions(ExportConfig config, HomeSummary summary, String homePath)`.

Rules: a `Ref` matches the object whose id equals `ref.id`; failing that, the single object whose name equals `ref.name`; two or more name matches is an "ambiguous" problem, none an "unknown" problem. Problem texts name the kind and the reference, for example `Unknown light "Desk lamp"`, `Ambiguous floor name "Hall" (2 matches)`. `allFloors` → every floor with the current view. A floor whose level resolves but whose camera does not keeps the floor with the current view and reports the camera. A floor listed twice is a problem and kept once. `allLights` → every light id. `renderer`: null → first of `summary.renderers` (problem `No renderer available` if empty); otherwise matched ignoring case against class name, simple class name (text after the last `.`) and display name; no match → problem, falls back to the first. `output`: null stays null; relative paths resolve against `baseDir`. `toInstructions` writes `Ref(id, name)` for every floor, camera and light, `allFloors` and `allLights` false, the renderer's class name, and an absolute `output`.

- [ ] **Step 1: Write the failing tests** in `InstructionsResolverTest`, against a hand-built summary with floors `Ground` and `First`, lights `Lamp` (twice, ids `l1`, `l2`, on `Ground`) and `Desk` (`l3`, on `First`), cameras `Top ground`, `Top first`, renderers `com.eteks.sweethome3d.j3d.PhotoRenderer` / `SunFlow` and `sh3d.gpurenderer.BlenderRenderer` / `Blender Cycles (GPU)`:
  - `resolvesById`, `resolvesByName`, `fallsBackToNameWhenIdUnknown` (`Ref("gone", "Desk")` → `l3`).
  - `idWinsOverName`: `Ref("l1", "Desk")` → `l1`.
  - `ambiguousNameIsAProblem`: `Ref.of("Lamp")` → dropped, one problem containing `Ambiguous`.
  - `unknownReferencesAreDroppedAndListed`: unknown floor, unknown light and unknown camera in one file → 3 problems; the floor with the unknown camera is kept with `cameraId` null.
  - `wildcards`: `allFloors` and `allLights` give 2 floors with null cameras and 3 light ids.
  - `rendererMatching`: `"BlenderRenderer"`, `"blender cycles (gpu)"` and the full class name all resolve to `sh3d.gpurenderer.BlenderRenderer`; null resolves to the SunFlow class; `"Nope"` gives a problem.
  - `relativeOutputUsesBaseDir`: `output` `out` with base `/data/job` → `/data/job/out`; an absolute `output` is unchanged.
  - `toInstructionsRoundTrips`: `resolve(toInstructions(config, summary, "/h.sh3d"), summary, anyDir)` has no problems and a config equal to `config` field by field; the instructions' `home` is `/h.sh3d`.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.plan.InstructionsResolverTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** `InstructionsResolver`.
- [ ] **Step 4: Run** the same command. Expected: all pass.
- [ ] **Step 5: Commit** `Resolve instructions against a home summary`.

---

### Task 6: Job planning and the manifest

**Files:**
- Create: `plan/RenderJob.java`, `plan/ExportPlan.java`, `plan/ExportPlanner.java`, `plan/ManifestWriter.java`
- Test: `plan/ExportPlannerTest.java`, `plan/ManifestWriterTest.java`

**Interfaces:**
- Consumes: `ExportConfig`, `HomeSummary`, `Slugs`, `Json`.
- Produces: `RenderJob` with public final fields `String floorId`, `Kind kind` (`enum Kind { BASE, NIGHT_BASE, LIGHT }`), `LocalDate date` and `LocalTime time` (set only for `BASE`), `String lightId` (set only for `LIGHT`), `String path` (relative, `/`-separated).
- Produces: `static ExportPlan ExportPlanner.plan(ExportConfig, HomeSummary)`. `ExportPlan` getters: `List<RenderJob> getJobs()`, `String floorSlug(String floorId)`, `String lightSlug(String lightId)`, `List<String> getSkippedLightIds()`.
- Produces: `static String ManifestWriter.write(ExportConfig config, HomeSummary summary, ExportPlan plan, String generator, LocalDateTime nightTime, Double nightSunElevation)`. The last two are null when the plan has no `LIGHT` job; the keys `nightTime` and `nightSunElevation` are then omitted.

Rules: job order and paths per §4.1 and §6. Floor slugs come from one `Slugs` instance over floor names in config order; light slugs from one `Slugs` instance per floor. A selected light is skipped when its `floorId` is null or is not a selected floor. Manifest shape per §6, plus `rendererName` (display name from the summary); a floor's `camera` is the stored camera's name or JSON `null` for the current view; a floor without lights has no `night` key and `"lights": []`.

- [ ] **Step 1: Write the failing tests.**
  - `ExportPlannerTest`, summary of 2 floors (`Ground floor`, `First floor`) and lights `Kitchen lamp` + `Kitchen lamp` on ground, `Desk` on first, `Stray` with null floor:
    - `ordersJobs`: both floors, 2 dates × 3 times, lights = the two kitchen lamps → jobs are ground `BASE` ×6 (date major, time minor), ground `NIGHT_BASE`, ground `LIGHT` ×2, then first `BASE` ×6 and nothing more (15 jobs).
    - `paths`: first job path `ground-floor/base/2026-01-01_0000.png`; night `ground-floor/night.png`; lights `ground-floor/lights/kitchen-lamp.png` and `ground-floor/lights/kitchen-lamp-2.png`.
    - `skipsLightsOffSelectedFloors`: only the ground floor selected with lights `Desk` and `Stray` → no `NIGHT_BASE`, no `LIGHT`, `getSkippedLightIds()` holds both ids.
    - `noLightsNoNightBase`: empty light list → only `BASE` jobs.
  - `ManifestWriterTest`:
    - `matchesPlan`: `Json.parse` of the output succeeds; `floors[0].base` has one entry per `BASE` job with equal `file`; `floors[0].lights[1].slug` is `kitchen-lamp-2`; `isolateLevel`, `hideCeilings`, `renderer`, `rendererName`, `dates`, `times` (as `HH:mm`) match the config; `nightTime` is `2026-01-01T00:00`.
    - `escapesNames`: a light named `6" \ spot` round-trips through `Json.parse`.
    - `omitsNightWithoutLights`: no `nightTime` key at the top, no `night` key in the floor.
- [ ] **Step 2: Run** `scripts/test.sh`. Expected: compilation fails.
- [ ] **Step 3: Implement** the four classes.
- [ ] **Step 4: Run** `scripts/test.sh`. Expected: all pass.
- [ ] **Step 5: Commit** `Plan render jobs and write the manifest`.

---

### Task 7: Overlay difference

**Files:**
- Create: `engine/OverlayDiff.java`
- Test: `engine/OverlayDiffTest.java`

**Interfaces:**
- Produces: `static BufferedImage OverlayDiff.diff(BufferedImage nightBase, BufferedImage lit, int threshold)` returning `TYPE_INT_ARGB` of the same size. Different sizes → `IllegalArgumentException`. Input alpha is ignored.

- [ ] **Step 1: Write the failing tests** in `OverlayDiffTest` (helper `composite(base, overlay)` doing straight source-over per channel in floating point, rounded):
  - `identicalImagesAreTransparent`: every output pixel has alpha 0.
  - `belowThresholdIsTransparent`: base grey 100, lit grey 106, threshold 6 → alpha 0; lit grey 107 → alpha > 0.
  - `compositingReproducesLitImage`: 64 pseudo-random pixel pairs (fixed seed) with `lit ≥ base` per channel and a difference above the threshold → `composite(base, diff)` is within ±1 of `lit` on every channel.
  - `usesMinimalAlpha`: base `(0,0,0)`, lit `(128,64,0)` → alpha 128, colour `(255,128,0)` within ±1.
  - `saturatedBaseChannel`: base `(255,10,10)`, lit `(255,60,10)` → no exception, alpha 52 ±1, green 255.
  - `darkerLitPixelIsTransparent`: lit darker than base on all channels → alpha 0.
  - `sizeMismatchThrows`.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.engine.OverlayDiffTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** the four-step formula of §4.2 "Overlay maths" exactly as written there.
- [ ] **Step 4: Run** the same command. Expected: all pass.
- [ ] **Step 5: Commit** `Add light overlay difference`.

---

### Task 8: Engine with a fake renderer

**Files:**
- Create: `engine/RenderBackend.java`, `engine/RenderSession.java`, `engine/HomeInspector.java`, `engine/SceneConfigurer.java`, `engine/Exporter.java`, `engine/ExportListener.java`, `engine/ExportException.java`
- Test: `engine/TestHomes.java`, `engine/FakeRenderBackend.java`, `engine/HomeInspectorTest.java`, `engine/ExporterTest.java`

**Interfaces:**
- Consumes: all of `plan`, `OverlayDiff`.
- Produces:
  ```java
  public interface RenderBackend {
    List<HomeSummary.Renderer> availableRenderers();
    RenderSession open(Home home, String rendererClassName, Quality quality) throws IOException;
  }
  public interface RenderSession extends Closeable {
    BufferedImage render(Camera camera, int width, int height) throws IOException;  // TYPE_INT_ARGB
    void stop();          // callable from another thread
    @Override void close();
  }
  ```
- Produces: `static HomeSummary HomeInspector.summarize(Home home, RenderBackend backend)`.
- Produces: `Exporter(Home home, ExportConfig config, RenderBackend backend, String generator)`; `boolean run(ExportListener listener) throws ExportException` (true = completed, false = cancelled; `listener` may be null); `void cancel()` (thread-safe).
- Produces: `ExportListener { void jobStarted(int index, int total, RenderJob job); void jobFinished(int index, int total, RenderJob job); }` with 0-based `index`.
- Produces: `ExportException extends Exception` with `List<String> getProblems()` and an optional cause.
- Produces (package-private, used only by `Exporter`): `SceneConfigurer(Home clone)` with `void showFloor(String floorId, boolean isolate, boolean hideCeilings)`, `void setLights(String litLightId)` (null = all off), `Camera camera(String cameraId)` (a clone; null = `home.getCamera()`), `static long cameraTime(LocalDate, LocalTime)`, `LocalDateTime nightTime(LocalDate firstDate)`, `float sunElevation(LocalDateTime)`.

Decisions:
- Ids are `HomeObject.getId()` for levels, lights and stored cameras. A home with no levels has one floor, id `HomeSummary.DEFAULT_FLOOR_ID`, named after the home's file name without extension, or `Home` when unsaved; `showFloor` and `isolate` are then no-ops apart from ceilings.
- Stored cameras without a name are left out of the summary.
- `cameraTime` is the wall-clock date and time taken as UTC: `LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()`. This is Sweet Home 3D's convention; renderers pass it through `Camera.convertTimeToTimeZone(time, compass.getTimeZone())`, and `sunElevation` does the same before `Compass.getSunElevation`.
- Level visibility, isolation, ceilings, light powers, night time, session lifetime, errors and cancellation exactly as §4.2. Ceiling visibility is restored to the clone's original values before each floor.
- Up-front validation collects into one `ExportException`: unknown floor, camera or light id; renderer class not in `availableRenderers()`; null output directory; output path that exists and is not a directory, or cannot be created or written. The directory is created if missing.
- Base images and `night.png` are written as opaque RGB PNG, overlays as ARGB PNG. Parent directories are created per file.
- `Exporter` builds its own `HomeSummary` and `ExportPlan` from the clone.

`FakeRenderBackend` (test): offers one renderer `fake.Renderer` / `Fake`; records every `open` (snapshot of each light's power and each level's `isVisible()` at open time, plus rendered camera times) and every `close`; `render` returns a solid image whose grey level is `40 + 60 × (number of lights with power > 0)`; a settable `failOnRender` index and `onRender` hook.

`TestHomes.twoLevels()` (test): levels `Ground floor` (elevation 0) and `First floor` (elevation 250), one room per level, lights `Kitchen lamp` (power 0.5) on ground inside a `HomeFurnitureGroup`, `Desk` (power 0.8) on first, one stored camera per level named `Top ground` / `Top first`. Lights come from `DefaultFurnitureCatalog`, as in `/work/sh3d/gpu-renderer/src/test/java/sh3d/gpurenderer/TestHomes.java`.

- [ ] **Step 1: Write the failing tests.**
  - `HomeInspectorTest`: `summarizesLevelsLightsAndCameras` (2 floors in order, 2 lights with correct floor ids, 2 cameras, 1 renderer); `findsLightsInsideGroups` (Review Focus 2: `Kitchen lamp` is present); `levelLessLightHasNoFloor` (Review Focus 2: a light added with no level in the levelled home has `floorId` null); `homeWithoutLevelsHasDefaultFloor` (one floor with id `default`, its lights carry `floorId` `default`).
  - `ExporterTest` (output in a JUnit `@TempDir`, config: both floors with their cameras, 1 date × 2 times, both lights):
    - `writesPlannedFiles`: `run` returns true; every `path` of the plan exists as a PNG of the configured size; `manifest.json` exists and parses.
    - `overlayIsDifferenceAgainstNightBase`: `ground-floor/lights/kitchen-lamp.png` has alpha > 0 everywhere (the fake renders lit frames 60 brighter); `night.png` is opaque.
    - `sessionsFollowSceneState`: per floor exactly 2 sessions, the first with all powers 0 and 3 renders (2 base + night), the second with only that floor's light at its original power (0.5 / 0.8) and 1 render; all sessions closed.
    - `baseRendersUseScheduledTimes`: the first session's camera times are `cameraTime(date, t)` for each scheduled time, then the night time.
    - `levelVisibility`: with `isolateLevel` false the first-floor sessions see both levels visible and the ground-floor sessions only the ground level; with it true every session sees exactly its own level.
    - `originalHomeUntouched`: after `run`, the original home's light powers, level `isVisible()` values, room ceiling flags, selected level and `isModified()` are unchanged.
    - `validationReportsEverythingBeforeRendering`: unknown light id + unknown renderer + output path that is a regular file → `ExportException` with 3 problems and no session opened (Review Focus 4).
    - `createsMissingOutputDir` (Review Focus 4).
    - `failureLeavesNoManifestAndClosesSession`: `failOnRender` 1 → `ExportException` with a cause, the first image exists, no `manifest.json`, every opened session closed.
    - `cancelStopsAfterCurrentJob`: `onRender` calls `exporter.cancel()` during the first render → `run` returns false, `stop()` was called on the open session, no `manifest.json`, session closed.
    - `listenerSeesEveryJob`: `jobStarted` and `jobFinished` are each called once per job with increasing indices and the right total.
- [ ] **Step 2: Run** `scripts/test.sh`. Expected: compilation fails.
- [ ] **Step 3: Implement** `HomeInspector`, `SceneConfigurer`, `Exporter` and the small types.
- [ ] **Step 4: Run** `scripts/test.sh`. Expected: all pass.
- [ ] **Step 5: Commit** `Add export engine with pluggable render backend`.

---

### Task 9: Real renderers, command line and the end-to-end sample

**Files:**
- Create: `engine/Sh3dRenderBackend.java`, `cli/HeadlessExport.java`, `scripts/export.sh`, `scripts/export-sample.sh`
- Test: `cli/HeadlessExportTest.java`, `sample/SampleHomeFactory.java`, `sample/SampleAssertions.java`
- Modify: the spec's §9, recording how each open point was settled

**Interfaces:**
- Consumes: `Exporter`, `HomeInspector`, `InstructionsJson`, `InstructionsResolver`, `RenderBackend`.
- Produces: `Sh3dRenderBackend implements RenderBackend` with a public no-argument constructor. `availableRenderers()` lists each class name of `AbstractPhotoRenderer.getAvailableRenderers()` for which `createInstance(name, new Home(), null, Quality.LOW)` returns an instance of exactly that class with `isAvailable()` true, paired with its `getName()` (the probe instance is disposed). `open` creates the instance with a null `Object3DFactory`; if the instance's class is not the requested one it throws `IOException("Renderer <name> is not available")`. `render` allocates the image, calls `render(image, camera, null)` and returns it; `stop()` → `renderer.stop()`; `close()` → `renderer.dispose()`.
- Produces: `HeadlessExport.main(String[])` and, for tests, `static int HeadlessExport.run(String[] args, InputStream in, PrintStream out, PrintStream err, RenderBackend backend)` returning the exit code, plus `static Instructions HeadlessExport.load(String[] args, InputStream in, File workingDir)` which parses arguments, reads the instructions and returns them with `home` and `output` made absolute. `main` calls `System.exit(run(…, new Sh3dRenderBackend()))`.
- Produces: `scripts/export.sh <instructions.json | -> [--home f] [--output d]` and `scripts/export-sample.sh` (environment: `RENDERER`, default `SunFlow`; `KEEP=1` keeps `build/sample/`).

Decisions:
- Argument and path rules, strictness and exit codes exactly as §4.4. Path resolution happens in `load`, before `InstructionsResolver` (which then receives absolute paths). Output lines: one `[i/total] <path>` per finished job on `out`; problems one per line on `err`.
- The generator string is `ha-floorplan-exporter <version>`, the version read from `/ApplicationPlugin.properties`.
- `export.sh` sources `env.sh`, builds if the `.sh3p` is missing, and runs `java $SH3D_JAVA_OPTS [-javaagent:$SH3D_GPU_RENDERER_JAR] -cp "$PLUGIN_FILE:$SH3D_CP" io.github.valsr.hafloorplan.cli.HeadlessExport "$@"`. The agent flag is added only when the variable is non-empty and `JAVA_TOOL_OPTIONS` does not already contain `gpu-renderer.jar`. Try `-Djava.awt.headless=true` first while developing; if a render fails for lack of a display, drop the flag from the script for good and state the display requirement in a comment (open point 3).
- `SampleHomeFactory.main(String[] {out.sh3d})` writes, with `HomeFileRecorder`, a home like `TestHomes.twoLevels()` but with walls around each 500×400 room, both lights not in a group, cameras looking straight down from above each level, and the upper room covering only half of the lower one so the lower level shows beside it (settles open point 6 without needing a floor opening).
- `export-sample.sh` generates the sample home in `build/sample/`, writes `build/sample/job.json` there with a **relative** `home` and `output` (320×240, `LOW`, date `2026-06-21`, times `00:00` and `12:00`, all floors by name with their cameras, `lights` `"*"`, `renderer` from `$RENDERER`), runs `scripts/export.sh` on it from the repository root, then a second time with `isolateLevel` true into `out-isolated`, and finally runs `SampleAssertions.main` on both output folders. `SampleAssertions` exits non-zero with a message per failed assertion of §8 "End-to-end".

- [ ] **Step 1: Write the failing tests** in `HeadlessExportTest` (using `FakeRenderBackend` and a home written to a `@TempDir` by `SampleHomeFactory`):
  - `relativePathsResolveAgainstInstructionsFile` (Review Focus 5): file `<tmp>/a/job.json` with `home` `../h.sh3d` and `output` `out`, `workingDir` `<tmp>/elsewhere` → `load` gives `<tmp>/h.sh3d` and `<tmp>/a/out` (normalised).
  - `commandLineOverridesAndUsesWorkingDir`: `--home rel.sh3d --output o` with `workingDir` W → `W/rel.sh3d`, `W/o`.
  - `readsStandardInput`: argument `-` reads the stream; relative paths in it resolve against `workingDir`.
  - `exitCodes`: no arguments → 2; missing file → 2; invalid JSON → 2; no home anywhere → 2; unknown light name → 2 and `err` contains `Unknown light`; fake backend failing on render → 1; valid run → 0 with one `out` line per job and a `manifest.json`.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.cli.HeadlessExportTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** `Sh3dRenderBackend`, `HeadlessExport`, `SampleHomeFactory`.
- [ ] **Step 4: Run** `scripts/test.sh`. Expected: all pass.
- [ ] **Step 5: Write** `scripts/export.sh`, `scripts/export-sample.sh`, `SampleAssertions`.
- [ ] **Step 6: Run** `scripts/export-sample.sh`. Expected: last line `sample export OK`, exit 0. Look at the images in `build/sample/out` with `KEEP=1`: the floors must be framed, the noon image lit by the sun, each overlay showing a pool of light.
- [ ] **Step 7: Run** `RENDERER=BlenderRenderer scripts/export-sample.sh` (build the agent first with `make -C /work/sh3d/gpu-renderer` if `build/gpu-renderer.jar` is missing). Expected: same OK line; Blender starts 4 times per export (per floor: one lights-off session and one light session), not once per image.
- [ ] **Step 8: Settle the open points of §9** from these runs and edit the spec: level hiding confirmed per renderer (or the fallback implemented and tested), noon sun matches the photo dialog for the same camera and time, headless or not, and the `noiseThreshold` default. If overlays show speckle outside the light pool with either renderer, raise the default in `InstructionsJson`, its test and the §5 table together.
- [ ] **Step 9: Commit** `Add real render backend, command-line export and sample run`.

---

### Task 10: Export dialog

**Files:**
- Create: `plugin/ExportDialog.java`, `plugin/ExportProgressDialog.java`
- Modify: `plugin/HaFloorplanPlugin.java`, `plugin/Messages.properties`
- Test: `plugin/ExportDialogTest.java`

**Interfaces:**
- Consumes: `HomeInspector`, `Sh3dRenderBackend`, `InstructionsJson`, `InstructionsResolver`, `ExportPlanner`, `Exporter`.
- Produces: `ExportDialog(Frame owner, HomeSummary summary, File homeFile, boolean homeModified)`; `void setInstructions(Instructions)` returning nothing and never throwing for unresolved references; `List<String> getLoadWarnings()` (problems from the last `setInstructions`); `Instructions getInstructions()` (current widget state, `home` = `homeFile`'s absolute path or null); `List<String> getValidationErrors()`; `String getSummaryText()`; `boolean showDialog()` (true = Export pressed).
- Produces: `ExportProgressDialog(Frame owner, Exporter exporter)`; `void run()` blocks on a modal dialog while a `SwingWorker` runs the export, then shows the completion, failure (problems listed) or cancelled message.

Decisions:
- Widgets, defaults and behaviour exactly as §4.3. Dates and times are text fields in `YYYY-MM-DD` and `HH:mm`; intervals, width and height are spinners (minimum 1).
- `getValidationErrors()` is: field parse errors, schedule errors (the `IllegalArgumentException` messages), no floor ticked, no output folder. Export and Save are disabled while it is non-empty and the first error is shown in a label above the buttons.
- `getSummaryText()` is `N base renders + M light renders` from `ExportPlanner` on the resolved state (the night bases are not counted).
- First open with no stored property: every floor ticked with "Current 3D view", today's date as start and end with interval 1, times `00:00`–`23:00` every 240, all lights ticked, 1920×1080, first renderer, Low.
- `HaFloorplanPlugin.execute()`: summarize the home, build the dialog, apply the home property `haFloorplanExporter.instructions` if present, show it; on Export store `InstructionsJson.write(getInstructions().withHome(null))` in that property, warn and ask to continue if the output folder exists and is not empty, then run `Exporter` through `ExportProgressDialog` with the config from `InstructionsResolver.resolve`. A home with no renderer available shows a message and no dialog.
- All user-visible strings come from `Messages.properties`.

- [ ] **Step 1: Write the failing tests** in `ExportDialogTest` (dialog constructed but never shown; Swing components need a display; skip with `Assumptions.assumeFalse(GraphicsEnvironment.isHeadless())`), using the hand-built summary of Task 5:
  - `stateRoundTrips`: `setInstructions(x); getInstructions()` equals `x` after both are passed through `InstructionsResolver.toInstructions(resolve(…))`, for an `x` with one floor, one light, `isolateLevel` true and the Blender renderer.
  - `loadDropsUnknownReferences`: instructions naming an unknown light and floor → `getLoadWarnings()` has 2 entries, the known ones are ticked.
  - `validation`: clearing the output folder gives an error and a disabled Export button; an end date before the start date gives an error containing `before`.
  - `summaryCountsRenders`: 2 floors, 2 dates × 3 times, 3 lights → `12 base renders + 3 light renders`.
  - `hideOtherLevelsDisabledWithoutLevels`: a summary whose only floor is `default` → the checkbox is disabled and `getInstructions().isIsolateLevel()` is false.
- [ ] **Step 2: Run** `scripts/test.sh --select-class io.github.valsr.hafloorplan.plugin.ExportDialogTest`. Expected: compilation fails.
- [ ] **Step 3: Implement** the two dialogs and wire `HaFloorplanPlugin`; update `PluginDescriptorTest` if the action name key moved.
- [ ] **Step 4: Run** `scripts/test.sh`. Expected: all pass.
- [ ] **Step 5: Manual check** with `scripts/run.sh build/sample/sample.sh3d` (after `KEEP=1 scripts/export-sample.sh`), working through §8 "Manual": All / None, the render count, an export with the Blender renderer, Cancel mid-export (no `manifest.json`, no Blender process left: `pgrep blender` is empty), settings restored after save and reopen, Save instructions then `scripts/export.sh <saved file>` producing the same file list, Load of a file saved from another home showing the warning. Record the result of each in the commit message body.
- [ ] **Step 6: Commit** `Add export dialog with saved and loaded instructions`.

---

### Task 11: README and publication

**Files:**
- Create: `README.md`
- Modify: any Java file lacking the GPL header

- [ ] **Step 1: Write** `README.md`: what it produces (with the §6 layout), requirements (Sweet Home 3D 7.5, a JDK, optional Blender GPU renderer and how `SH3D_GPU_RENDERER_JAR` is found), install (`scripts/build.sh && scripts/install.sh`), dialog usage, the instructions file (§5 table and example), command-line usage with both `export.sh` examples of §4.4 and the display requirement found in Task 9, development (`test.sh`, `export-sample.sh`, `RENDERER=`).
- [ ] **Step 2: Verify** headers and the whole suite: `grep -L "GNU General Public License" $(git ls-files '*.java')` prints nothing; `scripts/test.sh` and `scripts/export-sample.sh` pass from a clean `rm -rf build`.
- [ ] **Step 3: Commit** `Add README`.
- [ ] **Step 4: Ask the user before publishing.** Creating the public repository `valsr/ha-floorplan-exporter` and pushing is outward-facing and not undoable; confirm the name and visibility with the user, then `gh repo create valsr/ha-floorplan-exporter --public --source . --push`.
