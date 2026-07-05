# Phase 9 — HDR Skybox Generation Design Or Implementation

## What changed

- Added explicit HDR/EXR environment-generation config/state/controller scaffolding.
- Added reusable projection helpers for cubemap-face direction math and equirectangular UV conversion.
- Added `HdrImageReader`, `HdrImage`, `HdrEnvironmentGenerationService`,
  `EquirectangularToCubemapGenerator`, and `EnvironmentGenerationManifestUpdater`.
- Extended the shared core generation model with first-class skybox generation config and result fields.

## Main files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentIblGeneration.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/HdrImageReader.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/CubemapProjectionMath.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EquirectangularToCubemapGenerator.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/HdrEnvironmentGenerationService.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentGenerationManifestUpdater.kt`

## What is implemented

- `EnvironmentIblGenerationConfig` now uses `environmentManifestPath` and `sourceHdrPath`.
- Added `SkyboxGenerationConfig` and `EnvironmentToneMapping`.
- Added `skyboxFaces` to `EnvironmentIblGenerationResult`.
- Implemented reusable math/projection helpers that can be shared by future skybox, irradiance, and radiance generators.
- Implemented a tool-side equirectangular-to-cubemap PNG generator that is ready to run once a verified HDR reader is supplied.

## What is intentionally not implemented

- No concrete EXR/HDR decoder implementation is provided in this phase.
- No real irradiance convolution, radiance prefiltering, or BRDF LUT generation is provided yet.
- No backend-specific shortcut was added to bypass the missing HDR reader.

## Compile command

```powershell
$env:ORG_GRADLE_PROJECT_kotlin_incremental='false'; .\gradlew.bat --no-daemon :core:compileKotlin
$env:ORG_GRADLE_PROJECT_kotlin_incremental='false'; .\gradlew.bat --no-daemon :engine:tools:compileKotlin
```

## Compile result

- `:core:compileKotlin` succeeded.
- `:engine:tools:compileKotlin` succeeded.

## Known limitations

- The new skybox projection code is compiled and structurally integrated, but it cannot be exercised from the editor until a real HDR/EXR reader is added.
- `Generate All IBL` remains unavailable because the dependent HDR/EXR and BRDF stages are not implemented yet.
