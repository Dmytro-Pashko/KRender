# Step 09 — Environment Source Variants Panel

## Goal

Show Environment source variants clearly.

## Changes

- Added `EnvironmentSourceVariantsPanel` with variant list display
- Registered in `EnvironmentEditorScene`

## Displayed source data

- Variant ID, default flag
- Path, format, role
- Resolution, color space, dynamic range (when present)

## Actions implemented

- "Set as Default" button per variant (marks dirty)

## Validation/status behavior

- Missing source files are flagged by the validation service (shown in Diagnostics panel)

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentSourceVariantsPanel.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — panel registration

## Validation

Command:

```bash
./gradlew :engine:tools:compileKotlin --no-daemon
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- No import wizard for adding new source variants
- No "Reveal path" / "Copy path" actions yet
