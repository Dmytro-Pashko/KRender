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

STEP_EXIT_CODES=""
TESTS_CMD=""
COVERAGE_CMD=""

run_step() {
  local step_name="$1"
  shift
  local log_file="${LOG_DIR}/${step_name}.log"
  local cmd="${GRADLE_BASE[*]} $*"
  case "${step_name}" in
    tests) TESTS_CMD="${cmd}" ;;
    coverage) COVERAGE_CMD="${cmd}" ;;
  esac
  echo "Running ${step_name}: ${cmd}"
  "${GRADLE_BASE[@]}" "$@" >"${log_file}" 2>&1
  local exit_code=$?
  case "${step_name}" in
    tests) STEP_EXIT_CODES="${exit_code}" ;;
    coverage) STEP_EXIT_CODES="${STEP_EXIT_CODES}:${exit_code}" ;;
  esac
  if [[ ${exit_code} -eq 0 ]]; then
    echo "PASS ${step_name}"
  else
    echo "FAIL ${step_name} (see ${log_file})"
  fi
}

status_label() {
  local exit_code="$1"
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
TESTS_EXIT=$(echo "${STEP_EXIT_CODES}" | cut -d: -f1)
COVERAGE_EXIT=$(echo "${STEP_EXIT_CODES}" | cut -d: -f2)

if [[ ${TESTS_EXIT} -ne 0 || ${COVERAGE_EXIT} -ne 0 ]]; then
  OVERALL="FAIL"
  FINAL_EXIT=1
fi

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
  echo "| Unit tests | $(status_label "${TESTS_EXIT}") | \`${TESTS_CMD}\` |"
  echo "| Coverage | $(status_label "${COVERAGE_EXIT}") | \`${COVERAGE_CMD}\` |"
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
