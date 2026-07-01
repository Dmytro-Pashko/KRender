- UI entry point added
  - Added a dedicated `Create Environment` toolbar action in Asset Browser controls.
  - Extended the existing `Create Asset` dialog with a new `Environment` kind.
  - The dialog now exposes an EXR/HDR source picker and creates the Environment through the same modal flow used for other created resources.

- file picker implementation
  - Reused the existing `FileDialogService` / `AwtFileDialogService`.
  - Added `EnvironmentSourceFileDialogFilters` with:
    - `HDR Environment Sources` (`*.exr`, `*.hdr`)
    - `OpenEXR` (`*.exr`)
    - `Radiance HDR` (`*.hdr`)
  - The Create dialog uses the standard `Browse...` button pattern already used elsewhere in the editor.

- shared creation logic
  - Refactored `EnvironmentAssetCreation` around:
    - `CreateEnvironmentFromSourceRequest`
    - `CreateEnvironmentResult`
  - Both flows now use the same shared implementation:
    - browse-based Create dialog flow
    - existing right-click HDR source action inside Asset Browser
  - The shared creator handles:
    - target id generation
    - asset-root safety
    - source copy
    - manifest construction
    - placeholder generated resources
    - optional Environment Editor launch

- generated folder structure
  - Created Environment assets use:
    - `environments/<environment_id>/<environment_id>.environment.json`
    - `environments/<environment_id>/sources/<source_file_name>`
  - Example for `studio_soft_4k.exr`:
    - `environments/studio_soft_4k/studio_soft_4k.environment.json`
    - `environments/studio_soft_4k/sources/studio_soft_4k.exr`
  - Name collisions are resolved with suffixed ids:
    - `studio_soft_4k_1`
    - `studio_soft_4k_2`

- source copy behavior
  - External EXR/HDR files are copied into the new Environment folder under `sources/`.
  - Manifest source paths are written as project-relative variant paths such as:
    - `sources/studio_soft_4k.exr`
  - Creation validates that generated target paths stay inside the asset root before writing anything.

- manifest example/path
  - The generated manifest is saved as:
    - `environments/<environment_id>/<environment_id>.environment.json`
  - It contains:
    - `schema = "krender.environment"`
    - `schemaVersion = 1`
    - `environmentType = "HdrIbl"`
    - default runtime settings
    - one default `sources` entry pointing at `sources/<file>`
    - placeholder skybox / irradiance / radiance / BRDF LUT outputs
    - generation settings for later IBL generation

- manual test steps
  - Open Asset Browser.
  - Click `Create Environment`.
  - In the Create dialog:
    - choose `Environment`
    - click `Browse...`
    - select an external `.exr`
    - adjust the generated Environment name if desired
    - click `Create`
  - Verify:
    - a new `environments/<id>/` folder exists
    - the EXR file was copied into `sources/`
    - `<id>.environment.json` exists
    - Asset Browser refreshes
    - Environment Editor opens the created manifest
    - diagnostics show missing generated maps until generation is implemented/run

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.

- CI/static-analysis notes
  - Ran `./gradlew.bat staticAnalysis`
  - Initial failures were detekt/ktlint issues in the Environment area.
  - Fixed the reported issues and ran:
    - `./gradlew.bat :core:ktlintFormat :engine:tools:ktlintFormat`
  - Re-ran `./gradlew.bat staticAnalysis`
  - Passed.
