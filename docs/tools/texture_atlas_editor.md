# Texture Atlas Editor

## Overview

The Texture Atlas Editor is an atlas-centric tool for building and managing libGDX texture atlases. It opens an `.atlas` file as the root working document and provides a unified workflow for inspecting, importing, editing, packing, and saving atlas resources.

The tool supports three resource types — **Image**, **NinePatch**, and **BitmapFont** — and produces standard libGDX `.atlas` descriptors with accompanying PNG page textures.

## Opening an Atlas

Launch with `krender.scene=texture-atlas-editor` and an optional `krender.atlas.path`:

```sh
./gradlew :desktop-lwjgl3-linux:run \
    -Pkrender.scene=texture-atlas-editor \
    -Pkrender.atlas.path=ui/skins/default/uiskin.atlas
```

When `krender.atlas.path` is omitted the tool starts empty. Enter or browse an atlas path in the toolbar to load it.

On load the editor scans the atlas directory for texture files, parses the `.atlas` descriptor, discovers `.fnt` bitmap font files in the same directory, and builds a resource list from atlas regions and discovered fonts.

## Resource Types

### Image Resources

Plain texture regions extracted from atlas pages or added manually from existing image files. Each resource tracks its source texture path, source rectangle, and atlas region identity.

### NinePatch Resources

Atlas regions that carry `split` and `pad` metadata. NinePatch resources can be created from Image resources by choosing **Convert to NinePatch** in the Inspector panel. Once created, split and padding guides can be edited interactively in the NinePatch Editor canvas mode.

### BitmapFont Resources

Font resources created by importing existing `.fnt` descriptors. The import action copies the `.fnt` file and its page textures next to the atlas, rewrites page file references in the copied descriptor, and adds the font to the resource list.

BitmapFont generation from TTF/OTF is **not** part of the Texture Atlas Editor. Use the dedicated **Bitmap Font Editor** for that workflow.

## Preview Canvas

The Preview Canvas panel provides four modes:

- **Atlas File** — shows the selected atlas page texture with region bounds, hover/selection highlight, and region labels.
- **NinePatch Editor** — shows the selected NinePatch resource with interactive stretch/padding guide handles and optional stretch test preview.
- **Font Preview** — shows the selected font page texture with glyph bounds overlay, or renders sample text using the font's glyph layout.
- **Atlas Preview** — shows the current packed atlas plan as a composited texture with region bounds.

All modes support pan (right-mouse-button drag), zoom (scroll wheel), fit, checkerboard background, pixel grid overlay, and region/glyph focus.

The NinePatch Editor places **Preview Type** and the stretch-test **Preset** selector on the same row when the canvas is in stretch-test mode. Font Preview glyph bounds use the same baseline outline color as atlas region bounds in Atlas File mode.

## NinePatch Editor Mode

When a NinePatch resource is selected, the canvas switches to NinePatch Editor mode:

- **Source preview** — shows the NinePatch content area with draggable guide handles for stretch X, stretch Y, padding X, and padding Y segments.
- **Stretch Test preview** — renders the 9-slice stretched result at a configurable target size. Presets include Actual, Button (160×48), Panel (320×180), and Custom.
- Validation issues (out-of-bounds guides, zero-length segments) are shown in the Inspector panel and block Apply until resolved.
- **Apply Draft** writes the edited split/pad values to the working resource. **Reset** reverts to the source values.
- Changes are applied to the in-memory draft only. The atlas file is not written until an explicit **Save Texture Atlas** action.

## BitmapFont Preview

When a Font resource is selected:

- The Font Preview canvas mode shows the font page texture with glyph rectangles.
- The Inspector shows font metadata (face, size, line height, glyph count, kerning count) and a scrollable glyph list with filter.
- Sample text can be typed and previewed with the font's glyph layout.
- **Pack font in atlas** can be toggled per font resource. When enabled, the font page image is included in the next pack operation.

## Packing Workflow

1. Configure packing settings in the **Tools** panel: max page width/height, padding, NinePatch inclusion, and rotation (accepted but not currently applied).
2. Click **Pack Texture Atlas**. This runs an in-memory packing dry run and produces a packing plan with page layouts, region placements, and diagnostics.
3. Switch to **Atlas Preview** mode or toggle **Preview Packed Atlas** to inspect the result.
4. Review diagnostics for skipped regions, oversized textures, or missing sources.

**Pack Texture Atlas does not write any files.** It only computes the layout.

## Save Workflow

1. After packing, click **Save Texture Atlas** in the Tools panel.
2. The save action writes:
    - The `.atlas` descriptor file.
    - One PNG page file per packed page.
    - If any font resource has **Pack font in atlas** enabled, a separate `*_packed.fnt` descriptor with atlas-relative glyph coordinates. The original imported `.fnt` file is never modified.
3. After a successful save, the editor reloads the saved atlas.

**Save Texture Atlas** respects the **Overwrite Existing Atlas** toggle. When disabled, existing files block the save with an error message.

## File Write Safety

| Action | Writes files? | Details |
|---|---|---|
| Preview / selection / mode switching | No | Read-only canvas rendering |
| Pack Texture Atlas | No | In-memory layout only |
| Save Texture Atlas | Yes | Explicit — writes `.atlas` + PNGs + optional `*_packed.fnt` |
| Add Font | Yes | Explicit — copies `.fnt` + page PNGs next to atlas |
| Import Image | Yes | Explicit — copies texture into asset root |
| Export Resource | Yes | Explicit — writes PNG / `.9.png` to export path |

The tool does not generate `.krmeta` metadata files.

## Current Limitations

- **Shelf-based packing** — the current packing algorithm is a simple shelf/row packer sorted by descending height. MaxRects and other advanced strategies are planned but not yet implemented.
- **Rotation not applied** — the `Allow Rotation` setting is accepted but not used during packing.
- **Single-page BitmapFont only** — fonts with more than one page texture are skipped during packing with a diagnostic warning.
- **No TTF/OTF font generation** — bitmap font creation from vector fonts is out of scope for this tool and belongs to the dedicated Bitmap Font Editor.
- **No undo/redo** — resource edits are one-way except for NinePatch draft reset.
- **Sample text is single-line** — the font sample text preview does not handle newline characters.

## Related Tools

The Texture Atlas Editor is part of the broader **Scene2D UI authoring workflow** in KRender:

```text
Bitmap Font Editor
  TTF/OTF → BitmapFont .fnt + PNG pages

Texture Atlas Editor
  images / NinePatch / BitmapFont → packed .atlas + PNG pages

Skin Editor
  colors, drawables, fonts, Scene2D style properties → skin .json

UI Composer
  visual UI composition using skin / atlas / font assets → .krui document
```

- **Asset Browser** — discovers atlas files and opens them in the Texture Atlas Editor.
- **Skin Editor** — consumes packed atlases and font descriptors produced by this tool.
- **UI Composer** — uses skin/atlas/font assets authored through the Skin Editor and Texture Atlas Editor.
