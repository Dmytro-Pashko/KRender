# Phase 8 — Environment Generation UI Split

## What changed

- Split the Environment Editor `Generation` area into explicit actions:
  `Import Skybox Atlas`, `Generate Skybox From HDR/EXR`, `Generate Irradiance`,
  `Generate Radiance`, `Generate BRDF LUT`, and `Generate All IBL`.
- Renamed the old `Create Skybox` atlas workflow to `Import Skybox Atlas`.
- Added dedicated HDR/EXR generation dialogs with per-action settings and explicit unavailable-state messaging.
- Relaxed Environment validation so empty `sources` no longer make the manifest invalid by themselves.

## Main files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentValidator.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorUiFactory.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentToolsPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/HdrEnvironmentGenerationController.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/HdrEnvironmentGenerationState.kt`

## What is implemented

- Separate atlas import vs HDR/EXR generation concepts in the Environment Editor UI.
- Atlas import keeps using `SkyboxAtlasImportService` and still writes six skybox face PNG files.
- HDR/EXR dialogs expose source path, output path, overwrite policy, and per-action settings.
- Generate buttons no longer silently do nothing; unsupported actions show a visible reason.

## What is intentionally not implemented

- No verified HDR/EXR decoder is wired yet.
- No real irradiance, radiance, BRDF LUT, or combined IBL generation runs in this phase.
- No drag-handle editing for atlas regions.

## Compile command

```powershell
$env:ORG_GRADLE_PROJECT_kotlin_incremental='false'; .\gradlew.bat --no-daemon :core:compileKotlin
$env:ORG_GRADLE_PROJECT_kotlin_incremental='false'; .\gradlew.bat --no-daemon :engine:tools:compileKotlin
```

## Compile result

- `:core:compileKotlin` succeeded.
- `:engine:tools:compileKotlin` succeeded.

## Known limitations

- HDR-oriented dialogs currently describe the intended workflow more than they execute it.
- `Remember HDR/EXR source in Environment metadata` currently stores optional authoring source data only when future generation updates are applied.
