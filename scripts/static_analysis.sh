#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

REPORT_DIR="${ROOT_DIR}/build/reports/static-analysis"
LOG_DIR="${REPORT_DIR}/logs"
SUMMARY="${REPORT_DIR}/summary.md"
mkdir -p "${REPORT_DIR}" "${LOG_DIR}"

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

if [[ -f "${GRADLEW_BAT}" && ( "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ) ]]; then
  GRADLE_BASE=("${GRADLEW_BAT}" "--no-daemon" "--console=plain")
else
  GRADLE_BASE=("${GRADLEW_SH}" "--no-daemon" "--console=plain")
fi

LOG_FILE="${LOG_DIR}/detekt.log"
echo "Running static analysis: ${GRADLE_BASE[*]} detekt"
"${GRADLE_BASE[@]}" detekt >"${LOG_FILE}" 2>&1
EXIT_CODE=$?

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
RESULT="PASS"
if [[ ${EXIT_CODE} -ne 0 ]]; then
  RESULT="FAIL"
fi

{
  echo "# KRender Static Analysis Report"
  echo
  echo "Generated: ${GENERATED_AT}"
  echo
  echo "Result: ${RESULT}"
  echo
  echo "Command: \`${GRADLE_BASE[*]} detekt\`"
  echo
  echo "Reports:"
  echo
  echo "- Detekt HTML: \`build/reports/detekt/detekt.html\`"
  echo "- Detekt Markdown: \`build/reports/detekt/detekt.md\`"
  echo "- Detekt XML: \`build/reports/detekt/detekt.xml\`"
  echo "- Detekt SARIF: \`build/reports/detekt/detekt.sarif\`"
  echo "- Log: \`build/reports/static-analysis/logs/detekt.log\`"
} >"${SUMMARY}"

echo "Static analysis summary: ${SUMMARY}"
echo "Static analysis log: ${LOG_FILE}"
exit ${EXIT_CODE}
