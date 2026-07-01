# Step 06 — Environment Editor Tool Shell

## Goal

Add a new editor tool scene that can open and display an Environment asset.

## Changes

- Expanded `EnvironmentEditorScene` with state, service, load/validate workflow
- Added `EnvironmentEditorState` — mutable editor state (environment, validation, dirty, loadError, statusMessage)
- Added `EnvironmentEditorToolbarPanel` — shows environment header, Save/Reload/Validate buttons, status

## Files touched

- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — expanded
- `engine/tools/.../environmenteditor/EnvironmentEditorState.kt` — new
- `engine/tools/.../environmenteditor/EnvironmentEditorToolbarPanel.kt` — new

## Tool registration

- Scene name: `"environment-editor"`
- System property: `krender.environment.path`
- `ToolsModule` routing added in Step 5
- `EditorToolLauncher.launchEnvironmentEditor()` added in Step 5

## Open flow

1. Asset Browser → context menu "Open in Environment Editor" → `EditorToolLauncher.launchEnvironmentEditor(path)`
2. Desktop launcher starts new JVM with `-Dkrender.scene=environment-editor -Dkrender.environment.path=<path>`
3. `ToolsModule.createScene()` → `EnvironmentEditorScene(environmentPath)`
4. `show()` loads environment via `DefaultEnvironmentService`, validates, creates toolbar panel

## State model

```kotlin
class EnvironmentEditorState(
    val manifestPath: String,
    var selectedEnvironmentId: EnvironmentAssetId?,
    var environment: EnvironmentAsset?,
    var validation: EnvironmentValidationReport?,
    var dirty: Boolean,
    var loadError: String?,
    var statusMessage: String?,
)
```

## Validation

Command:

```bash
./gradlew :engine:tools:compileKotlin --no-daemon -q
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- Only toolbar panel exists — Inspector, Settings, Sources, Generated Maps, Diagnostics, Preview panels are added in subsequent steps.
- No ImGui layout config yet — single window layout.
