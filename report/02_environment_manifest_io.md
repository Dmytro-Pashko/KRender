# Step 02 — Environment Manifest IO

## Goal

Support reading and writing `.environment.json` manifests.

## Changes

- Added `EnvironmentManifestDto` — `@Serializable` DTO matching the JSON manifest schema (`krender.environment` v1)
- Added `EnvironmentManifestCodec` — JSON encode/decode via kotlinx.serialization
- Added `EnvironmentManifestMapper` — bidirectional mapping between DTO and domain `EnvironmentAsset`
- Added `EnvironmentManifestIO` — load/save through `SceneFileService`
- Schema constants: `ENVIRONMENT_SCHEMA = "krender.environment"`, `ENVIRONMENT_SCHEMA_VERSION = 1`

## Manifest file naming convention

```
<environment_name>.environment.json
```

## Files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentManifest.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentManifestCodec.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentManifestMapper.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentManifestIO.kt`

## Design decisions

- Manifest DTO is a separate class from the domain model to decouple JSON shape from runtime usage.
- Uses `SceneFileService` for IO — same pattern as scene/terrain persistence, platform-neutral.
- Schema validation occurs during load; unsupported schema or version throws `IllegalArgumentException`.
- `EnvironmentType` parsing falls back to `HdrIbl` for unknown values.

## Known limitations

- No sample `.environment.json` file added yet (Step 14).
- No connection to Asset Browser or UI yet.
- Save preserves all editable parameters by round-tripping through the mapper.

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

- Round-trip fidelity depends on DTO field coverage — additional manifest fields will need DTO updates.
- No file-level locking or conflict detection.
