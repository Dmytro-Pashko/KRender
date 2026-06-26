# Step 01 — Extract common bitmap font model and io

## Summary
Extracted reusable BitmapFont model, parser, and writer into `common/bitmapfont/` packages.
Updated Texture Atlas Editor to use common types via type aliases and delegation.

## Files changed
- **Created** `engine/tools/.../common/bitmapfont/model/BitmapFontModel.kt` — common model types with own `BitmapFontDiagnosticSeverity` enum.
- **Created** `engine/tools/.../common/bitmapfont/io/BitmapFontParser.kt` — common parser with `layoutSampleText()`.
- **Created** `engine/tools/.../common/bitmapfont/io/BitmapFontWriter.kt` — common writer with `BitmapFontWriteResult`.
- **Modified** `engine/tools/.../textureatlaseditor/BitmapFontModel.kt` — replaced data classes with type aliases to common model.
- **Modified** `engine/tools/.../textureatlaseditor/BitmapFontParser.kt` — delegates to common parser.
- **Modified** `engine/tools/.../textureatlaseditor/BitmapFontWriter.kt` — delegates to common writer with atlas-specific path validation.
- **Modified** `engine/tools/.../textureatlaseditor/TextureAtlasEditorProjectLoader.kt` — added `toEditorSeverity()` conversion for diagnostic severity.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- `FontPreviewState` remains in the TextureAtlasEditor package because it depends on `TextureAtlasEditorColor`.
- Common `BitmapFontDiagnosticSeverity` has `Info`, `Warning`, `Error` — same values as `TextureAtlasEditorDiagnosticSeverity` but decoupled.
- The `toEditorSeverity()` extension function bridges the two severity enums in `TextureAtlasEditorProjectLoader`.
- Common writer provides a self-contained `BitmapFontWriteResult`; the atlas editor writer maps it to `TextureAtlasEditorFileWriteResult`.
- All existing Texture Atlas Editor behavior is preserved through type aliases and delegation.
