# Step 02 — Extract common canvas foundation

## Summary
Created common canvas types for reuse by BitmapFontEditor and future tools.
Texture Atlas Editor's deeply coupled canvas code is not refactored (too risky for MVP);
instead, new independent common types are provided for BitmapFontEditor to use directly.

## Files changed
- **Created** `engine/tools/.../common/canvas/CanvasState.kt` — `CanvasZoomMode`, `CanvasViewportState`, `CanvasRect`, `CanvasPreviewState`.
- **Created** `engine/tools/.../common/canvas/CanvasViewportLayout.kt` — `CanvasViewportLayout` data class + `computeCanvasViewportLayout()` function.
- **Created** `engine/tools/.../common/canvas/CanvasOverlays.kt` — `CanvasOverlays` with checkerboard and grid drawing + `packColor()` utility.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- The common canvas types are independent of Texture Atlas Editor types.
- `CanvasOverlays` uses ImGui draw lists for checkerboard and grid — same visual approach as the atlas editor.
- Texture Atlas Editor continues using its own `TextureAtlasEditorPreviewState`, `TextureAtlasEditorCanvasRect`, and `TexturePreviewViewportLayout` — no existing code was modified.
- BitmapFontEditor will use `CanvasRect`, `CanvasPreviewState`, `CanvasViewportLayout`, and `CanvasOverlays` directly.
- Future refactoring could migrate Texture Atlas Editor to use common canvas types, but that is out of scope for this MVP.
