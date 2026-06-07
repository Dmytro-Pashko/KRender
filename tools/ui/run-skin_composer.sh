#!/usr/bin/env bash
# ------------------------------------------------------------------------------
# run-skin_composer.sh
# Launches Skin Composer (skin_composer.jar) on macOS / Linux.
#
# Resource folder:
#   assets/ui  (two levels up from this script, relative to the repo root)
#   Skin Composer will treat this directory as the project/resource root,
#   so any .scmp project files and generated skin assets live in assets/ui.
#
# Requirements:
#   - Java 11+ must be on PATH  (verify with: java -version)
#   - skin_composer.jar must be present next to this script in tools/ui/
#
# Usage:
#   chmod +x tools/ui/run-skin_composer.sh   # first time only
#   ./tools/ui/run-skin_composer.sh           # from repo root, or
#   bash tools/ui/run-skin_composer.sh
# ------------------------------------------------------------------------------

# Resolve the directory that contains this script, following symlinks.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Absolute path to the assets/ui resource folder (two levels up from tools/ui).
RESOURCE_DIR="$(cd "${SCRIPT_DIR}/../../assets/ui" && pwd)"

# ------------------------------------------------------------------------------
# Sanity checks
# ------------------------------------------------------------------------------

# Make sure the jar is present.
JAR="${SCRIPT_DIR}/skin_composer.jar"
if [ ! -f "${JAR}" ]; then
    echo "[ERROR] skin_composer.jar not found at: ${JAR}"
    echo "        Download it from https://github.com/raeleus/skin-composer/releases"
    echo "        and place it next to this script."
    exit 1
fi

# Make sure Java is available.
if ! command -v java &>/dev/null; then
    echo "[ERROR] Java was not found on PATH."
    echo "        Install Java 11+ and make sure 'java' is available from the shell."
    exit 1
fi

# Make sure the resource folder exists (create it silently if missing).
if [ ! -d "${RESOURCE_DIR}" ]; then
    echo "[INFO] Resource folder not found; creating it: ${RESOURCE_DIR}"
    mkdir -p "${RESOURCE_DIR}"
fi

# ------------------------------------------------------------------------------
# Launch Skin Composer.
# The path passed as the first argument is the resource / project folder.
# Skin Composer will open this directory so you can directly load / save
# .scmp project files and export skin assets inside assets/ui.
# ------------------------------------------------------------------------------
echo "Starting Skin Composer..."
echo "Resource folder: ${RESOURCE_DIR}"
echo ""

java -jar "${JAR}" "${RESOURCE_DIR}"

