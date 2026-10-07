# Shared settings, sourced by the other scripts.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Where Sweet Home 3D is installed (defaults: Arch / Manjaro package)
SH3D_JARS="${SH3D_JARS:-/usr/share/java/sweethome3d}"
SH3D_LIB="${SH3D_LIB:-/usr/lib/sweethome3d}"

if [ ! -f "$SH3D_JARS/SweetHome3D.jar" ]; then
  echo "SweetHome3D.jar not found in $SH3D_JARS" >&2
  exit 1
fi

SH3D_CP="$(printf '%s:' "$SH3D_JARS"/*.jar "$SH3D_LIB"/java3d-1.5/*.jar)"
SH3D_CP="${SH3D_CP%:}"
# Same library path as the launcher of Sweet Home 3D: Java 3D, then YafaRay which is found through it
SH3D_JAVA_OPTS="--add-opens=java.desktop/sun.awt=ALL-UNNAMED -Djava.library.path=$SH3D_LIB/java3d-1.5:$SH3D_LIB/yafaray"

VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
PLUGIN_FILE="$ROOT/build/HaFloorplanExporter-$VERSION.sh3p"

# Blender GPU renderer agent: kept if already set (empty disables it),
# otherwise the installed jar, then a sibling checkout
if [ -z "${SH3D_GPU_RENDERER_JAR+set}" ]; then
  SH3D_GPU_RENDERER_JAR=""
  for jar in "$SH3D_LIB/gpu-renderer/gpu-renderer.jar" "$ROOT/../gpu-renderer/build/gpu-renderer.jar"; do
    if [ -f "$jar" ]; then
      SH3D_GPU_RENDERER_JAR="$(cd "$(dirname "$jar")" && pwd)/gpu-renderer.jar"
      break
    fi
  done
fi
