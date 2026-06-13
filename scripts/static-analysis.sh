#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

REPORT_DIR="${ROOT_DIR}/build/reports/static-analysis"
LOG_DIR="${REPORT_DIR}/logs"
SUMMARY="${REPORT_DIR}/summary.md"
mkdir -p "${REPORT_DIR}" "${LOG_DIR}"

MODE="check"
if [[ $# -gt 1 ]]; then
  echo "Usage: ./scripts/static-analysis.sh [--fix]" >&2
  exit 2
fi
if [[ $# -eq 1 ]]; then
  if [[ "$1" == "--fix" ]]; then
    MODE="fix"
  else
    echo "Usage: ./scripts/static-analysis.sh [--fix]" >&2
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

declare -A STEP_EXIT_CODES
declare -A STEP_LOGS
declare -A STEP_COMMANDS

run_step() {
  local step_name="$1"
  shift

  local log_file="${LOG_DIR}/${step_name}.log"
  STEP_LOGS["${step_name}"]="${log_file}"
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

if command -v cmd.exe >/dev/null 2>&1 && [[ -f "${GRADLEW_BAT}" ]]; then
  GRADLE_BASE=("cmd.exe" "/c" "$(to_windows_path "${GRADLEW_BAT}")" "--no-daemon" "--console=plain")
else
  GRADLE_BASE=("${GRADLEW_SH}" "--no-daemon" "--console=plain")
fi

if [[ "${MODE}" == "fix" ]]; then
  run_step "format-apply" "${GRADLE_BASE[@]}" ktlintFormat
fi
run_step "format-check" "${GRADLE_BASE[@]}" ktlintCheck
run_step "detekt" "${GRADLE_BASE[@]}" detekt
run_step "tests" "${GRADLE_BASE[@]}" test
run_step "coverage" "${GRADLE_BASE[@]}" unitTestCoverageReport

OVERALL="PASS"
FINAL_EXIT=0
for step_name in "format-apply" "format-check" "detekt" "tests" "coverage"; do
  if [[ "$(status_label "${step_name}")" == "FAIL" ]]; then
    OVERALL="FAIL"
    FINAL_EXIT=1
  fi
done

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
DETEKT_HTML="build/reports/detekt/detekt.html"
DETEKT_MD="build/reports/detekt/detekt.md"
DETEKT_XML="build/reports/detekt/detekt.xml"
DETEKT_SARIF="build/reports/detekt/detekt.sarif"
TEST_REPORT="core/build/reports/tests/test/index.html"
COVERAGE_HTML="build/reports/coverage/unit/html/index.html"
COVERAGE_XML="build/reports/coverage/unit/jacoco.xml"
COVERAGE_CSV="build/reports/coverage/unit/jacoco.csv"

{
  echo "# KRender Static Analysis Report"
  echo
  echo "Generated: ${GENERATED_AT}"
  echo
  echo "## Result"
  echo
  echo "Overall: ${OVERALL}"
  echo
  echo "Mode: ${MODE}"
  echo
  echo "## Commands"
  echo
  echo "| Check | Result | Command |"
  echo "|---|---|---|"
  if [[ "${MODE}" == "fix" ]]; then
    echo "| Formatting apply | $(status_label "format-apply") | \`${STEP_COMMANDS[format-apply]}\` |"
  fi
  echo "| Formatting / ktlint | $(status_label "format-check") | \`${STEP_COMMANDS[format-check]}\` |"
  echo "| Detekt | $(status_label "detekt") | \`${STEP_COMMANDS[detekt]}\` |"
  echo "| Tests / architecture checks | $(status_label "tests") | \`${STEP_COMMANDS[tests]}\` |"
  echo "| Coverage / JaCoCo | $(status_label "coverage") | \`${STEP_COMMANDS[coverage]}\` |"
  echo
  echo "## Reports"
  echo
  echo "- Summary: \`build/reports/static-analysis/summary.md\`"
  echo "- Detekt HTML: \`${DETEKT_HTML}\`"
  echo "- Detekt Markdown: \`${DETEKT_MD}\`"
  echo "- Detekt XML: \`${DETEKT_XML}\`"
  echo "- Detekt SARIF: \`${DETEKT_SARIF}\`"
  echo "- Test report: \`${TEST_REPORT}\`"
  echo "- Coverage HTML: \`${COVERAGE_HTML}\`"
  echo "- Coverage XML: \`${COVERAGE_XML}\`"
  echo "- Coverage CSV: \`${COVERAGE_CSV}\`"
  echo
  echo "## Main Problems"
  echo
  if [[ "$(status_label "format-check")" == "FAIL" ]]; then
    echo "- Formatting check failed. Run \`./scripts/static-analysis.sh --fix\` for safe Kotlin formatting."
  fi
  if [[ -f "${ROOT_DIR}/${DETEKT_MD}" ]]; then
    echo "- Review the Detekt findings in \`${DETEKT_MD}\`."
  else
    echo "- Detekt markdown report was not generated."
  fi
  if [[ "$(status_label "tests")" == "FAIL" ]]; then
    echo "- Review the test and architecture failures in \`${TEST_REPORT}\`."
  fi
  if [[ "$(status_label "coverage")" == "FAIL" ]]; then
    echo "- Coverage report generation failed. Review \`${STEP_LOGS[coverage]}\` and rerun \`./gradlew unitTestCoverageReport\`."
  fi
  if [[ "${OVERALL}" == "PASS" ]]; then
    echo "- No failing static-analysis steps were detected."
  fi
  echo
  echo "## Next Steps"
  echo
  echo "- Run \`./scripts/static-analysis.sh --fix\` for formatting issues."
  echo "- Review the Detekt report before changing the baseline."
  echo "- Open \`${COVERAGE_HTML}\` for the latest unit test coverage report."
  echo "- Do not add new \`BackendBoundaryTest\` allowlist entries without review."
} >"${SUMMARY}"

echo "Static analysis summary: ${SUMMARY}"
echo "Detekt HTML report: ${ROOT_DIR}/${DETEKT_HTML}"
echo "Detekt Markdown report: ${ROOT_DIR}/${DETEKT_MD}"
echo "Detekt SARIF report: ${ROOT_DIR}/${DETEKT_SARIF}"
echo "Test report: ${ROOT_DIR}/${TEST_REPORT}"
echo "Coverage HTML report: ${ROOT_DIR}/${COVERAGE_HTML}"
echo "Coverage XML report: ${ROOT_DIR}/${COVERAGE_XML}"

exit ${FINAL_EXIT}
