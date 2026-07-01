- context action added
  - Added Asset Browser custom action plumbing via `AssetBrowserOperationsHandler.actionsFor(...)` and `runAction(...)`.
  - Added right-click and details-panel actions for HDR source assets:
    - `Create Environment from EXR`
    - `Create Environment from HDR`

- manifest creation logic
  - Added `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/assetbrowser/EnvironmentAssetCreation.kt`.
  - The helper:
    - resolves the selected HDR source from the shared asset root
    - creates `environments/<environment_id>/`
    - writes `<environment_id>.environment.json`
    - seeds one default `sources` entry
    - writes placeholder generated skybox / irradiance / radiance / BRDF LUT output paths
    - saves and reloads the manifest through `DefaultEnvironmentService`
    - launches Environment Editor for the new manifest

- source file handling: copied or referenced
  - Copied.
  - Selected `.exr` / `.hdr` files are copied into:
    - `environments/<environment_id>/sources/<original_file_name>`
  - Manifest source path is stored as:
    - `sources/<original_file_name>`

- created folder/path convention
  - Source: `.../studio_2k.exr`
  - Result:
    - `environments/studio_2k/studio_2k.environment.json`
    - `environments/studio_2k/sources/studio_2k.exr`
  - If the target folder already exists, suffixes increment as:
    - `studio_2k_1`
    - `studio_2k_2`
    - etc.

- limitations
  - No real IBL generation is performed.
  - Generated resource files are only declared as placeholder output paths.
  - Validation is expected to report missing generated maps until generation is implemented or run later.

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.
