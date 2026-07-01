# Step 08 — Editable Environment Settings

## Goal

Allow editing runtime environment parameters and saving them back to manifest.

## Changes

- Added `EnvironmentSettingsPanel` with editable fields using imgui `slider` extension
- Added `skyboxVisibleHolder` to `EnvironmentEditorState` for checkbox binding

## Editable fields

- Name (text input)
- Exposure (slider 0.01–10)
- Rotation (slider 0–360°)
- Skybox Visible (checkbox)
- Skybox Intensity (slider 0–5)
- Diffuse Intensity (slider 0–5)
- Specular Intensity (slider 0–5)
- Background Mode (radio buttons)

## Save behavior

- Save button writes current state to `.environment.json` via `EnvironmentService.save()`
- Revert button reloads from disk and resets dirty flag

## Dirty state

- Editing any field sets `state.dirty = true`
- Save clears dirty flag
- Revert clears dirty flag
- "[Unsaved changes]" shown when dirty

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentSettingsPanel.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentEditorState.kt` — added skyboxVisibleHolder
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

- No confirmation dialog for Revert — immediately discards changes.
- Background color editing not implemented yet (only mode selection).
