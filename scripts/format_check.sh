#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"
source "${SCRIPT_DIR}/gradle_runner.sh"

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

if [[ "${MODE}" == "fix" ]]; then
  echo "Running formatter: $(gradle_command_string ktlintFormat)"
  run_gradle ktlintFormat
fi

echo "Running format check: $(gradle_command_string ktlintCheck)"
run_gradle ktlintCheck
