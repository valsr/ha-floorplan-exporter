# HA Floorplan Exporter

A [Sweet Home 3D](https://www.sweethome3d.com/) plugin that renders a home as the image layers
[ha-floorplan](https://experiencelovelace.github.io/ha-floorplan/) stacks in Home Assistant:

- **base images** of each floor at the dates and times of day you choose, all lights off;
- one **transparent overlay per light**, holding only what that light adds, to show over a base
  image when the light is on.

All the images of a floor are taken from the same point of view at the same size, so they line
up pixel for pixel. The same export runs from a dialog in Sweet Home 3D or from the command line.

## Output

```
<output>/
  manifest.json
  <floor>/
    base/<YYYY-MM-DD>_<HHmm>.png    one per date and time, lights off
    night.png                       lights off at night, only if the floor has exported lights
    lights/<light>.png              transparent overlay of one light
```

`manifest.json` lists every image with the floor, date, time or light it belongs to, for a tool
that would generate the ha-floorplan configuration. It is written last: a folder without it comes
from an export that failed or was cancelled.

Overlays are computed against `night.png`. Drawn over that image they reproduce the lit render
exactly; over a daytime base they are an approximation.

## Requirements

- Sweet Home 3D 7.5 and a JDK (8 or later to run, any recent one to build).
- A display, also for the command line: Java 3D, which Sweet Home 3D renderers use to build the
  scene, does not load in headless mode. `xvfb-run` is enough.
- Optional: the Blender GPU renderer for Sweet Home 3D (`gpu-renderer.jar`) and Blender 4 or later.

The scripts look for Sweet Home 3D where the Arch / Manjaro package installs it. Elsewhere, set
`SH3D_JARS` (folder of `SweetHome3D.jar`) and `SH3D_LIB` (folder containing `java3d-1.5/`).

## Install

    scripts/build.sh      # build/HaFloorplanExporter-<version>.sh3p
    scripts/install.sh    # copies it to ~/.eteks/sweethome3d/plugins

Restart Sweet Home 3D; **Tools > Export for HA Floorplan…** appears.

## Using the dialog

- **Floors**: tick the floors to export and choose for each the stored point of view it is seen
  from (*3D view > Store point of view* in Sweet Home 3D), or the current 3D view.
- **Dates** and **Times of day**: a start, an end and an interval. Both ends are included; one
  base image is rendered per floor, date and time.
- **Lights**: the lights to make overlays for. A light is rendered on the floor it stands on,
  at night, at the power it has in the home.
- **Hide ceilings** (on by default) removes the ceilings of the exported floor so it can be seen
  from above.
- **Hide other levels** renders each floor alone. When off, a floor is rendered with the levels
  under it, as Sweet Home 3D shows it.
- **Renderer** lists the photo renderers Sweet Home 3D has available, at its two ray-traced
  quality levels.

The choices are stored in the home and come back the next time the dialog opens.

**Save instructions…** writes the choices in a JSON file that the command line can run, and
**Load instructions…** reads one back. Loading a file made for another home keeps what matches
by name and lists the rest.

## Command line

    scripts/export.sh house.ha-floorplan.json
    scripts/export.sh job.json --home other.sh3d --output /tmp/out
    scripts/export.sh - --home house.sh3d < job.json

- `--home` and `--output` replace the `home` and `output` of the instructions.
- Relative paths written in an instructions file start from the folder of that file; paths given
  as arguments, or in instructions read from standard input, start from the current folder.
- The home is read from its file: save it in Sweet Home 3D before exporting.
- Exit code 0 on success, 1 if rendering failed, 2 if the arguments, the instructions or the home
  can't be used. Unlike the dialog, the command line refuses instructions naming a floor, a point
  of view or a light the home doesn't have.

## Instructions file

```json
{
  "version": 1,
  "home": "house.sh3d",
  "output": "out",
  "floors": [
    {"level": "Ground floor", "camera": "Top ground"},
    {"level": {"id": "level-1a2b", "name": "First floor"}}
  ],
  "dates": {"start": "2026-01-01", "end": "2026-12-31", "intervalDays": 30},
  "times": {"start": "00:00", "end": "23:00", "intervalMinutes": 240},
  "lights": "*",
  "width": 1920,
  "height": 1080,
  "renderer": "Blender Cycles (GPU)",
  "quality": "LOW",
  "hideCeilings": true,
  "isolateLevel": false,
  "noiseThreshold": 6
}
```

| Key | Required | Default | Meaning |
|---|---|---|---|
| `version` | yes | | `1` |
| `home` | no | | The `.sh3d` file, unless given with `--home` |
| `output` | unless `--output` | | Output folder |
| `floors` | yes | | Array of `{level, camera}`, or `"*"` for every floor from the current view. Without `camera`, the view the home was saved with |
| `dates` | yes | | `start`, `end` (`YYYY-MM-DD`) and `intervalDays` |
| `times` | yes | | `start`, `end` (`HH:mm`) and `intervalMinutes` |
| `lights` | no | `"*"` | Array of lights, `"*"` for all, `[]` for none |
| `width`, `height` | no | 1920, 1080 | Image size in pixels |
| `renderer` | no | first available | Class name, class name without package or displayed name, in any case: `SunFlow`, `BlenderRenderer`, `Blender Cycles (GPU)`… |
| `quality` | no | `"LOW"` | `"LOW"` or `"HIGH"` |
| `hideCeilings` | no | `true` | |
| `isolateLevel` | no | `false` | Render each floor without the other levels |
| `noiseThreshold` | no | `6` | Brightening, from 0 to 255, under which a pixel is left out of a light overlay |

A level, a point of view or a light is written as its name, or as `{"id": …, "name": …}`, the
form the dialog saves: the id is tried first, then the name, so the file survives a renaming and
still works in a copy of the home. A name shared by two objects is refused. Unknown keys are
refused too, to catch typing mistakes.

## Blender GPU renderer

The dialog offers the renderer when Sweet Home 3D is started with its Java agent, as that
project's README explains. For the command line, `scripts/export.sh` adds the agent itself from the first of:

1. `$SH3D_GPU_RENDERER_JAR`, if set (set it empty to do without the renderer);
2. `/usr/lib/sweethome3d/gpu-renderer/gpu-renderer.jar`;
3. `../gpu-renderer/build/gpu-renderer.jar` beside this repository.

Blender is started once per floor for all its base images, then once per light.

## Development

    scripts/test.sh                                  # unit tests (downloads the JUnit console jar into lib/)
    scripts/export-sample.sh                         # end-to-end export of a generated home with SunFlow
    RENDERER=BlenderRenderer scripts/export-sample.sh
    KEEP=1 scripts/export-sample.sh                  # keeps build/sample/ to look at the images
    scripts/run.sh [home.sh3d]                       # build, install and start Sweet Home 3D

Sources are in four packages under `io.github.valsr.hafloorplan`: `plan` (schedules, instructions
file, list of images, manifest; no Sweet Home 3D classes), `engine` (runs an export on a copy of
the home), `plugin` (the dialog) and `cli` (the command line). `src/main` is compiled for Java 8.
The design is described in `docs/superpowers/specs/`.

## License

GNU General Public License, version 2 or later. See `LICENSE`.
