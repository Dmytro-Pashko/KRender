# Step 04 — Add bitmap font editor tool shell

## Summary
Registered a new standalone BitmapFontEditor tool with empty/safe UI shell panels,
scene routing, desktop launcher support, and scene config preset.

## Files changed
- **Created** `engine/tools/.../bitmapfonteditor/BitmapFontEditorState.kt` — editor state.
- **Created** `engine/tools/.../bitmapfonteditor/BitmapFontEditorController.kt` — controller skeleton.
- **Created** `engine/tools/.../bitmapfonteditor/BitmapFontEditorLayout.kt` — panel IDs and layout defaults.
- **Created** `engine/tools/.../bitmapfonteditor/BitmapFontEditorScene.kt` — scene with UI system.
- **Created** `engine/tools/.../bitmapfonteditor/panels/BitmapFontToolbarPanel.kt`
- **Created** `engine/tools/.../bitmapfonteditor/panels/FontPageCanvasPanel.kt`
- **Created** `engine/tools/.../bitmapfonteditor/panels/GlyphListPanel.kt`
- **Created** `engine/tools/.../bitmapfonteditor/panels/GlyphInspectorPanel.kt`
- **Created** `engine/tools/.../bitmapfonteditor/panels/FontGenerationPanel.kt`
- **Created** `engine/tools/.../bitmapfonteditor/panels/FontDiagnosticsPanel.kt`
- **Modified** `core/.../scene/SceneConfigPresets.kt` — added `BitmapFontEditor` preset.
- **Modified** `core/.../scene/EditorToolLauncher.kt` — added `launchBitmapFontEditor()`.
- **Modified** `engine/tools/.../ToolsModule.kt` — added `bitmap-font-editor` scene creation with `fontPath` parameter.
- **Modified** `desktop-lwjgl3-{win,macos,linux}/.../DesktopMain.kt` — added `configuredFontPath()` and `fontPath` param.
- **Modified** `desktop-lwjgl3-{win,macos,linux}/.../Lwjgl3EditorToolLauncher.kt` — added `launchBitmapFontEditor()`.

## Compilation
```
./gradlew :core:compileKotlin :engine:tools:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-linux:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Scene name: `bitmap-font-editor`, system property: `krender.font.path`.
- All panels are placeholder shells that display status text.
- Canvas panel captures `CanvasRect` for future preview rendering.
- Controller exposes glyph selection, sample text, zoom, and pan methods.
- No font loading or generation logic yet — that comes in Steps 05+.
