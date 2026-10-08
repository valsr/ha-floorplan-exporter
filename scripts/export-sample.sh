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

write_job() {  # write_job <file> <output> <isolateLevel> [<capLight> [<exposure>]]
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
  "isolateLevel": $3,
  "capLight": "${4:-off}",
  "exposure": ${5:-0}
}
JSON
}
write_job "$SAMPLE/job.json" out false
write_job "$SAMPLE/job-isolated.json" out-isolated true

scripts/export.sh "$SAMPLE/job.json"
scripts/export.sh "$SAMPLE/job-isolated.json"

# Only the Blender GPU renderer can stop light with hidden ceilings and levels, and has an exposure setting
CAPPED_OUTPUTS=()
if [[ "${RENDERER,,}" == *blender* ]]; then
  write_job "$SAMPLE/job-sun-capped.json" out-sun-capped false sun
  write_job "$SAMPLE/job-capped.json" out-capped false all
  scripts/export.sh "$SAMPLE/job-sun-capped.json"
  write_job "$SAMPLE/job-exposed.json" out-exposed false all 2
  scripts/export.sh "$SAMPLE/job-capped.json"
  scripts/export.sh "$SAMPLE/job-exposed.json"
  CAPPED_OUTPUTS=("$SAMPLE/out-sun-capped" "$SAMPLE/out-capped" "$SAMPLE/out-exposed")
fi

# shellcheck disable=SC2086
java $SH3D_JAVA_OPTS -cp "$TEST_CP" io.github.valsr.hafloorplan.sample.SampleAssertions "$SAMPLE/out" "$SAMPLE/out-isolated" "${CAPPED_OUTPUTS[@]}"

if [ -z "${KEEP:-}" ]; then
  rm -rf "$SAMPLE"
fi
echo "sample export OK"
