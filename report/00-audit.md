# Step 00 — Audit existing implementation

## Summary

Inspected current BitmapFont, Texture Atlas Editor, Asset Browser, Skin Editor, runtime text rendering,
and build infrastructure to determine what exists and what must be built for the BitmapFontEditor MVP.

## Key findings

### 1. Does runtime KRender text rendering support kerning pairs?
No dedicated runtime BitmapFont rendering exists in `core` or `engine:backend-gdx`. There is no runtime
kerning lookup. Font rendering is handled by libGDX Scene2D internally through skins, not by engine code.
The Texture Atlas Editor parser preserves kerning pairs from `.fnt` files, but no engine runtime consumes them.

### 2. Does runtime text rendering use top-left or bottom-left coordinates?
The Texture Atlas Editor BitmapFont preview uses **top-left** origin for glyph `x`/`y` — consistent with
BMFont specification and libGDX BitmapFont convention. `yOffset` is interpreted as distance from the top
of the line cell down to the glyph.

### 3. How are xoffset, yoffset, xadvance, lineHeight, and base currently interpreted?
In `layoutSampleText()` (`BitmapFontParser.kt:255-302`):
- `cursorX` starts at 0, advances by `glyph.xAdvance` per glyph.
- Glyph placement x = `cursorX + glyph.xOffset`.
- Glyph placement y = `glyph.yOffset` (from top of line).
- `lineHeight` from `BitmapFontCommon` is reported but single-line only in MVP.
- `base` is stored but not used in layout (it represents baseline offset from the top of the line cell).

### 4. How does Texture Atlas Editor currently parse/preview BitmapFont?
- **Model**: `BitmapFontDocument`, `BitmapFontInfo`, `BitmapFontCommon`, `BitmapFontPage`, `BitmapFontGlyph`,
  `BitmapFontKerning`, `BitmapFontDiagnostic`, `FontPreviewState`, `SampleTextLayout`,
  `SampleTextGlyphPlacement` — all in `textureatlaseditor/BitmapFontModel.kt`.
- **Parser**: `BitmapFontParser` — text BMFont format, validates bounds, resolves page paths relative to
  `.fnt` parent directory — in `textureatlaseditor/BitmapFontParser.kt`.
- **Writer**: `BitmapFontWriter` — writes text BMFont with optional page file overrides — in
  `textureatlaseditor/BitmapFontWriter.kt`.
- **Layout**: `layoutSampleText()` — in `BitmapFontParser.kt` (bottom of file).
- **Operations**: `TextureAtlasBitmapFontOperations` — font resource add/import/export/select glyph/tint/
  sample text/glyph filter — in `TextureAtlasBitmapFontOperations.kt`.
- **Inspector UI**: Font inspector section in `TextureAtlasEditorInspectorPanel` — shows face, size,
  line height, base, glyph list with filter, selected glyph metrics, kerning pairs, diagnostics.
- **Canvas**: Font preview canvas in `TextureAtlasEditorPreviewCanvasPanel.drawFontPreviewCanvas()` — renders
  page texture, glyph bounds overlay, sample text preview, zoom/pan interaction, glyph hit testing.
- **Overlays**: `TextureAtlasEditorPreviewOverlays.drawFontGlyphBounds()`, `hitTestFontGlyph()`,
  `drawFontSampleText()` — shared overlay rendering.
- **Viewport**: `TexturePreviewViewportMath.kt` — `computeTexturePreviewViewportLayout()` with zoom modes,
  pan, checkerboard, grid.

### 5. Which current classes should move to common/bitmapfont?
**Model** (move to `common/bitmapfont/model`):
- `BitmapFontDocument` → needs to be decoupled from `TextureAtlasEditorDiagnosticSeverity` reference.
- `BitmapFontInfo`, `BitmapFontCommon`, `BitmapFontPage`, `BitmapFontGlyph`, `BitmapFontKerning`.
- `FontPreviewState` → needs decoupling from `TextureAtlasEditorColor`.
- `SampleTextLayout`, `SampleTextGlyphPlacement`.

**IO** (move to `common/bitmapfont/io`):
- `BitmapFontParser` — needs to use common diagnostic severity instead of `TextureAtlasEditorDiagnosticSeverity`.
- `BitmapFontWriter` — needs to decouple from `TextureAtlasEditorPathValidator` and
  `TextureAtlasEditorFileWriteResult`.
- `layoutSampleText()` function.

**Diagnostic model**: `BitmapFontDiagnostic` — needs its own severity enum, not the atlas editor one.

### 6. Which current classes should move to common/canvas?
The viewport math and overlay rendering are heavily coupled to atlas-editor-specific types
(`TextureAtlasEditorPreviewState`, `TextureAtlasEditorCanvasRect`, `TextureAtlasCanvasMode`).
For MVP, it is more pragmatic to:
- Extract `TexturePreviewViewportLayout` and `computeTexturePreviewViewportLayout()` into a shared utility,
  but this requires also extracting `TextureAtlasEditorCanvasRect`, zoom mode enum, preview state subset.
