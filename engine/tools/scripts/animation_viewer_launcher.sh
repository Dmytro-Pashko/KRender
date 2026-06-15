#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${ROOT_DIR}"

MODEL_PATH="${1:-model/wool_boy_animated.glb}"
if [[ $# -gt 0 ]]; then
  shift
fi

./gradlew :lwjgl3:run -Pkrender.scene=animation-viewer -Pkrender.model.path="${MODEL_PATH}" "$@"
