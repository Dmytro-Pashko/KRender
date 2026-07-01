# Step 03 — Environment Validation

## Goal

Provide diagnostics for missing or invalid Environment data.

## Changes

- Added `EnvironmentValidator` object with `validate(asset, fileService)` method
- Returns `EnvironmentValidationReport` with severity-based status rollup

## Validation checks implemented

| Code | Severity | Description |
|---|---|---|
| `ENV_MISSING_NAME` | Error | Name is blank |
| `ENV_MISSING_ID` | Error | ID is blank |
| `ENV_UNSUPPORTED_TYPE` | Warning | Type is not HdrIbl |
| `ENV_NO_SOURCES` | Error | No source variants |
| `ENV_NO_DEFAULT_SOURCE` | Warning | No variant marked default |
| `ENV_SOURCE_FILE_MISSING` | Warning | Source file doesn't exist |
| `ENV_MISSING_SKYBOX` | Warning | No skybox resource set |
| `ENV_SKYBOX_NO_FACES` | Warning | Skybox has no faces |
| `ENV_SKYBOX_FACE_MISSING` | Warning | Individual skybox face missing |
| `ENV_MISSING_IRRADIANCE` | Warning | No irradiance cubemap |
| `ENV_IRRADIANCE_FILE_MISSING` | Warning | Irradiance file missing |
| `ENV_MISSING_RADIANCE` | Warning | No radiance mip chain |
| `ENV_RADIANCE_NO_MIPS` | Warning | Radiance has no mip entries |
| `ENV_RADIANCE_MIP_MISSING` | Warning | Individual radiance mip missing |
| `ENV_MISSING_BRDF_LUT` | Warning | No BRDF LUT reference |
| `ENV_BRDF_LUT_MISSING` | Warning | BRDF LUT file missing |
| `ENV_INVALID_EXPOSURE` | Warning | Non-positive exposure |
| `ENV_INVALID_INTENSITY` | Warning | Negative intensity values |

## Severity rules

- **Error**: manifest is unusable (missing name/id, no sources)
- **Warning**: environment is degraded but loadable (missing generated files, bad settings)
- **Info**: reserved for future informational checks

## Files touched

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentValidator.kt`

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

- File existence checks use `SceneFileService.exists()` — relative path resolution is basic (manifest directory + relative path).
- No check for stale generated resources (generated timestamp vs source modified time).
- No check for resolution consistency between generation settings and generated resources.