- Alternatively, the BitmapFontEditor can define its own lightweight canvas state and viewport layout that
  reuses the same math formulas without importing atlas-editor types.

**Decision for MVP**: Create minimal common canvas types (rect, zoom mode, viewport layout, compute function)
in `common/canvas`. Refactor both Texture Atlas Editor and BitmapFont Editor to use them. Keep overlay
rendering tool-specific.

### 7. How does Asset Browser register/open tools?
- `AssetToolRegistry` holds `AssetTool` implementations, registered in `AssetBrowserScene.show()`.
- Each tool has `id`, `displayName`, `supportedCategories`, `canOpen()`, `open()`.
- `open()` receives `AssetDescriptor` + `EngineContext` and calls `EditorToolLauncher.launchXxx()`.
- The `EditorToolLauncher` interface (in `core`) must be extended with `launchBitmapFontEditor()`.
- Desktop `Lwjgl3EditorToolLauncher` implementations (win/macos/linux) spawn a JVM process with system
  properties `krender.scene=bitmap-font-editor` and `krender.font.path=<path>`.
- `ToolsModule.createScene()` must handle the new scene name.

### 8. How does Style Editor resolve skin-relative font paths?
- `SkinBitmapFontParser` in `skin/` package parses `.fnt` files relative to the skin directory.
- It extracts face, size, line height, char count, ASCII/Ukrainian glyph coverage.
- Font paths in skin JSON are skin-relative. The Skin Editor resolves them from the skin file's parent dir.
- Generated `.fnt` + `.png` must be placeable in the asset tree so skin JSON can reference them.

### 9. Does UI Composer already resolve BitmapFont references?
No. UI Composer resolves skins (`.json`) and `.krui` documents. Fonts are resolved transitively through
skin loading by the libGDX backend. UI Composer does not directly load `.fnt` files.

### 10. Is there an existing compile task used by this repository for editor/tool modules?
Yes: `./gradlew :engine:tools:compileKotlin` — compiles `core` then `engine:tools` without running tests.
For changes that touch `core` and all desktop launchers:
`./gradlew :core:compileKotlin :engine:tools:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-linux:compileKotlin`

## Packer inspection
Texture Atlas Editor has `TextureAtlasPackingPlanner` — it uses a shelf/skyline-based bin packing algorithm
for atlas pages. It is atlas-oriented (works with `TextureAtlasPackingRegion`, atlas resource IDs, multi-page).
For the font editor, a simpler glyph-specific packer is cleaner than trying to adapt the atlas packer.
**Decision**: Implement a dedicated `SkylineGlyphPacker` in `common/bitmapfont/generator`.

## FreeType inspection
No FreeType code exists in the repository. The `engine:tools` module already depends on `com.badlogicgames.gdx:gdx`
(noted as "temporary editor-preview adapter dependency"). For font rasterization, we will create a local
abstraction with `FontRasterizer` interface and implement `AwtFontRasterizer` using `java.awt` for MVP
(Java AWT Font/Graphics2D is available on desktop JVM without external dependencies). This avoids adding
the libGDX FreeType extension and is sufficient for MVP quality.

If higher quality rasterization is needed later, a `FreeTypeFontRasterizer` can be added behind the same interface.

## Serialization
The project uses `kotlinx.serialization.json` via manual `JsonObject`/`buildJsonObject` API (not
`@Serializable` compiler plugin). `KRenderJson.Pretty` is the shared `Json` instance. Helper functions
in `JsonDocumentHelpers.kt` provide typed field access. The `engine:tools` module already has
`kotlinx-serialization-json:1.8.1` as a dependency. `.kfont.json` will use the same manual
`buildJsonObject`/`Json.parseToJsonElement` approach — no compiler plugin changes needed.

## Package structure
Existing package root: `com.pashkd.krender.engine.tools`.
New packages will follow this pattern:
- `com.pashkd.krender.engine.tools.common.canvas`
- `com.pashkd.krender.engine.tools.common.bitmapfont`
- `com.pashkd.krender.engine.tools.bitmapfonteditor`

## Files changed
No source files changed — audit only.
Report file created: `report/00-audit.md`.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- `AssetType.Font` already exists and covers `.fnt`, `.ttf`, `.otf` files in `AssetCategory.Scene2D`.
- `AssetCapabilities` currently sets `canOpenWith = false` for `AssetType.Font` — this must be changed to
  `true` to enable "Open in Bitmap Font Editor" from Asset Browser.
- `BitmapFontDiagnostic.severity` currently references `TextureAtlasEditorDiagnosticSeverity` — this coupling
  must be broken when extracting to common.
- `FontPreviewState.tintColor` references `TextureAtlasEditorColor` — needs a common color type or simple floats.
- `BitmapFontWriter` references `TextureAtlasEditorPathValidator` and `TextureAtlasEditorFileWriteResult` —
  the writer must become self-contained or use common abstractions.
- AWT-based rasterizer is the pragmatic MVP choice since `java.awt` is always available on desktop JVM.
- The `kotlinx.serialization` plugin must be applied to `engine:tools` if not already — need to verify.
