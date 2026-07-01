# Step 01 — Environment Domain Model

## Goal

Introduce platform-independent Environment asset data models in `core`.

## Changes

- Added `EnvironmentAssetId` inline value class
- Added `EnvironmentAsset` top-level data model
- Added `EnvironmentType` enum (HdrIbl, ProceduralSky, SolidColor, GradientSky)
- Added `EnvironmentMetadata` data class
- Added `EnvironmentSettings` with exposure, rotation, intensities, background mode
- Added `BackgroundMode` enum (Skybox, SolidColor, Transparent, None)
- Added `EnvironmentColor` serializable RGBA color
- Added `EnvironmentSourceVariant` with format, role, resolution
- Added `EnvironmentSourceFormat` enum (EXR, HDR)
- Added `SourceVariantRole` enum (Source, Preview, BakeInput, RuntimeFallback)
- Added `EnvironmentGeneratedResources` with skybox, irradiance, radiance, brdfLut
- Added `SkyboxResourceSet`, `CubemapResource`, `RadianceMipChain`, `RadianceMip`, `TextureResourceRef`
- Added `EnvironmentGenerationSettings` for generator parameters
- Added `EnvironmentValidationReport`, `ValidationStatus`, `EnvironmentIssue`, `IssueSeverity`

## Files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentAsset.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentSettings.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentSourceVariant.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentGeneratedResources.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentGenerationSettings.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentValidation.kt`

## Package location

`com.pashkd.krender.engine.assets.environment` — alongside existing `hdr/` package in the core asset layer.
This is backend-independent: no Gdx, Texture, Cubemap, FileHandle, or ImGui imports.

## Deviations from proposed model

- Used `String` for `manifestPath` and `relatedPath` instead of `AssetPath` — no `AssetPath` type exists in the project (it's a string convention).
- Added `EnvironmentColor` as a separate serializable color instead of reusing `com.pashkd.krender.engine.api.Color` which is mutable and not `@Serializable`.
- `EnvironmentAsset` itself is not `@Serializable` — the manifest serialization will use a dedicated DTO in Step 2 to decouple domain model from JSON shape.
- Serializable sub-models (`EnvironmentSettings`, source/generated types) are annotated for direct manifest embedding.

## Validation

Command:

```bash
./gradlew :core:compileKotlin --no-daemon -q
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- `EnvironmentAsset` is a plain data class, not yet loaded from manifests (Step 2).
- Validation logic not yet implemented (Step 3).
- No service abstraction yet (Step 4).
