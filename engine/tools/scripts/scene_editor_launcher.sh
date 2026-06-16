#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${ROOT_DIR}"

if [[ $# -gt 0 ]]; then
  SCENE_PATH="$1"
  shift
  ./gradlew :desktop-lwjgl3:run -Pkrender.scene=scene-editor -Pkrender.scene.path="${SCENE_PATH}" "$@"
else
  ./gradlew :desktop-lwjgl3:run -Pkrender.scene=scene-editor
fi
