# Step 26 — Environment asset preparation

## Goal

Prepare the HDR environment code for future reusable Environment asset support without building a full Environment Editor yet.

## Files changed

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentAssets.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxHdrEnvironmentResolver.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerState.kt`

## Architecture notes

- Added a backend-neutral `HdrEnvironmentAssets` helper in `core` to centralize canonical HDR environment paths and naming conventions.
- Moved default environment preset/manifest knowledge out of the backend resolver and Model Viewer-local constants so future tools can share the same Environment asset conventions.
- Kept the current manifest-driven resolver flow intact while reducing coupling between the Model Viewer and raw manifest path details.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`
- Result: Passed
- Command: `./gradlew.bat --no-daemon --console=plain :core:ktlintMainSourceSetCheck :engine:backend-gdx:ktlintMainSourceSetCheck :engine:tools:ktlintMainSourceSetCheck`
- Result: Passed

## Commit

- Hash: `95a85964`
- Message: `assets: prepare hdr environment model for future editor support`
