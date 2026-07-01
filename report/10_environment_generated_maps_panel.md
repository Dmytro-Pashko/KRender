# Step 10 — Environment Generated Maps Panel

## Goal

Show generated Environment resources and expose generation actions.

## Changes

- Added `EnvironmentGenerationService` interface in core with `generateSkybox/Irradiance/Radiance/BrdfLut/All`
- Added `EnvironmentGenerationResult` sealed class: `Success`, `Failed`, `NotImplemented`
- Added `PlaceholderEnvironmentGenerationService` — returns `NotImplemented` for all operations
- Added `EnvironmentGeneratedMapsPanel` in tools — displays generated resources + generation buttons

## Generated resources displayed

- **Skybox**: layout, resolution, format, face paths
- **Irradiance**: path, resolution, format
- **Radiance**: base resolution, mip count, per-mip roughness + path
- **BRDF LUT**: path, shared flag

## Generation service interface

```kotlin
interface EnvironmentGenerationService {
    fun generateSkybox(asset: EnvironmentAsset): EnvironmentGenerationResult
    fun generateIrradiance(asset: EnvironmentAsset): EnvironmentGenerationResult
    fun generateRadiance(asset: EnvironmentAsset): EnvironmentGenerationResult
    fun generateBrdfLut(asset: EnvironmentAsset): EnvironmentGenerationResult
    fun generateAll(asset: EnvironmentAsset): EnvironmentGenerationResult
}
```

## Placeholder behavior

All generation actions return `NotImplemented` and display:
"[Name] generator is not implemented yet."

## Files touched

- `core/.../assets/environment/EnvironmentGenerationService.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentGeneratedMapsPanel.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — wiring

## Validation

Command:

```bash
./gradlew :core:compileKotlin :engine:tools:compileKotlin --no-daemon
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- No actual generators — all return NotImplemented
- Generation buttons are synchronous placeholders; future real generators should use `TaskService`
