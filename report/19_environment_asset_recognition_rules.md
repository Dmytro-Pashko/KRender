- recognition rules added
  - `AssetTypeDetector` now classifies Environment resources before falling back to generic model/texture rules.
  - `LocalAssetRegistryService.describe()` now prefers `AssetTypeDetector.detect(path)` whenever it returns a recognized type, so Environment path heuristics override the generic texture importer for generated maps.

- extensions/path patterns
  - Manifest:
    - `*.environment.json` -> `AssetType.Environment`
  - HDR source:
    - `*.exr` -> `AssetType.HdrSource`
    - `*.hdr` -> `AssetType.HdrSource`
  - Legacy descriptor:
    - `*.krskybox` -> `AssetType.EnvironmentSkybox`
  - Environment-generated / environment-grouped textures:
    - paths under `environments/`
    - paths under `skyboxes/`
    - paths containing `generated/skybox`
    - paths containing `generated/irradiance`
    - paths containing `generated/radiance`
    - paths containing `cubemap`
    - paths containing `brdf_lut`
    - cubemap face filenames such as `px`, `nx`, `py`, `ny`, `pz`, `nz` or `*_px`, `*-nx`, etc.

- asset types used
  - `Environment`
  - `HdrSource`
  - `EnvironmentSkybox`
  - `EnvironmentCubemap`
  - `EnvironmentGeneratedMap`
  - `BrdfLut`
  - All of the above map to `AssetCategory.Environment`.

- examples
  - `environments/studio/studio.environment.json` -> `Environment`
  - `environments/studio/sources/studio_2k.exr` -> `HdrSource`
  - `environments/studio/generated/skybox/px.ktx` -> `EnvironmentSkybox`
  - `environments/studio/generated/irradiance/irradiance.ktx` -> `EnvironmentCubemap`
  - `environments/studio/generated/radiance/radiance_mip_02.ktx` -> `EnvironmentCubemap`
  - `shared/pbr/brdf_lut.ktx` -> `BrdfLut`
  - `skyboxes/default_skybox_studio.krskybox` -> `EnvironmentSkybox`

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.
