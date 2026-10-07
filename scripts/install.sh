#!/bin/bash
# Installs the built plugin for the current user, replacing older versions
set -euo pipefail
. "$(dirname "$0")/env.sh"

PLUGINS_DIR="$HOME/.eteks/sweethome3d/plugins"
if [ ! -f "$PLUGIN_FILE" ]; then
  echo "Run scripts/build.sh first" >&2
  exit 1
fi
mkdir -p "$PLUGINS_DIR"
rm -f "$PLUGINS_DIR"/HaFloorplanExporter-*.sh3p
cp "$PLUGIN_FILE" "$PLUGINS_DIR/"
echo "Installed $(basename "$PLUGIN_FILE") in $PLUGINS_DIR"
