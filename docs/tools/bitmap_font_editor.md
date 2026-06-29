# Bitmap Font Editor

## Overview

The Bitmap Font Editor is a standalone KRender desktop tool for opening, inspecting, generating, previewing, and saving BitmapFont assets used by the Scene2D UI workflow.

![Bitmap Font Editor full interface](screenshots/bitmap_font_editor/bitmap_font_editor_main.png)

Runtime output:

```text
font.fnt   — BMFont text descriptor loaded by Scene2D at runtime
font.png   — standalone page texture referenced by the descriptor
```

Editor metadata:

```text
font.kfont.json   — generation settings (source font, charset, size, rasterizer options, output paths)
```

`.kfont.json` is used only by the editor workflow. It is not required at runtime.

## Scene2D Resource Workflow

The Bitmap Font Editor fits into the broader Scene2D UI authoring pipeline:

```text
Asset Browser
  → New → Bitmap Font → opens Bitmap Font Editor

Bitmap Font Editor
  → choose TTF/OTF source font
  → configure charset, size, padding, page size, rasterizer options
  → Preview / Generate
  → Save → writes .fnt + .png + .kfont.json

Skin Editor / Style Editor
  → reference generated .fnt via skin-relative path in skin JSON

UI Composer
  → consumes fonts through Scene2D Skin
```

The Texture Atlas Editor can import and pack existing `.fnt` bitmap fonts into atlases, but TTF/OTF generation belongs exclusively to the Bitmap Font Editor.

## Opening Existing Fonts

Launch with `krender.scene=bitmap-font-editor` and an optional `krender.font.path`:

```sh
./gradlew :desktop-lwjgl3-linux:run \
    -Pkrender.scene=bitmap-font-editor \
    -Pkrender.font.path=ui/fonts/roboto-24.fnt
```

The editor can open:

- **`.fnt` files** — text BMFont descriptors. Page PNG is resolved relative to the `.fnt` file.
- **`.kfont.json` files** — editor metadata. If the referenced `.fnt` output exists, it is loaded automatically.

Binary and XML BMFont formats are not supported in the current implementation. The editor detects the `BMF` binary header and reports it in Diagnostics.

Missing page textures, parse issues, and glyph bounds violations appear in the Diagnostics panel.

## Creating a New Bitmap Font Asset

From Asset Browser:

```text
Asset Browser → New → Bitmap Font
```

This creates a `.kfont.json` file in `ui/fonts/` with default generation settings and opens the Bitmap Font Editor. The default configuration uses:

- Size: 24 px
- Charset: English + Symbols + Ukrainian Cyrillic
- Page: 512 × 512
- Padding: 2, Spacing: 1

## Generation Workflow

The **Tools** panel provides all generation controls:

| Control | Description |
|---|---|
| Source Font (.ttf/.otf) | Project-relative path to the source vector font |
| Browse | Opens a file picker to choose a TTF/OTF file |
| Size (px) | Rasterized font size (8–128 px) |
| Charset | Preset character set or Custom |
| Custom Characters | Exact characters when Charset is Custom |
| Padding | Empty pixels around each glyph on the packed page |
| Spacing | Gap between packed glyph rectangles |
| Rasterizer | Rasterization backend (currently AWT) |
| Text AA | AWT text anti-aliasing mode (OFF, ON, GASP, LCD variants) |
| Fractional Metrics | Sub-pixel glyph metric accuracy |
| Render Quality | AWT rendering quality hint |
| Stroke Control | AWT stroke normalization hint |
| Page Width / Height | Page texture dimensions (128–4096) |

Actions at the bottom of the Tools panel:

- **Preview** — generates a transient preview page in a temporary location and shows it in the canvas without marking the document dirty.
- **Generate** — generates the working font document and preview page. Marks the document dirty.
- **Save Font** (toolbar) — writes `.fnt` + `.png` + `.kfont.json` to disk.
- **Reload** (toolbar) — reloads the current `.fnt` or `.kfont.json` from disk. Blocked while unsaved changes exist.

## Preview Canvas

The preview canvas supports two modes selected via the **Canvas Mode** combo:

- **Font Page** — shows the full page texture with glyph bounds overlay.
- **Sample Text** — renders sample text using the font's glyph layout with kerning.

Canvas controls:

| Control | Action |
|---|---|
| Zoom combo | Fit / 50% / 100% / 200% / Custom |
| Custom zoom slider | Precise zoom when Custom is selected |
| Fit | Fits content to canvas |
| Reset Camera | Resets pan and zoom to default |
| Focus Selected Char | Centers and zooms on the selected glyph |
| Scroll wheel | Zoom in/out |
| Right-mouse drag | Pan |
| Left-click on glyph | Select glyph (syncs with Glyph List) |
| Checkerboard | Toggle transparent-area background |
| Grid | Toggle pixel grid overlay |
| Grid Size | Grid spacing (1–64 px) |
| Highlight Selected Glyph | Toggle glyph bounds/selection overlay |
| Sample Text | Editable sample text input |

The status bar shows current zoom, cursor position, hovered glyph, and selected glyph.

After generation, the preview page is written to disk immediately so the backend can load the texture. There may be a brief delay before the texture handle becomes available.

![Bitmap Font Editor preview canvas controls](screenshots/bitmap_font_editor/bitmap_font_editor_font_preview_changing_options.gif)

