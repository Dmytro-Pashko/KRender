# Texture Atlas Editor — Agent Context

> Read `AGENTS.md` first. This file covers only the Texture Atlas Editor tool.

## Purpose

Atlas-centric editor for building and inspecting libGDX texture atlases. The opened `.atlas` file
is the root working document. Resources (Image, NinePatch, BitmapFont) are packed into atlas pages
and saved explicitly.

## Current Implementation

- Scene: `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/TextureAtlasEditorScene.kt`
- Core tool files:
  - `TextureAtlasEditorModel.kt` — state model, enums, preview/canvas state
  - `TextureAtlasEditorOperations.kt` — UI-facing facade delegating to feature operations
  - `TextureAtlasEditorStateSelectors.kt` — state query helpers, constants
  - `TextureAtlasEditorProjectLoader.kt` — directory scan, document parsing, project snapshot
  - `TextureAtlasEditorSelectionCoordinator.kt` — selection sync across panels
  - `TextureAtlasResourceBuilder.kt` — resource rebuild from project snapshot
  - `TextureAtlasResourceModel.kt` — sealed resource hierarchy (Image, NinePatch, Font)
  - `TextureAtlasResourceOperations.kt` — add/delete/rename/export resource operations
  - `TextureAtlasPackingModel.kt` — packing input/output/plan/settings
  - `TextureAtlasPackingOperations.kt` — pack preparation, resource-to-input conversion
  - `TextureAtlasPackingPlanner.kt` — shelf packing algorithm
  - `TextureAtlasSaveService.kt` — save service interface + NoOp
  - `TextureAtlasParser.kt` — libGDX `.atlas` text format parser
  - `TextureAtlasNinePatchOperations.kt` — NinePatch draft editing operations
  - `TextureAtlasNinePatchStretchModel.kt` — stretch test preview computation
  - `NinePatchEditorModel.kt` — draft model, split/pad conversion, validation
  - `NinePatchParser.kt` — `.9.png` border guide parser
  - `BitmapFontModel.kt` — font document model
  - `BitmapFontParser.kt` — text `.fnt` parser, sample text layout
  - `BitmapFontWriter.kt` — text `.fnt` writer with page overrides
  - `TextureAtlasBitmapFontOperations.kt` — font import, export, pack-in-atlas
  - `TextureAtlasEditorImportExportOperations.kt` — import texture, save atlas, browse dialogs
  - `TextureAtlasEditorImportService.kt` — file copy for texture import
  - `TextureAtlasRegionExportService.kt` — region PNG export with `.9.png` border reconstruction
  - `TextureAtlasEditorPathValidator.kt` — path resolution and root containment
  - `TexturePreviewViewportMath.kt` — shared viewport layout, hit testing, zoom
  - `TextureMetadataService.kt` — image dimension reader wrapper
- UI panels under `engine/tools/.../textureatlaseditor/ui/`:
  - `TextureAtlasEditorToolbarPanel`
  - `TextureAtlasEditorPreviewCanvasPanel`
  - `TextureAtlasEditorInspectorPanel`
  - `TextureAtlasEditorResourcesPanel`
  - `TextureAtlasEditorToolsPanel`
  - `TextureAtlasEditorDiagnosticsPanel`
  - `TextureAtlasEditorPreviewOverlays`
  - `TextureAtlasEditorPanelHelpers`
- GDX preview adapters under `engine/tools/.../textureatlaseditor/gdx/`:
  - `GdxTextureAtlasEditorPreview` — GDX texture loading, packed page composition
  - `GdxTextureAtlasSaveService` — GDX Pixmap-based atlas page writing
  - `GdxNinePatchPixelReader` — GDX Pixmap pixel buffer reader for `.9.png`

## GDX Boundary Rules

- GDX-specific code must stay under `textureatlaseditor/gdx/`.
- Parsers, models, operations, and services must not import `com.badlogic.gdx`.
- GDX files are allowlisted in `BackendBoundaryTest.KNOWN_TOOL_GDX_IMPORTS`.
- UI panels may use `TexturePreviewHandle` only as an opaque backend-neutral preview handle.

## File Write Safety Rules

- **Preview, selection, canvas mode switching, and Pack Texture Atlas must not write files.**
- Save Texture Atlas is the explicit write action for `.atlas` + PNG pages.
- Add Font is an explicit import action that copies `.fnt` + page PNGs next to the atlas.
- Import Image and Export Resource are explicit file operations.
- Packed font descriptors are written as separate `*_packed.fnt` files. The original imported `.fnt` is never overwritten.
- The tool does not generate `.krmeta` files.
- The Resources panel import UI is intentionally a single shared import source field; image and font buttons reuse that path but still route through their existing dedicated operations.

## Scope Boundaries

- Do not add TTF/OTF BitmapFont generation to Texture Atlas Editor. Font generation belongs to a future Bitmap Font Editor.
- Do not add color/style resource editing. Colors and styles belong to the Skin Editor.
- Do not add automatic `.krmeta` generation.

## Safe Change Rules

- Read this file before modifying Texture Atlas Editor.
- Do not add GDX imports outside `textureatlaseditor/gdx/`.
- Do not put file I/O in ImGui panels.
- Keep viewport math in `TexturePreviewViewportMath.kt`, not duplicated in panels.
- Use `beginImGuiPanel()` from the shared editor helpers, not private `beginPanel()` copies.
- Use `engine.logger`, never `println`.
- If save workflow or file write behavior changes, update `docs/tools.md`, `docs/tools/texture_atlas_editor.md`, and this file in the same change.
