- environment manifest open flow
  - `.environment.json` assets still route through `EnvironmentEditorAssetTool`.
  - `EnvironmentEditorAssetTool.canOpen(...)` remains restricted to:
    - `AssetCategory.Environment`
    - `AssetType.Environment`
  - This keeps direct open behavior explicit for manifests only.

- EXR create flow
  - `.exr` and `.hdr` assets now expose:
    - `Create Environment from EXR`
    - `Create Environment from HDR`
  - The action writes a new manifest, copies the source into the new environment folder, validates loadability, and launches Environment Editor for the created manifest.

- generated asset behavior
  - Added `Open Parent Environment` for:
    - `EnvironmentSkybox`
    - `EnvironmentCubemap`
    - `EnvironmentGeneratedMap`
    - `BrdfLut`
    - `HdrSource` when the source already lives under `environments/`
  - Parent lookup walks upward from the selected asset path to the closest `.environment.json` and opens it in Environment Editor.

- removed legacy Skybox behavior
  - There is no standalone Skybox browser category anymore.
  - Legacy `.krskybox` descriptors are indexed under `AssetCategory.Environment`.
  - Generated skybox/irradiance/radiance/BRDF resources no longer rely on the legacy Skybox browser flow.

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.
