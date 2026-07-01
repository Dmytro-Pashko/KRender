# Step 12 — Environment Preview Panel

## Goal

Preview the Environment using a manifest/resource summary (Stage A).

## Preview approach

**Stage A (implemented)**: Manifest/resource preview panel showing:
- Environment name and type
- Settings summary (exposure, rotation, background mode, intensities)
- Resource availability (skybox faces, irradiance, radiance mips, BRDF LUT)
- Source variant summary
- Validation status
- Clear messages when resources are missing

**Stage B (future)**: Real-time PBR preview with skybox background, roughness ladder, and test spheres using the glTF/PBR backend. Not implemented in MVP.

## Fallback behavior

- No environment → "No environment selected"
- Load error → error message displayed
- Missing generated maps → "PBR preview unavailable" with guidance to use Generated Maps panel
- Validation warnings → reference to Diagnostics panel

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentPreviewPanel.kt` — new
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

## Known limitations

- No real PBR rendering — text-based resource summary only
- No thumbnail preview
- Stage B requires connecting to the glTF/PBR backend renderer
