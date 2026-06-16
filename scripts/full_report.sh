#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

REPORT_DIR="${ROOT_DIR}/build/reports/full-report"
LOG_DIR="${REPORT_DIR}/logs"
SUMMARY="${REPORT_DIR}/summary.md"
mkdir -p "${REPORT_DIR}" "${LOG_DIR}"

declare -A STEP_EXIT_CODES
declare -A STEP_COMMANDS

run_step() {
  local step_name="$1"
  shift
  local log_file="${LOG_DIR}/${step_name}.log"
  STEP_COMMANDS["${step_name}"]="$*"
  echo "Running ${step_name}: $*"
  "$@" >"${log_file}" 2>&1
  local exit_code=$?
  STEP_EXIT_CODES["${step_name}"]=${exit_code}
  if [[ ${exit_code} -eq 0 ]]; then
    echo "PASS ${step_name}"
  else
    echo "FAIL ${step_name} (see ${log_file})"
  fi
}

status_label() {
  local step_name="$1"
  local exit_code="${STEP_EXIT_CODES[${step_name}]:-999}"
  if [[ "${exit_code}" == "999" ]]; then
    echo "SKIPPED"
  elif [[ ${exit_code} -eq 0 ]]; then
    echo "PASS"
  else
    echo "FAIL"
  fi
}

run_step "format-check" "${SCRIPT_DIR}/format_check.sh"
run_step "static-analysis" "${SCRIPT_DIR}/static_analysis.sh"
run_step "unit-test-coverage" "${SCRIPT_DIR}/unit_test_coverage.sh"

OVERALL="PASS"
FINAL_EXIT=0
for step_name in "format-check" "static-analysis" "unit-test-coverage"; do
  if [[ "$(status_label "${step_name}")" == "FAIL" ]]; then
    OVERALL="FAIL"
    FINAL_EXIT=1
  fi
done

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
{
  echo "# KRender Full Report"
  echo
  echo "Generated: ${GENERATED_AT}"
  echo
  echo "Overall: ${OVERALL}"
  echo
  echo "| Check | Result | Command |"
  echo "|---|---|---|"
  echo "| Format | $(status_label "format-check") | \`${STEP_COMMANDS[format-check]}\` |"
  echo "| Static analysis | $(status_label "static-analysis") | \`${STEP_COMMANDS[static-analysis]}\` |"
  echo "| Unit tests + coverage | $(status_label "unit-test-coverage") | \`${STEP_COMMANDS[unit-test-coverage]}\` |"
  echo
  echo "Child reports:"
  echo
  echo "- Static analysis: \`build/reports/static-analysis/summary.md\`"
  echo "- Unit test coverage: \`build/reports/unit-test-coverage/summary.md\`"
} >"${SUMMARY}"

echo "Full report summary: ${SUMMARY}"
exit ${FINAL_EXIT}
