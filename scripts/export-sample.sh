#!/bin/bash
# Exports a small generated home from end to end and checks the images.
#   RENDERER=BlenderRenderer scripts/export-sample.sh   uses another renderer than SunFlow
#   KEEP=1 scripts/export-sample.sh                     keeps build/sample/ to look at the images
set -euo pipefail
. "$(dirname "$0")/env.sh"
cd "$ROOT"

RENDERER="${RENDERER:-SunFlow}"
SAMPLE=build/sample
TEST_CP="build/test-classes:build/classes:$SH3D_CP"

scripts/test.sh --compile-only
rm -rf "$SAMPLE"
mkdir -p "$SAMPLE"
# shellcheck disable=SC2086
java $SH3D_JAVA_OPTS -cp "$TEST_CP" io.github.valsr.hafloorplan.sample.SampleHomeFactory "$SAMPLE/sample.sh3d"

write_job() {  # write_job <file> <output> <isolateLevel>
  cat > "$1" <<JSON
{
  "version": 1,
  "home": "sample.sh3d",
  "output": "$2",
  "floors": [
    {"level": "Ground floor", "camera": "Top ground"},
    {"level": "First floor", "camera": "Top first"}
  ],
  "dates": {"start": "2026-06-21", "end": "2026-06-21", "intervalDays": 1},
  "times": {"start": "00:00", "end": "12:00", "intervalMinutes": 720},
  "lights": "*",
  "width": 320,
  "height": 240,
  "renderer": "$RENDERER",
  "quality": "LOW",
  "isolateLevel": $3
}
JSON
}
write_job "$SAMPLE/job.json" out false
write_job "$SAMPLE/job-isolated.json" out-isolated true

scripts/export.sh "$SAMPLE/job.json"
scripts/export.sh "$SAMPLE/job-isolated.json"

# shellcheck disable=SC2086
java $SH3D_JAVA_OPTS -cp "$TEST_CP" io.github.valsr.hafloorplan.sample.SampleAssertions "$SAMPLE/out" "$SAMPLE/out-isolated"

if [ -z "${KEEP:-}" ]; then
  rm -rf "$SAMPLE"
fi
echo "sample export OK"
