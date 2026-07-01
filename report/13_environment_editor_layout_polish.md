# Step 13 — Environment Editor Layout Polish

## Goal

Make the MVP usable as a coherent tool.

## Final layout

The Environment Editor opens with 7 ImGui windows:

1. **Environment Editor** (toolbar) — name, ID, type, validation status, Save/Reload/Validate buttons, status message
2. **Inspector** — read-only collapsible sections for all environment fields
3. **Settings** — editable runtime parameters with sliders, checkboxes, radio buttons, Save/Revert
4. **Source Variants** — source list with format/role/default, "Set as Default" action
5. **Generated Maps** — skybox/irradiance/radiance/BRDF LUT display, generation action buttons
6. **Diagnostics** — validation issues grouped by severity, Refresh Validation button
7. **Preview** — manifest/resource summary, settings overview, resource availability, status

## Empty states

| State | Message |
|---|---|
| No environment loaded | "No environment loaded" + manifest path |
| Manifest load failure | "ERROR: Failed to load environment" + error + path |
| No sources defined | "No source variants defined." |
| No validation report | "No validation report available." + button |
| No generated maps | "(not defined)" per resource type |
| Missing PBR resources | "PBR preview unavailable: no generated IBL maps." |
| Generator unavailable | "[Name] generator is not implemented yet." |

## Changes

- Removed unused `UiPanel` import from scene
- All panels already have proper empty/error states

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — cleanup

## Validation

Command:

```bash
./gradlew :engine:tools:compileKotlin --no-daemon
```

Result:

```text
SUCCESS
```

## UX limitations

- No dockable panel layout — ImGui windows are freely movable
- No persistent layout configuration
- No keyboard shortcuts
