#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

MODE="check"
if [[ $# -gt 1 ]]; then
  echo "Usage: ./scripts/format_check.sh [--fix]" >&2
  exit 2
fi
if [[ $# -eq 1 ]]; then
  if [[ "$1" == "--fix" ]]; then
    MODE="fix"
  else
    echo "Usage: ./scripts/format_check.sh [--fix]" >&2
    exit 2
  fi
fi

to_windows_path() {
  local path="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -aw "${path}"
  elif command -v wslpath >/dev/null 2>&1; then
    wslpath -w "${path}"
  else
    printf '%s\n' "${path}"
  fi
}

GRADLEW_SH="${ROOT_DIR}/gradlew"
GRADLEW_BAT="${ROOT_DIR}/gradlew.bat"

if [[ ! -x "${GRADLEW_SH}" ]]; then
  chmod +x "${GRADLEW_SH}" 2>/dev/null || true
fi

if command -v cmd.exe >/dev/null 2>&1 && [[ -f "${GRADLEW_BAT}" ]]; then
  GRADLE_BASE=("cmd.exe" "/c" "$(to_windows_path "${GRADLEW_BAT}")" "--no-daemon" "--console=plain")
else
  GRADLE_BASE=("${GRADLEW_SH}" "--no-daemon" "--console=plain")
fi

if [[ "${MODE}" == "fix" ]]; then
  echo "Running formatter: ${GRADLE_BASE[*]} ktlintFormat"
  "${GRADLE_BASE[@]}" ktlintFormat
fi

echo "Running format check: ${GRADLE_BASE[*]} ktlintCheck"
"${GRADLE_BASE[@]}" ktlintCheck
