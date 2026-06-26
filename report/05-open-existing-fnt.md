# Step 05 — Open existing bitmap font files

## Summary
BitmapFontEditor can now open existing .fnt files and display imported data including
font page texture preview, glyph bounds overlay, glyph list, glyph metrics inspector,
and sample text preview.

## Files changed
- **Created** `engine/tools/.../bitmapfonteditor/workflow/OpenBitmapFontWorkflow.kt` — parses .fnt, resolves page PNG, queues texture loading.
- **Modified** `engine/tools/.../bitmapfonteditor/BitmapFontEditorScene.kt` — added `OpenBitmapFontWorkflow` call on show, added `BitmapFontEditorPreviewSyncSystem`.
- **Modified** `engine/tools/.../bitmapfonteditor/panels/FontPageCanvasPanel.kt` — full canvas with texture rendering via `drawList.addImage`, checkerboard, grid, glyph bounds overlay via `FontPreviewOverlays`, sample text preview, zoom/pan interaction, glyph hit testing.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Page texture is resolved relative to `.fnt` parent directory, then converted to asset-root-relative path for `AssetService.queue()`.
- `BitmapFontEditorPreviewSyncSystem` runs each frame to sync the `TexturePreviewHandle` from the asset service once the texture is loaded.
- Canvas supports zoom (mouse wheel), pan (right-drag), glyph selection (left-click), and glyph hover highlighting.
- Sample text input and preview toggle are in the canvas options bar.
- Diagnostics panel shows parse diagnostics.
- Inspector panel shows font metadata and selected glyph metrics.
