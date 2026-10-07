#!/bin/bash
# Compiles the plugin and packages build/HaFloorplanExporter-<version>.sh3p
set -euo pipefail
. "$(dirname "$0")/env.sh"
cd "$ROOT"

rm -rf build/classes
mkdir -p build/classes
find src/main/java -name '*.java' > build/sources.txt
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$SH3D_CP" -d build/classes @build/sources.txt
cp -r src/main/resources/. build/classes/
grep -rl '@VERSION@' build/classes --include='*.properties' | while read -r file; do
  sed -i "s/@VERSION@/$VERSION/g" "$file"
done
rm -f build/HaFloorplanExporter-*.sh3p
jar cf "$PLUGIN_FILE" -C build/classes .
echo "Built $PLUGIN_FILE"
