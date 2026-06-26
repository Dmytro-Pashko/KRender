# Step 13 — Wire bitmap font generation into editor

## Summary
Users can now generate and preview a bitmap font from the editor UI.

## Files changed
- **Created** `engine/tools/.../bitmapfonteditor/workflow/GenerateBitmapFontWorkflow.kt` — validates config, runs generator, updates editor state with result.
- **Modified** `engine/tools/.../bitmapfonteditor/BitmapFontEditorState.kt` — added `generatedPageRgba`, `generatedPageWidth`, `generatedPageHeight`.
- **Modified** `engine/tools/.../bitmapfonteditor/BitmapFontEditorController.kt` — added `generate()` method.
- **Modified** `engine/tools/.../bitmapfonteditor/panels/FontGenerationPanel.kt` — added Generate button.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Validates source font exists and size is in range before generating.
- Source font path is resolved relative to asset root.
- Generated result updates `state.document` and `state.generatedPageRgba` for preview.
- Diagnostics are shown in the diagnostics panel.
- Marks document dirty after generation.
