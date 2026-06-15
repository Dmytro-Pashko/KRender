#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${ROOT_DIR}"

UI_SCENE_PATH="${1:-ui/scenes/test_scene_01.krui}"
if [[ $# -gt 0 ]]; then
  shift
fi

./gradlew :lwjgl3:run -Pkrender.scene=ui-composer -Pkrender.ui.scene.path="${UI_SCENE_PATH}" "$@"
