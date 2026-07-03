#!/usr/bin/env bash

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

GRADLE_CMD_DISPLAY="./gradlew --no-daemon --console=plain"
GRADLE_RUN_MODE="sh"
GRADLEW_BAT_WIN=""
WINDOWS_POWERSHELL=""

if [[ -f "${GRADLEW_BAT}" && ( "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ) ]]; then
  GRADLE_RUN_MODE="bat-direct"
  GRADLE_CMD_DISPLAY="./gradlew.bat --no-daemon --console=plain"
elif [[ -f "${GRADLEW_BAT}" && "$(uname -s 2>/dev/null)" == "Linux" && -d /mnt/c && -x /mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe ]]; then
  GRADLE_RUN_MODE="bat-via-powershell"
  GRADLE_CMD_DISPLAY="./gradlew.bat --no-daemon --console=plain"
  GRADLEW_BAT_WIN="$(to_windows_path "${GRADLEW_BAT}")"
  WINDOWS_POWERSHELL="/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"
fi

gradle_command_string() {
  local args=("$@")
  if [[ ${#args[@]} -eq 0 ]]; then
    printf '%s\n' "${GRADLE_CMD_DISPLAY}"
  else
    printf '%s %s\n' "${GRADLE_CMD_DISPLAY}" "${args[*]}"
  fi
}

run_gradle() {
  local args=("$@")
  case "${GRADLE_RUN_MODE}" in
    bat-direct)
      "${GRADLEW_BAT}" --no-daemon --console=plain "${args[@]}"
      ;;
    bat-via-powershell)
      local command="& '${GRADLEW_BAT_WIN}' --no-daemon --console=plain"
      local arg
      for arg in "${args[@]}"; do
        command+=" ${arg}"
      done
      "${WINDOWS_POWERSHELL}" -NoProfile -ExecutionPolicy Bypass -Command "${command}"
      ;;
    *)
      "${GRADLEW_SH}" --no-daemon --console=plain "${args[@]}"
      ;;
  esac
}
