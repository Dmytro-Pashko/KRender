# Step 07 — Environment Inspector Panel

## Goal

Allow inspection of all Environment Asset data.

## Changes

- Added `EnvironmentInspectorPanel` — read-only panel with collapsible sections
- Registered in `EnvironmentEditorScene`

## Fields displayed

- **Identity**: id, name, type, version, manifest path, description
- **Settings**: exposure, rotation, skybox visible, skybox/diffuse/specular intensity, background mode, background color
- **Sources**: variant id, format, role, default flag, path, resolution, color space, dynamic range
- **Generated Resources**: skybox summary, irradiance, radiance, BRDF LUT
- **Generation Settings**: source variant, generator, version, timestamps, resolutions, mip count, output format
- **Metadata**: author, tags, created/modified timestamps (only shown when present)

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentInspectorPanel.kt` — new
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

- All sections are read-only in this panel (editing is in the Settings panel, Step 8).
