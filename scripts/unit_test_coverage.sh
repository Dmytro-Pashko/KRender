#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

REPORT_DIR="${ROOT_DIR}/build/reports/unit-test-coverage"
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

declare -A STEP_EXIT_CODES
declare -A STEP_COMMANDS

run_step() {
  local step_name="$1"
  shift
  local log_file="${LOG_DIR}/${step_name}.log"
  STEP_COMMANDS["${step_name}"]="${GRADLE_BASE[*]} $*"
  echo "Running ${step_name}: ${STEP_COMMANDS[${step_name}]}"
  "${GRADLE_BASE[@]}" "$@" >"${log_file}" 2>&1
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

run_step "tests" :core:test :engine:scene-player:test
run_step "coverage" unitTestCoverageReport

OVERALL="PASS"
FINAL_EXIT=0
for step_name in "tests" "coverage"; do
  if [[ "$(status_label "${step_name}")" == "FAIL" ]]; then
    OVERALL="FAIL"
    FINAL_EXIT=1
  fi
done

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
{
  echo "# KRender Unit Test Coverage Report"
  echo
  echo "Generated: ${GENERATED_AT}"
  echo
  echo "Overall: ${OVERALL}"
  echo
  echo "| Check | Result | Command |"
  echo "|---|---|---|"
  echo "| Unit tests | $(status_label "tests") | \`${STEP_COMMANDS[tests]}\` |"
  echo "| Coverage | $(status_label "coverage") | \`${STEP_COMMANDS[coverage]}\` |"
  echo
  echo "Reports:"
  echo
  echo "- Core test report: \`core/build/reports/tests/test/index.html\`"
  echo "- Scene Player test report: \`engine/scene-player/build/reports/tests/test/index.html\`"
  echo "- Coverage HTML: \`build/reports/coverage/unit/html/index.html\`"
  echo "- Coverage XML: \`build/reports/coverage/unit/jacoco.xml\`"
  echo "- Coverage CSV: \`build/reports/coverage/unit/jacoco.csv\`"
} >"${SUMMARY}"

echo "Unit test coverage summary: ${SUMMARY}"
exit ${FINAL_EXIT}
