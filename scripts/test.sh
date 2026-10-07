#!/bin/bash
# Runs the unit tests: all of them, or the ones selected by JUnit console
# arguments, for example
#   scripts/test.sh --select-class io.github.valsr.hafloorplan.plan.SlugsTest
# With --compile-only, builds the plugin and the test classes without running them.
set -euo pipefail
. "$(dirname "$0")/env.sh"
cd "$ROOT"

JUNIT_VERSION=1.10.2
JUNIT_JAR="lib/junit-platform-console-standalone-$JUNIT_VERSION.jar"
if [ ! -f "$JUNIT_JAR" ]; then
  mkdir -p lib
  curl -fsSL -o "$JUNIT_JAR" \
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
fi

scripts/build.sh > /dev/null

rm -rf build/test-classes
mkdir -p build/test-classes
find src/test/java -name '*.java' > build/test-sources.txt
javac -nowarn -encoding UTF-8 -cp "build/classes:$SH3D_CP:$JUNIT_JAR" -d build/test-classes @build/test-sources.txt

if [ "${1:-}" = "--compile-only" ]; then
  exit 0
fi
if [ $# -eq 0 ]; then
  set -- --scan-class-path build/test-classes
fi
# shellcheck disable=SC2086
java $SH3D_JAVA_OPTS -jar "$JUNIT_JAR" execute --disable-banner --details=summary \
  --class-path "build/test-classes:build/classes:$SH3D_CP" "$@"
