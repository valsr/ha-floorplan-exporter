#!/bin/bash
# Exports a home from the command line:
#   export.sh <instructions.json | -> [--home <home.sh3d>] [--output <dir>]
# Needs a display (DISPLAY set, xvfb-run is enough): Java 3D, which every renderer uses to build
# the scene, doesn't load in AWT headless mode.
# Loads the Blender GPU renderer when its jar is found (see SH3D_GPU_RENDERER_JAR in env.sh).
set -euo pipefail
. "$(dirname "$0")/env.sh"

if [ ! -f "$PLUGIN_FILE" ]; then
  "$ROOT/scripts/build.sh" >&2
fi

AGENT=()
if [ -n "$SH3D_GPU_RENDERER_JAR" ] && [[ "${JAVA_TOOL_OPTIONS:-}" != *gpu-renderer.jar* ]]; then
  AGENT=("-javaagent:$SH3D_GPU_RENDERER_JAR")
fi

# shellcheck disable=SC2086
exec java $SH3D_JAVA_OPTS "${AGENT[@]}" -cp "$PLUGIN_FILE:$SH3D_CP" \
  io.github.valsr.hafloorplan.cli.HeadlessExport "$@"
