# Step 09 — Add bitmap font generation config

## Summary
Added charset presets, Unicode code point model, generation config, and full generation panel UI.

## Files changed
- **Created** `engine/tools/.../common/bitmapfont/charset/UnicodeCodePoint.kt` — `@JvmInline value class UnicodeCodePoint`.
- **Created** `engine/tools/.../common/bitmapfont/charset/CharsetPreset.kt` — presets + `CharsetBuilder`.
- **Created** `engine/tools/.../common/bitmapfont/generator/BitmapFontGenerationConfig.kt` — config + diagnostics.
- **Modified** `engine/tools/.../bitmapfonteditor/panels/FontGenerationPanel.kt` — full UI with source font, size, charset, padding, spacing, page size controls.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Charsets include Ukrainian-specific Cyrillic: Ґ ґ Є є І і Ї ї.
- Default preset: `ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC`.
- `UnicodeCodePoint` uses `Int` internally for future non-BMP support.
- Generation panel updates `BitmapFontEditorMetadata` in editor state and marks dirty.
