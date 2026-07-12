# Phase 6 — Environment IBL Generation Design

## What was changed

- Added backend-neutral Environment IBL generation contracts and config models in `core`.
- Added compile-safe design types for:
  - top-level Environment IBL generation config
  - irradiance generation config
  - radiance generation config
  - BRDF LUT generation config
  - output format
  - overwrite policy
  - face naming policy
  - roughness distribution
  - generation result
  - generation service interface
- Documented the intended output model in code through the contract shape:
  - separate irradiance face files
  - separate radiance face files grouped by mip level
  - standalone BRDF LUT output

## Main files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentIblGeneration.kt`

## Architecture decisions

- Placed the design contract in `core` because generation inputs/outputs are backend-neutral data and future implementations should sit behind an abstraction.
- Deliberately did not add any UI wiring or placeholder generation actions.
- Kept the face-file output shape aligned with the Environment Editor V2 runtime decision: no shared runtime atlas for skybox/irradiance/radiance outputs.

## Risks / limitations

- This phase does not add a concrete generator implementation.
- The current contract exposes only `PNG` because that is the active editor/runtime output path we have validated for the importer workflow.
- Advanced naming and roughness strategies are represented as enums but only a single strategy is defined today.

## Compilation command used

`./gradlew.bat :core:compileKotlin`

## Compilation result

Success.

## Next step

Run final cleanup, update the directly relevant Environment Editor docs, produce the final summary report, and do the last compilation pass.
