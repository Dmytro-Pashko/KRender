- skyboxVisibleHolder fix
  - Added `EnvironmentEditorState.applyLoadedEnvironment(...)` to synchronize loaded asset state in one place.
  - `EnvironmentEditorScene.loadEnvironment()`, `EnvironmentEditorToolbarPanel.reload()`, and `EnvironmentSettingsPanel` revert now all sync `skyboxVisibleHolder` from the loaded asset.
  - This prevents manifests with `"skyboxVisible": false` from being auto-mutated to `true` on first settings draw.

- unknown environmentType behavior
  - Updated `EnvironmentManifestMapper.parseEnvironmentType()` to throw `IllegalArgumentException("Unsupported environmentType '<value>'.")` instead of silently falling back to `EnvironmentType.HdrIbl`.
  - Invalid manifests now fail explicitly during load, which preserves validation visibility instead of hiding unsupported types.

- validation refresh behavior
  - `EnvironmentEditorToolbarPanel.save()` now refreshes `state.validation` immediately after a successful save.
  - `EnvironmentSettingsPanel` save and revert now refresh `state.validation` from the saved/reloaded environment.

- files changed
  - `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentManifestMapper.kt`
  - `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`
  - `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorState.kt`
  - `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorToolbarPanel.kt`
  - `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentSettingsPanel.kt`

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.
