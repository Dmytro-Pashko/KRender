# Step 11 — Add FreeType bitmap font rasterizer

## Summary
Added AWT-based font rasterizer behind the `FontRasterizer` interface. Uses `java.awt.Font`
and `java.awt.Graphics2D` — no external FreeType dependency needed for MVP.

## Files changed
- **Created** `engine/tools/.../common/bitmapfont/generator/AwtFontRasterizer.kt` — loads .ttf/.otf via `Font.createFont()`, rasterizes individual glyphs to RGBA bitmaps, extracts metrics.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- AWT is always available on desktop JVM — no new dependencies.
- `FontRasterizer` interface allows swapping in a FreeType-based implementation later.
- Supports antialias and fractional metrics hints from config.
- Reports missing codepoints as diagnostics.
- Produces RGBA byte arrays per glyph suitable for texture packing.
