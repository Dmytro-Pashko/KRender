# Step 12 — Add bitmap font generator

## Summary
Implemented end-to-end bitmap font generation pipeline: rasterize → pack → compose page → build model.

## Files changed
- **Created** `engine/tools/.../common/bitmapfont/generator/BitmapFontGenerator.kt` — orchestrates rasterization, packing, page compositing, and BitmapFontDocument construction.
- **Created** `engine/tools/.../common/bitmapfont/generator/FontPageImageWriter.kt` — writes RGBA byte array to PNG via AWT ImageIO.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Pipeline: resolve charset → rasterize glyphs → pack → blit onto RGBA page → build BitmapFontDocument.
- Single page only; overflow results in failure with diagnostics.
- Page file path in `.fnt` is relative to the `.fnt` file.
- `FontPageImageWriter` converts RGBA byte array to `BufferedImage` and writes PNG.
- `BitmapFontGenerationResult` contains both the document model and raw page image.
