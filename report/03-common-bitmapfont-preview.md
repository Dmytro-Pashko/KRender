# Step 03 — Extract common bitmap font preview

## Summary
Created common preview types for font glyph overlays, sample text rendering, glyph selection,
and font inspector view model. These can be used by both the BitmapFontEditor and (optionally
in the future) the Texture Atlas Editor.

## Files changed
- **Created** `engine/tools/.../common/bitmapfont/preview/FontPreviewOverlays.kt` — glyph bounds drawing, hit testing, sample text rendering.
- **Created** `engine/tools/.../common/bitmapfont/preview/GlyphSelectionState.kt` — reusable glyph selection/hover/filter state.
- **Created** `engine/tools/.../common/bitmapfont/preview/FontInspectorViewModel.kt` — `FontInspectorData`, `buildFontInspectorData()`, `glyphDisplayLabel()`.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- `FontPreviewOverlays` uses `CanvasViewportLayout` from `common/canvas` — not the atlas editor's `TexturePreviewViewportLayout`.
- Texture Atlas Editor still uses its own overlay code in `TextureAtlasEditorPreviewOverlays`. Migration to common overlays is out of scope for MVP.
- `FontInspectorData` provides a flat view model built from `BitmapFontDocument` — panels render from this instead of reaching into the document directly.
- `glyphDisplayLabel()` provides consistent glyph labeling for both glyph lists and inspector panels.
