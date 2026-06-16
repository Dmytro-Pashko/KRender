#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${ROOT_DIR}"

case "$(uname -s)" in
  Darwin*) DESKTOP_RUN_TASK=":desktop-lwjgl3-macos:run" ;;
  Linux*) DESKTOP_RUN_TASK=":desktop-lwjgl3-linux:run" ;;
  MINGW* | MSYS* | CYGWIN*) DESKTOP_RUN_TASK=":desktop-lwjgl3-win:run" ;;
  *) DESKTOP_RUN_TASK=":desktop-lwjgl3-linux:run" ;;
esac

UI_SCENE_PATH="${1:-ui/scenes/test_scene_01.krui}"
if [[ $# -gt 0 ]]; then
  shift
fi

./gradlew "${DESKTOP_RUN_TASK}" -Pkrender.scene=ui-composer -Pkrender.ui.scene.path="${UI_SCENE_PATH}" "$@"
