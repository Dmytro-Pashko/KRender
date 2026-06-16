#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${ROOT_DIR}"

TERRAIN_PATH="${1:-terrains/terrain_02_small_flat.json}"
if [[ $# -gt 0 ]]; then
  shift
fi

./gradlew :desktop-lwjgl3:run -Pkrender.scene=terrain-editor -Pkrender.terrain.path="${TERRAIN_PATH}" "$@"
