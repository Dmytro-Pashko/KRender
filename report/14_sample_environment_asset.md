# Step 14 — Sample Environment Asset

## Goal

Provide a minimal sample manifest for manual testing.

## Sample asset path

```
assets/environments/dev_default/dev_default.environment.json
```

## Whether real source/generated files exist

- **Source**: `sources/placeholder_2k.exr` — does NOT exist (intentional, for diagnostics testing)
- **Generated skybox faces**: do NOT exist (intentional)
- **Generated irradiance**: does NOT exist (intentional)
- **Generated radiance mips**: do NOT exist (intentional)
- **BRDF LUT**: does NOT exist at referenced path (intentional)

## Expected validation result

- `ENV_SOURCE_FILE_MISSING` warning (placeholder_2k.exr missing)
- `ENV_SKYBOX_FACE_MISSING` warnings (6 faces missing)
- `ENV_IRRADIANCE_FILE_MISSING` warning
- `ENV_RADIANCE_MIP_MISSING` warnings (5 mips missing)
- `ENV_BRDF_LUT_MISSING` warning
- Overall status: `Warning`

## Manual test steps

1. Open Asset Browser
2. Navigate to `environments/dev_default/`
3. Double-click `dev_default.environment.json`
4. Environment Editor opens
5. Verify:
   - Toolbar shows "Dev Default", id "dev_default", type "HdrIbl"
   - Inspector shows all fields
   - Settings panel allows editing exposure/rotation/etc
   - Source Variants shows 1 source with "source_2k" as default
   - Generated Maps shows skybox/irradiance/radiance/BRDF LUT references
   - Diagnostics shows warnings for all missing files
   - Preview shows resource availability summary

## Files touched

- `assets/environments/dev_default/dev_default.environment.json` — new

## Validation

Command:

```bash
./gradlew :core:compileKotlin :engine:tools:compileKotlin --no-daemon -q
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- No real EXR source file included (avoids large binary in repo)
- No real generated KTX files — all diagnostics test missing resource paths
