# Bitmap Font Editor — Agent Context

> Read `AGENTS.md` first. This file covers only the Bitmap Font Editor tool.

## Purpose

Standalone desktop tool that owns TTF/OTF → BitmapFont `.fnt` + `.png` generation and preview.
Editor metadata is stored in `.kfont.json` (not required at runtime).

## Ownership Boundaries

| Tool | Responsibility |
|---|---|
| **Bitmap Font Editor** | Open `.fnt`, inspect glyphs, generate `.fnt` + `.png` from TTF/OTF, manage `.kfont.json`, preview font page and sample text |
| **Texture Atlas Editor** | Atlas-centric workflows, imports/exports existing bitmap fonts, does **not** generate fonts from TTF/OTF |
| **Skin Editor** | References generated `.fnt` from skin/style data, does not own font rasterization |
| **UI Composer** | Consumes fonts through Scene2D Skin, does not own font generation |

## Current Implementation

- Scene: `engine/tools/.../bitmapfonteditor/BitmapFontEditorScene.kt`
- Core tool files:
  - `BitmapFontEditorState.kt` — mutable editor state
  - `BitmapFontEditorController.kt` — UI-facing controller with camera, selection, layout, workflow dispatch
  - `BitmapFontEditorLayout.kt` — panel IDs and default ImGui layout config
  - `BitmapFontEditorMetadata.kt` — `.kfont.json` codec (manual `buildJsonObject` / `parseToJsonElement`)
- Panels under `bitmapfonteditor/panels/`:
  - `BitmapFontToolbarPanel` — save/reload/layout/exit + status
  - `FontPageCanvasPanel` — preview canvas with mode switch, overlays, interaction
  - `GlyphListPanel` — filterable glyph list with scroll-to-selected
  - `GlyphInspectorPanel` — font metadata and selected glyph metrics
  - `FontGenerationPanel` — generation config controls (source font, charset, rasterizer options)
  - `FontDiagnosticsPanel` — diagnostic messages
- Workflows under `bitmapfonteditor/workflow/`:
  - `OpenBitmapFontWorkflow` — opens `.fnt` or `.kfont.json`
  - `GenerateBitmapFontWorkflow` — rasterize + pack + compose page + build document; supports preview-only mode
  - `SaveBitmapFontWorkflow` — writes `.fnt` + `.png` + `.kfont.json`

## Common Reusable Packages

These packages are shared with Texture Atlas Editor and available for future tools:

| Package | Content |
|---|---|
| `common/bitmapfont/model` | `BitmapFontDocument`, `BitmapFontGlyph`, `BitmapFontKerning`, diagnostics |
| `common/bitmapfont/io` | `BitmapFontParser` (text BMFont + binary detection), `BitmapFontWriter`, `layoutSampleText()` with kerning |
| `common/bitmapfont/charset` | `UnicodeCodePoint`, `CharsetPreset`, `CharsetBuilder` |
| `common/bitmapfont/preview` | `FontPreviewOverlays`, `GlyphSelectionState`, `FontInspectorData` |
| `common/bitmapfont/generator` | `FontRasterizer`, `AwtFontRasterizer`, `GlyphPacker`, `SkylineGlyphPacker`, `BitmapFontGenerator`, `FontPageImageWriter` |
| `common/canvas` | `CanvasRect`, `CanvasPreviewState`, `CanvasViewportLayout`, `CanvasOverlays` |

## Desktop Launcher Integration

- `EditorToolLauncher.launchBitmapFontEditor(fontPath)` on all platforms
- Scene name: `bitmap-font-editor`
- System property: `krender.font.path`
- `SceneConfigPresets.BitmapFontEditor`
- Registered in `ToolsModule.createScene()`

## Rules for Future Agents

- Do not duplicate BMFont parser/writer/model in individual tools. Reuse `common/bitmapfont`.
- Do not add TTF/OTF generation to Texture Atlas Editor. Font generation belongs to Bitmap Font Editor.
- Keep UI panels thin; put orchestration in controller/workflows.
- Keep rasterizers behind the `FontRasterizer` interface.
- Do not leak tool/ImGui code into runtime or mobile modules.
- Do not introduce mobile dependencies on Bitmap Font Editor.
- Use `EngineContext` services where existing tooling does.
- Log user-level operations and failures through `engine.logger` with a `TAG` companion constant.
- Use `beginImGuiPanel()` from shared editor helpers.
- Use `textLine()` / `wrappedTextLine()` / `tooltipOnHover()` from panel helpers.

## Logging Expectations

Future changes should log:

- Open `.fnt` / `.kfont.json` — path, resolved path, glyph/page counts
- Parse/read failures — path, error message
- Generate/preview start — source font, size, page dimensions, preview-only flag
- Generate/preview success — glyph count, page dimensions
- Generate/preview failure — diagnostic count
- Preview PNG write — path, success/failure
- Save start/success/failure — written paths
- Source font browse/select — selected path
- Canvas mode changes — sample preview enabled/disabled
- Camera fit/reset/focus selected glyph — zoom, pan, glyph ID
- Blocked reload/exit due to dirty state

Avoid:
- Per-frame logs (hover, mouse move, drag ticks, update cycles)
- Logging every glyph hover change

## Scope Boundaries

- Do not add binary/XML BMFont parsing.
- Do not add multi-page support to the generator.
- Do not add manual glyph metrics editing.
- Do not add SDF/MSDF generation.
- Do not add kerning generation unless the rasterizer provides kerning data.
- LibGDX FreeType rasterizer is the intended future direction but is not yet implemented — do not claim it exists.

## Safe Change Rules

- Read this file before modifying Bitmap Font Editor.
- Do not put file I/O in ImGui panels. Use workflows.
- Keep viewport/canvas math in `common/canvas`, not duplicated in panels.
- If save workflow or file write behavior changes, update `docs/tools/bitmap_font_editor.md` and this file in the same change.
