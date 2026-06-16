@echo off
:: ---------------------------------------------------------------------------
:: run-skin_composer.bat
:: Launches Skin Composer (skin_composer.jar) on Windows.
::
:: Resource folder:
::   assets/ui  (two levels up from this script, relative to the repo root)
::   Skin Composer will treat this directory as the project/resource root,
::   so any .scmp project files and generated skin assets live in assets/ui.
::
:: Requirements:
::   - Java 11+ must be on PATH  (verify with: java -version)
::   - skin_composer.jar must be present next to this script in tools/ui/
::
:: Usage:
::   Double-click this file, OR run from any directory:
::     tools\ui\run-skin_composer.bat
:: ---------------------------------------------------------------------------

:: Change to the directory that contains this script so relative paths work
:: regardless of where the user launched the script from.
cd /d "%~dp0"

:: Sanity-check: make sure the jar is present
if not exist "skin_composer.jar" (
    echo [ERROR] skin_composer.jar not found in: %~dp0
    echo         Download it from https://github.com/raeleus/skin-composer/releases
    echo         and place it next to this script.
    pause
    exit /b 1
)

:: Sanity-check: make sure Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found on PATH.
    echo         Install Java 11+ and make sure "java" is available from the command line.
    pause
    exit /b 1
)

:: ---------------------------------------------------------------------------
:: Launch Skin Composer.
:: The path passed as the first argument is the resource / project folder.
:: Skin Composer will open this directory so you can directly load / save
:: .scmp project files and export skin assets inside assets/ui.
:: ---------------------------------------------------------------------------
echo Starting Skin Composer...
echo Resource folder: %~dp0..\..\assets\ui
echo.

java -jar skin_composer.jar "..\..\assets\ui"

:: Keep the window open if the jar exited with an error
if errorlevel 1 (
    echo.
    echo [WARN] Skin Composer exited with error code %errorlevel%.
    pause
)