## Glyph List and Inspector

The **Glyph List** panel shows all glyphs in the loaded font:

- Filter by glyph ID or character.
- Click to select; the canvas highlights and can focus the selected glyph.
- Canvas click-selection scrolls the glyph list to the selected entry.

The **Inspector** panel shows:

- Font metadata: face, size, bold/italic, line height, base, scale, page count, glyph count, kerning count.
- Selected glyph metrics: id/codepoint, character label, position (x, y), size (width × height), offset (xoffset, yoffset), advance (xadvance), page, channel.
- Kerning pairs involving the selected glyph (if any).
- Diagnostic count.

The screenshot below highlights the working relationship between the **Glyph List**, **Tools**, and **Inspector** panels, including the rasterizer option block used during generation.

![Bitmap Font Editor glyph list, tools, and inspector panels](screenshots/bitmap_font_editor/glyph_tools_inspector_panels.png)

## Charset Presets

| Preset | Characters |
|---|---|
| English | A–Z, a–z, 0–9 |
| Symbols | Space, punctuation, common ASCII symbols |
| Ukrainian Cyrillic | А–Я, а–я including Ґ ґ Є є І і Ї ї |
| English + Symbols + Ukrainian Cyrillic | All of the above (default) |
| Custom | User-specified character string |

The custom charset should include all characters expected in runtime UI text. Missing glyphs are reported in sample text preview and diagnostics.

## BMFont File Basics

Generated files:

```text
font.kfont.json  — editor/generation metadata (not required at runtime)
font.fnt         — BMFont text descriptor
font.png         — page texture
```

Key `.fnt` fields:

| Field | Meaning |
|---|---|
| `lineHeight` | Vertical distance between text lines |
| `base` | Baseline offset from top of line cell |
| `scaleW` / `scaleH` | Page texture dimensions |
| `page id` / `file` | Page index and PNG filename (relative to `.fnt`) |
| `char id` | Unicode codepoint |
| `x` / `y` / `width` / `height` | Glyph rectangle on the page |
| `xoffset` / `yoffset` | Drawing offset from cursor/baseline |
| `xadvance` | Horizontal cursor advance after this glyph |
| `kerning first` / `second` / `amount` | Pair-wise spacing adjustment |

## Rasterization

The current implementation uses **AWT** (`java.awt.Font` + `Graphics2D`) behind the `FontRasterizer` interface. AWT is adequate for MVP and desktop tooling.

Future planned rasterizers:

- **LibGDX FreeType** — intended as the default production-quality rasterizer for higher fidelity output.
- **STB TrueType** or other backends may be added behind the same interface.

The rasterizer is selectable in the Tools panel but currently only AWT is available.

## Save / Export

**Save Font** writes three files:

| File | Content |
|---|---|
| `.fnt` | BMFont text descriptor with page file path relative to the `.fnt` file |
| `.png` | Standalone page texture |
| `.kfont.json` | Updated editor metadata with output paths and generation config |

The source font path in `.kfont.json` is stored as project-relative when possible.

Dirty state is tracked. Reload and Exit are blocked while unsaved changes exist.

## Troubleshooting

| Problem | Fix |
|---|---|
| Source font not found | Verify the path in the Tools panel is correct and project-relative. Use Browse to pick the file. |
| Font cannot display requested characters | The source TTF/OTF does not contain glyphs for the selected charset. Check Diagnostics for missing codepoints. |
| Glyphs do not fit on page | Increase Page Width/Height or reduce font size / charset scope. Overflow is reported in Diagnostics. |
| Page texture not found | The `.fnt` page file path is resolved relative to the `.fnt` file. Ensure the PNG is in the same directory. |
| Preview says loading texture | The backend has not finished loading the page PNG yet. Wait a frame or two after generation. |
| Missing glyphs in sample text | The sample text contains characters not in the generated charset. Add them to Custom Characters or choose a broader preset. |
| Generated output looks blurry | Try changing Text AA mode (e.g. GASP or LCD_HRGB), Render Quality, or Stroke Control in the Tools panel. |
| Ukrainian letters missing | Ensure the charset preset includes Ukrainian Cyrillic. Verify the source font contains Ґ ґ Є є І і Ї ї. |
| Backspace/Delete not working in text fields | This is a known ImGui input limitation in the current KRender editor runtime. |

## Current Limitations

- No binary or XML BMFont support.
- Single page only — overflow is reported, multiple pages are not created.
- No manual glyph metrics editing.
- No kerning generation (existing kernings in opened `.fnt` files are preserved).
- No SDF/MSDF font support.
- No complex text shaping or RTL support.
- No fallback font chain.
- AWT rasterizer only — FreeType is planned as a future production rasterizer.

## Related Tools

```text
Bitmap Font Editor
  TTF/OTF → .fnt + .png (generation and preview)

Texture Atlas Editor
  imports existing .fnt → packs into atlas

Skin Editor
  references .fnt from skin JSON

UI Composer
  consumes fonts through Scene2D Skin
```

- **Asset Browser** — discovers `.fnt` and `.kfont.json` files, opens them in Bitmap Font Editor.
- **Texture Atlas Editor** — can import and pack existing bitmap fonts but does not generate from TTF/OTF.
- **Skin Editor** — references generated `.fnt` files using skin-relative paths.
- **UI Composer** — consumes fonts transitively through Scene2D Skin loading.
