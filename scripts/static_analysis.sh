#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"
source "${SCRIPT_DIR}/gradle_runner.sh"

REPORT_DIR="${ROOT_DIR}/build/reports/static-analysis"
LOG_DIR="${REPORT_DIR}/logs"
SUMMARY="${REPORT_DIR}/summary.md"
mkdir -p "${REPORT_DIR}" "${LOG_DIR}"

LOG_FILE="${LOG_DIR}/detekt.log"
echo "Running static analysis: $(gradle_command_string detekt)"
run_gradle detekt >"${LOG_FILE}" 2>&1
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
  echo "Command: \`$(gradle_command_string detekt)\`"
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
