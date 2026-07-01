# Step 04 — Environment Service

## Goal

Introduce a clean service API for loading, saving, and validating Environment assets.

## Changes

- Added `EnvironmentService` interface with `load`, `save`, `validate`
- Added `DefaultEnvironmentService` implementation backed by `EnvironmentManifestIO` + `EnvironmentValidator`

## Service interface

```kotlin
interface EnvironmentService {
    fun load(manifestPath: String): EnvironmentAsset
    fun save(asset: EnvironmentAsset)
    fun validate(asset: EnvironmentAsset): EnvironmentValidationReport
}
```

## Implementation location

Both interface and default implementation live in `core/.../assets/environment/` — no backend dependency needed since IO goes through `SceneFileService`.

## Registration approach

The service is instantiated directly by the Environment Editor tool scene using the `SceneFileService` from `EngineContext`. No global registration on `EngineContext` is needed for the MVP — the service is tool-local. This can be promoted to an engine-wide service later if Scene Editor / Scene Player need it.

## Files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentService.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/DefaultEnvironmentService.kt`

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

- Synchronous API — can be wrapped in `TaskService` coroutines if needed later.
- No caching or change notification — editor tools reload explicitly.
