#!/bin/bash
# Builds and installs the plugin, then starts Sweet Home 3D: run.sh [home.sh3d]
set -euo pipefail
"$(dirname "$0")/build.sh"
"$(dirname "$0")/install.sh"
exec sweethome3d "$@"
