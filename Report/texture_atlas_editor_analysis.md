# Texture Atlas Editor Analysis

## Executive Summary

The Texture Atlas Editor is a substantial, well-structured atlas-centric tool (~9,400 LOC across 35+ Kotlin files) added to the `engine:tools` module. It follows KRender's established Scene/System/Panel architecture and cleanly separates operations by feature domain. The core model and parser layers are correctly backend-neutral, with GDX dependencies confined to `gdx/` subpackage files.

Key strengths: clear operation decomposition, explicit save semantics, good diagnostics infrastructure, and a full NinePatch editing workflow with stretch preview.

Key risks: the **Preview Canvas panel is 1,371 lines** and combines rendering, interaction, and UI logic in a single class; the **shelf-based packing algorithm** is functional but weak compared to MaxRects; the **packed font descriptor rewrite overwrites the copied `.fnt`** in-place with atlas-relative glyph coordinates, which is destructive if the user re-imports or toggles `packInAtlas` off; and the `GdxTextureAtlasEditorPreview` recomposes packed pages via `Pixmap` on every key change without caching individual source textures.

The tool is merge-ready with a small set of P0 correctness fixes and documented P1 follow-ups.

---

## Scope Reviewed

- **Primary review target**: all 35 `.kt` files under `engine/tools/src/main/kotlin/.../textureatlaseditor/` including `gdx/` and `ui/` subpackages.
- **Integration points**: `ToolsModule.kt`, `SceneConfigPresets`, desktop launcher `EditorToolLauncher`, `AssetBrowserScene` atlas creation/details, `AwtFileDialogService`.
- **Asset files**: only sampled to validate expected tool behavior (e.g. atlas format, `.fnt` format).
- **Out of scope**: repacked skins, `.krmeta` files, `.json` skin definitions, large PNG diffs.

---

## High-Level Architecture

```
TextureAtlasEditorScene (Scene)
  ├── TextureAtlasEditorState (mutable state bag)
  ├── TextureAtlasEditorOperations (facade → delegates)
  │     ├── TextureAtlasResourceOperations
  │     ├── TextureAtlasPackingOperations
  │     ├── TextureAtlasBitmapFontOperations
  │     ├── TextureAtlasNinePatchOperations
  │     ├── TextureAtlasEditorImportExportOperations
  │     └── TextureAtlasEditorSelectionCoordinator
  ├── TextureAtlasEditorProjectLoader (scan + parse)
  ├── GdxTextureAtlasEditorPreview (GDX texture lifecycle)
  ├── GdxTextureAtlasSaveService (GDX Pixmap write)
  ├── GdxNinePatchPixelReader (GDX Pixmap read)
  └── UI Panels (7 panels via UiSystem)
        ├── ToolbarPanel
        ├── PreviewCanvasPanel (1,371 LOC)
        ├── InspectorPanel
        ├── ResourcesPanel
        ├── ToolsPanel
        ├── DiagnosticsPanel
        └── LogsPanel (shared engine panel)
```

**Backend abstraction compliance**: The architecture correctly places GDX code exclusively under `gdx/` (3 files: `GdxTextureAtlasEditorPreview`, `GdxTextureAtlasSaveService`, `GdxNinePatchPixelReader`). The save service implements a neutral `TextureAtlasSaveService` interface. The `NinePatchPixelReader` interface abstracts pixel access. `TextureAtlasParser`, `BitmapFontParser`, `BitmapFontWriter`, and `NinePatchParser` are fully backend-neutral.

**Known violation**: The scene directly instantiates `AwtFileDialogService()` (line 58 of `TextureAtlasEditorScene.kt`). This is a `java.awt` dependency, not a GDX dependency, but it still couples the scene to a desktop platform. The `FileDialogService` interface exists and has a `NoOpFileDialogService` default, so this should be injected from the launcher.

---

## Current Tool Workflow

1. **Open**: User provides an `.atlas` path via launcher param or toolbar input. `TextureAtlasEditorProjectLoader.load()` resolves it, scans the directory tree, parses atlas/font/NinePatch documents, and builds the `TextureAtlasEditorProject` snapshot.
2. **Browse**: Resources panel shows Image, NinePatch, and Font resources derived from atlas regions and discovered `.fnt` files.
3. **Import**: Image textures can be added from existing asset paths or imported (copied) into the asset root. Bitmap fonts are imported by copying `.fnt` + page PNGs next to the atlas.
4. **Edit**: NinePatch resources get a draft editor with split/pad guides, interactive handles, and a stretch-test preview.
5. **Pack**: An in-memory packing dry-run produces a `TextureAtlasPackingPlan` with page layouts.
6. **Preview**: The packed atlas is composited via GDX Pixmap and rendered as a texture preview.
7. **Save**: Explicit button writes atlas `.atlas` descriptor + page PNGs. For fonts with `packInAtlas = true`, the copied `.fnt` descriptor is rewritten with offset glyph coordinates.

---

## Major Strengths

- **Clean operation decomposition**: 6 operation classes keep domain logic out of UI panels. The facade pattern in `TextureAtlasEditorOperations` reduces panel coupling.
- **Explicit save semantics**: Pack is in-memory only; Save is an explicit user action writing files. Dirty state tracking is present.
- **Robust diagnostics**: Unified `TextureAtlasEditorDiagnostic` model with severity/category/source/message used across atlas parsing, font parsing, NinePatch validation, and packing.
- **Backend-neutral parsers**: `TextureAtlasParser`, `BitmapFontParser`, `NinePatchParser` have no GDX imports and are unit-testable.
- **NinePatch workflow completeness**: Draft editing with guide handles, validation, stretch-test preview with target-size presets, and `.9.png` export with border reconstruction.
- **Path safety**: `TextureAtlasEditorPathValidator` prevents writes outside the asset root using canonical path comparison.

---

## Critical Findings

### P0-1: Packed font `.fnt` rewrite is destructive and non-reversible

**Files**: `TextureAtlasBitmapFontOperations.kt:261-298`

**Problem**: `savePackedFontDescriptors()` overwrites the copied `.fnt` file at `resource.documentPath` with atlas-relative glyph coordinates (x += packedRegion.x, y += packedRegion.y) and changes `scaleW`/`scaleH` to the packed page dimensions. If the user subsequently toggles `packInAtlas = false` or re-packs with different settings, the original glyph coordinates are lost because the source `.fnt` was overwritten.

**Impact**: Data loss. The user must manually restore the original `.fnt` or reimport it.

**Suggested fix**: Before overwriting, save the original document coordinates as a backup (e.g. keep an "original" document snapshot in state), or write a separate `*_packed.fnt` file instead of overwriting the imported copy. Alternatively, always recompute offsets from the original parsed coordinates rather than from the previously-written file.

### P0-2: `AwtFileDialogService` instantiated directly in Scene

**Files**: `TextureAtlasEditorScene.kt:58`

**Problem**: `AwtFileDialogService()` is hard-coded. On Android or headless environments, this would fail. The existing `NoOpFileDialogService` default in the operations constructor shows the intent was injection.

**Impact**: Breaks on non-desktop targets; violates backend abstraction contract.

**Suggested fix**: Inject `FileDialogService` from the launcher or `EngineContext`, consistent with other tools.

### P0-3: `TextureAtlasEditorPreviewCanvasPanel` is 1,371 lines and combines 4 concerns

**Files**: `ui/TextureAtlasEditorPreviewCanvasPanel.kt`

**Problem**: This single class handles:
1. All 4 canvas modes (Atlas, NinePatch, FontPreview, FinalPackedAtlas)
2. Mouse interaction (region selection, NinePatch handle dragging, packed region clicking)
3. Zoom/pan/surface-mode UI controls
4. Viewport layout computation (duplicated from `TexturePreviewViewportMath.kt`)

Private helper functions like `atlasRegionScreenRect`, `hitTestAtlasRegion`, `screenToTexturePixelX/Y`, and `computeTexturePreviewViewportLayout` are **duplicated** as private top-level functions at the bottom of this file (lines 1251-1371), despite identical implementations existing in `TexturePreviewViewportMath.kt`.

**Impact**: Hard to maintain, test, or reuse. Any bug fix to viewport math must be applied in two places.

**Suggested fix**: Extract mode-specific canvas renderers into separate classes. Remove the duplicated viewport math functions and use the shared `TexturePreviewViewportMath` implementations.

---

## P1 Findings

### P1-1: Packing algorithm is a naive shelf packer

**Files**: `TextureAtlasPackingPlanner.kt:28-112`

**Problem**: The packing algorithm sorts inputs by descending height, then uses a simple shelf/row approach. It does not try to fit shorter textures into gaps left by taller ones on the same shelf. No bin-packing heuristics (MaxRects, Guillotine, Skyline) are used. `allowRotation` is accepted as a setting but explicitly logged as "not applied" (line 25).

**Impact**: Poor atlas occupancy, especially with varied-size regions. Users will get larger/more atlas pages than necessary.

**Suggested fix**: Implement a MaxRects or Skyline bin packing strategy behind a `PackingStrategy` interface. Add occupancy metrics to the packing result (used area / total page area).

### P1-2: Packed page recomposition loads every source Pixmap on every key change

**Files**: `gdx/GdxTextureAtlasEditorPreview.kt:214-254`

**Problem**: `composePackedPageTexture()` loads every source region's `Pixmap` from disk, draws them into an output Pixmap, creates a `Texture`, and disposes the sources. The key changes if any region ID, position, or size changes. There is no per-source-file Pixmap cache.

**Impact**: Slow preview for large atlases or many regions. Each Pack operation forces a full recomposition.

**Suggested fix**: Cache loaded Pixmaps per source path with a simple LRU and only reload when the source file modification time changes.

### P1-3: `rebuildResources()` in Scene mixes font-document filtering with atlas resource building

**Files**: `TextureAtlasEditorScene.kt:160-232`

**Problem**: The `rebuildResources()` method in the Scene class directly builds atlas resources and font resources, applies carry-over logic for manually-added resources, and reassigns the resource state. This is business logic that belongs in an operation or service class, not in the Scene.

**Impact**: Cannot unit-test resource rebuild logic without a Scene instance.

**Suggested fix**: Extract to a `TextureAtlasResourceBuilder` or move into `TextureAtlasResourceOperations`.

### P1-4: Multi-page BitmapFont support is explicitly blocked

**Files**: `TextureAtlasPackingOperations.kt:285-293`, `TextureAtlasBitmapFontOperations.kt:241-244`

**Problem**: Both packing and descriptor rewrite explicitly skip fonts with `pages.size != 1`. The diagnostic says "currently supports single-page bitmap fonts only." This is correct for an MVP but means multi-page fonts silently fail to pack.

**Impact**: Users with multi-page bitmap fonts will see warnings but no packed output for those fonts.

**Suggested fix**: At minimum, document this limitation prominently. Long-term, support multi-page fonts by packing each page separately and rewriting per-page glyph coordinates.

### P1-5: No undo/redo support

**Problem**: All state mutations are immediate. There is no command history. NinePatch draft editing has a "Reset" button, but resource delete, rename, and NinePatch apply are one-way.

**Impact**: User workflow friction; accidental changes require a full reload.

**Suggested fix**: Add a simple command stack for reversible operations.

### P1-6: `GdxTextureAtlasSaveService.writePage()` does not catch per-region Pixmap load failures

**Files**: `gdx/GdxTextureAtlasSaveService.kt:88-109`

**Problem**: In `writePage()`, `Pixmap(Gdx.files.absolute(region.sourcePath))` is called without error handling for each region. If any region source file is missing or corrupt, the entire save operation fails with an unhandled `GdxRuntimeException`. The preview composition (`composePackedPageTexture`) handles this correctly with `runCatching`, but the save path does not.

**Impact**: A single missing region source file aborts the entire atlas save.

**Suggested fix**: Add `runCatching` per-region with a diagnostic, consistent with the preview compositor.

---

## P2 Findings

### P2-1: `beginPanel()` is duplicated across 3 UI files

**Files**: `TextureAtlasEditorPreviewCanvasPanel.kt:1234-1249`, `TextureAtlasEditorResourcesPanel.kt:317-332`, `TextureAtlasEditorInspectorPanel.kt:328-343`

**Problem**: Three identical `beginPanel()` private functions. `TextureAtlasEditorToolbarPanel` and `TextureAtlasEditorDiagnosticsPanel` use `beginImGuiPanel` from the shared editor helpers, which is the correct approach.

**Impact**: Code duplication; inconsistent panel behavior if one copy is modified.

**Suggested fix**: Use the shared `beginImGuiPanel()` helper from `com.pashkd.krender.engine.ui.editor`.

### P2-2: `TextureAtlasEditorDiagnosticsPanel.visibleDiagnostics()` called twice per frame

**Files**: `ui/TextureAtlasEditorDiagnosticsPanel.kt:57,63`

**Problem**: `visibleDiagnostics()` is called once for the iteration and once for the empty check, filtering the full diagnostics list twice.

**Impact**: Minor performance waste on large diagnostic lists.

**Suggested fix**: Cache the result in a local variable.

### P2-3: No occupancy metrics in packing result

**Files**: `TextureAtlasPackingPlanner.kt`, `TextureAtlasPackingModel.kt`

**Problem**: The packing plan reports `inputCount`, `packedRegionCount`, and `skippedCount`, but not the occupancy percentage (used pixels / total page pixels). This makes it hard for users to judge packing quality.

**Suggested fix**: Add `occupancyPercent` per page and total to `TextureAtlasPackingPlan`.

### P2-4: Sample text layout does not handle newlines

**Files**: `BitmapFontParser.kt:245-291` (`layoutSampleText`)

**Problem**: `layoutSampleText()` iterates characters and advances `cursorX` but never resets the cursor for newline characters. Multi-line sample text renders as a single line.

**Suggested fix**: Handle `\n` by resetting `cursorX` to 0 and advancing cursorY by `common.lineHeight`.

### P2-5: `ColorAtlasResource` is never created by any workflow

**Files**: `TextureAtlasResourceModel.kt:46-54`

**Problem**: `ColorAtlasResource` exists in the sealed interface but no code path creates one. The resources panel filters it out (`filterNot { resource.type == Color }`). The packing operations emit a warning for it.

**Impact**: Dead code that may confuse maintainers.

**Suggested fix**: Either remove it or document it as a planned feature.

### P2-6: `GdxTextureAtlasSaveService.buildDescriptor()` always writes `filter: Nearest, Nearest` and `repeat: none`

**Files**: `gdx/GdxTextureAtlasSaveService.kt:126-127`

**Problem**: The save service hard-codes filter and repeat settings. The original atlas may have had `Linear, Linear` or `repeat: xy`. These settings are not preserved or configurable.

**Suggested fix**: Carry filter/repeat settings from the source atlas document or add them to packing settings.

---

## P3 / Future Improvements

### P3-1: Strategy abstraction for packing algorithms
Add a `PackingStrategy` interface with `ShelfPacker`, `MaxRectsPacker`, `SkylinePacker` implementations. Allow the user to choose in the Tools panel.

### P3-2: Extrusion / edge padding for bleeding prevention
`TextureAtlasPackingSettings.extrude` field exists but is never used. Implement edge pixel extrusion during the save phase.

### P3-3: Trim whitespace support
`TextureAtlasPackingSettings.trimWhitespace` field exists but is unused. Implement transparent border trimming with `orig`/`offset` metadata in the descriptor.

### P3-4: Drag-and-drop file import
Allow dragging files from the OS file manager into the resources panel.

### P3-5: BitmapFont model/parser/writer extraction to shared package
Move `BitmapFontModel.kt`, `BitmapFontParser.kt`, `BitmapFontWriter.kt`, `BitmapFontDiagnostic` to a shared `core/.../engine/font/` package for reuse by the future BitmapFont Editor, UI Composer, and Skin Editor.

### P3-6: NinePatch model/parser extraction to shared package
Move `NinePatchParser.kt`, `NinePatchEditorModel.kt`, `NinePatchSegment`, `NinePatchDocument` to a shared `core/.../engine/ninepatch/` package.

---

## File Write Safety Review

| Write Operation | Trigger | Classification | Notes |
|---|---|---|---|
| Save Packed Atlas (`.atlas` + page PNGs) | Explicit "Save Texture Atlas" button | **Safe explicit write** | Requires prior Pack; validates path inside asset root; respects overwrite flag |
| Import Texture (file copy) | Explicit "Import Image Into Assets" button | **Safe explicit write** | Validates inside asset root; respects overwrite flag |
| Import BitmapFont (`.fnt` + page PNGs copy) | Explicit "Add Font" button | **Safe explicit write** | Chooses unique target names; copies files; writes rewritten `.fnt` |
| Export Resource PNG | Explicit "Export Resource" button | **Safe explicit write** | Validates inside asset root |
| Export BitmapFont `.fnt` | Explicit "Export" button | **Safe explicit write** | Validates `.fnt` extension and asset root |
| Packed font `.fnt` rewrite | Auto after Save if `packInAtlas=true` | **Potentially unsafe write** | Overwrites the imported copy in-place. See P0-1. |
| UI layout save | Explicit "Save Layout" button | **Safe explicit write** | Writes JSON to assets path |
| Pack Texture Atlas | Explicit "Pack" button | **No write** | In-memory only. Correct. |
| Selection/preview/mode switching | Any UI interaction | **No write** | Correct. Verified. |
| `.krmeta` generation | None | **Not present** | Correct. The tool does not generate `.krmeta` files. |

**Path validation**: All write operations use `TextureAtlasEditorPathValidator.resolveAssetPath()` which compares canonical paths to ensure the target stays inside `assetRoot`. This is correctly implemented using `Path.normalize()` + `startsWith()`.

**Original `.fnt` safety**: The import workflow copies the source `.fnt` and its pages to a new location next to the atlas. The original source `.fnt` is never modified. However, the **copied** `.fnt` is overwritten by the packed font rewrite (P0-1 above).

---

## Packing Pipeline Review

### Algorithm
- **Type**: Shelf/row packing, sorted by descending height.
- **Rotation**: Accepted as a setting flag but explicitly not applied. A diagnostic is emitted.
- **Multi-page**: Supported. When a shelf overflows the page height, a new page is created.
- **Deduplication**: Not implemented. If the same source image appears in multiple resources, it is packed multiple times.

### Correctness
- NinePatch regions include `split` and `pad` metadata correctly carried through to `TextureAtlasPackingRegion`.
- Font page images are correctly treated as full-page regions for packing.
- The "too large for page" check (line 64-70) correctly accounts for padding on both sides.
- Region placement coordinates are correct: `cursorX` advances by `width + padding`, `cursorY` by `shelfHeight + padding`.

### Output quality concerns
- No occupancy metric. Users cannot judge how efficient the packing is.
- Shelf packing wastes vertical space when regions on the same row have significantly different heights.
- No power-of-two enforcement option for atlas page dimensions (the page size is the tightly-fitted used area, not the maxPage size).

### Descriptor output
- The save service writes `size:` as the actual used size, not `maxPageWidth`/`maxPageHeight`. This is correct for libGDX.
- `orig:` is always set to the region's packed size, which loses the original atlas `orig` values. This is acceptable for a fresh pack but may break workflows that depend on original sprite sheet frames with padding.
- `offset:` is always `0, 0`. Same concern.

---

## NinePatch Workflow Review

### Draft model
- `NinePatchDraft` stores `stretchX`, `stretchY`, `paddingX?`, `paddingY?` as `NinePatchSegment(start, length)`.
- Built from resource split/pad lists or from parsed `.9.png` document guides.
- Falls back to full-content stretch if no other data is available.

### Guide editing
- Interactive drag handles are implemented in `TextureAtlasEditorPreviewOverlays.buildNinePatchDraftOverlay()`.
- Hit testing uses axis-aligned rectangles around guide endpoints.
- Handle drag correctly clamps start/end within content bounds (lines 1145-1183 of PreviewCanvasPanel).
- Guide handles scale with zoom level, clamped between 8-16px screen size.

### Validation
- `validateNinePatchDraft()` checks segment bounds, positive lengths, and warns about missing padding.
- Apply is blocked if any validation error exists.

### Stretch preview
- `buildNinePatchStretchPreview()` correctly computes 9 slice regions from the 3x3 grid.
- Target size presets (Actual, Button 160x48, Panel 320x180, Custom) are provided.
- Warnings are generated when target size is smaller than fixed edges.
- Padding rect is mapped from source to destination coordinates.

### Correctness risks
- **Split/pad conversion correctness**: `toSplitList()` produces `[left, right, top, bottom]` as `[stretchX.start, contentWidth - (start+length), stretchY.start, contentHeight - (start+length)]`. This matches libGDX's split format. ✓
- **`.9.png` export**: `TextureAtlasRegionExportService.exportNinePatchResource()` correctly adds 2px border and draws guide pixels. ✓
- **Edge case**: If `split = [0, 0, 0, 0]`, `splitToSegments()` returns `null` because `stretchWidth = contentWidth - 0 - 0 = contentWidth > 0`... wait, actually `[0,0,0,0]` gives `stretchWidth = contentWidth` which is > 0, so it returns valid segments. But `NinePatchDraft` is then built with `start=0, length=contentWidth` — full stretch. When `createNinePatchFromSelectedResource()` sets `split = [0,0,0,0]`, the draft will have `stretchWidth = width - 0 - 0 = width`, which is correct.

---

## BitmapFont Workflow Review

### Import flow
1. User browses/enters `.fnt` path.
2. `importFontResourceFromPath()` validates the source, parses it, checks all pages exist.
3. `chooseImportTargets()` finds unique file names in the atlas directory (increments suffix to avoid collisions).
4. Page PNGs are copied with `Files.copy()`. The `.fnt` is rewritten via `BitmapFontWriter` with page filename overrides.
5. The rewritten `.fnt` is reparsed and added to `fontDocuments`.
6. A `FontAtlasResource` is created and selected.

### Font preview
- Page texture preview works through the normal `GdxTextureAtlasEditorPreview` texture loading path.
- Glyph bounds overlay draws rectangles for each glyph on the selected page.
- Sample text layout (`layoutSampleText()`) places glyphs using `xOffset`/`yOffset`/`xAdvance`. Single-line only (see P2-4).

### Pack font in atlas
- When `packInAtlas = true`, the font page image is included in packing inputs.
- After save, `savePackedFontDescriptors()` rewrites the `.fnt` with:
  - `scaleW`/`scaleH` set to packed page dimensions.
  - Glyph `x` += `packedRegion.x`, `y` += `packedRegion.y`.
  - Page file set to the packed atlas page PNG name.
- **Critical issue (P0-1)**: This overwrites the copied `.fnt` in-place, making the original glyph coordinates unrecoverable.

### Glyph coordinate correctness
- The offset calculation `glyph.x + packedRegion.x` assumes glyph coordinates are relative to the page texture origin. Since the packed region places the entire page image at `(packedRegion.x, packedRegion.y)`, adding this offset to each glyph is correct.
- After rewrite, `glyph.page = 0` for all glyphs. This is correct for single-page packing.

### Multi-page limitation
- Multi-page fonts are explicitly skipped with a diagnostic warning. This is documented behavior.

---

## UI / Preview Canvas Review

### Panel sizes (LOC)
| Panel | Lines |
|---|---|
| `TextureAtlasEditorPreviewCanvasPanel` | 1,371 |
| `TextureAtlasEditorPreviewOverlays` | 691 |
| `TextureAtlasEditorInspectorPanel` | 344 |
| `TextureAtlasEditorResourcesPanel` | 333 |
| `TextureAtlasEditorToolsPanel` | 170 |
| `TextureAtlasEditorDiagnosticsPanel` | 91 |
| `TextureAtlasEditorToolbarPanel` | 69 |

The Preview Canvas panel is by far the largest. It should be split into at least:
- `AtlasCanvasRenderer` — atlas region preview and interaction
- `NinePatchCanvasRenderer` — NinePatch source/stretch preview and handle interaction
- `FontCanvasRenderer` — font page/sample text preview
- `PackedAtlasCanvasRenderer` — packed atlas wireframe/texture preview
- `CanvasControlsBar` — zoom/surface/mode UI widgets

### Code duplication in PreviewCanvasPanel
- `beginPanel()`, `atlasRegionScreenRect()`, `hitTestAtlasRegion()`, `screenToTexturePixelX/Y()`, `formatZoomMode()`, and `computeTexturePreviewViewportLayout()` are all **re-implemented as private functions** at the bottom of the file despite existing as internal functions in `TexturePreviewViewportMath.kt`.

### Buffer management
- Several `ByteArray` buffers (`fontSampleBuf`, `customCanvasWidthBuf`, etc.) are manually synced. This is standard for ImGui text input but the sync logic is fragile and spread across multiple private methods.

---

## Reuse and Component Extraction Opportunities

| Component | Current Location | Future Consumers | Extraction Target |
|---|---|---|---|
| `BitmapFontModel` / `BitmapFontParser` / `BitmapFontWriter` | `textureatlaseditor/` | BitmapFont Editor, Skin Editor, UI Composer | `core/.../engine/font/` |
| `SampleTextLayout` / `layoutSampleText()` | `BitmapFontParser.kt` | BitmapFont Editor, UI Composer | `core/.../engine/font/` |
| `NinePatchParser` / `NinePatchDocument` / `NinePatchSegment` | `textureatlaseditor/` | Style Editor, UI Composer | `core/.../engine/ninepatch/` |
| `NinePatchEditorModel` / `NinePatchDraft` / validation | `textureatlaseditor/` | UI Composer (NinePatch editing) | `core/.../engine/ninepatch/` |
| `TextureAtlasPackingPlanner` | `textureatlaseditor/` | Asset Browser (quick repack), CI tooling | `core/.../engine/atlas/` |
| `TextureAtlasParser` | `textureatlaseditor/` | Skin Editor, Asset Browser | `core/.../engine/atlas/` |
| `TextureAtlasEditorPathValidator` | `textureatlaseditor/` | All tools with file writes | `core/.../engine/assets/` |
| `TextureMetadataService` | `textureatlaseditor/` | Asset Browser, other tools | Already wraps `TextureMetadataReader` from core |

---

## Performance and Memory Notes

### Preview texture lifecycle
- `GdxTextureAtlasEditorPreview` loads one texture at a time (`ensureTextureLoaded`). Old textures are disposed before loading new ones. This is correct and memory-safe.
- **Packed page composition** (`composePackedPageTexture`) creates a temporary output `Pixmap`, loads each region source as a separate `Pixmap`, draws into the output, and disposes each source. For large atlases with many regions, this means many sequential file reads and Pixmap allocations per frame that triggers a recomposition.

### Potential issues
- `TextureMetadataService.read()` uses `TextureMetadataReader.read()` which likely reads image headers via `javax.imageio`. This is called during project load for every texture file and during packing input construction. No caching.
- `NinePatchParser.parse()` reads entire `.9.png` pixel buffers via `GdxNinePatchPixelReader`. This is done once during project load, which is acceptable.
- `BitmapFontParser.parse()` reads entire `.fnt` files line by line. Also once during project load.
- `buildNinePatchStretchPreview()` is called during draw if the NinePatch stretch test mode is active. It's a pure computation (no I/O) and creates a few list objects per frame — acceptable.
- `layoutSampleText()` is called during font preview canvas draw. For long sample text, this creates a placement object per character per frame. Should be memoized.

### Logging volume
- The codebase uses `engine.logger` consistently (never `println`). However, many operations log at `info` level for routine actions (e.g. every preview resolution, every selection change). This could generate high log volume during interactive use. Consider `debug` for high-frequency events.

---

## Manual Test Plan

### Basic workflow
1. **Open existing skin atlas**: Launch with `atlasPath = assets/ui/skins/default/uiskin.atlas`. Verify pages/regions load, preview renders, and resources panel shows Image and NinePatch resources.
2. **Import image**: Add an external PNG via "Import Image Into Assets". Verify the file is copied into the asset directory and appears as a resource.
3. **Add existing image**: Add an image already in the asset directory via "Add Existing Image". Verify it appears without file copy.

### NinePatch workflow
4. **Create NinePatch**: Select an Image resource, click "Create NinePatch". Verify it becomes a NinePatch resource with default split [0,0,0,0].
5. **Edit NinePatch guides**: Drag stretch handles in the NinePatch editor canvas mode. Verify the draft values update in the inspector.
6. **Apply and validate**: Set invalid split values (start + length > contentWidth). Verify validation errors appear and Apply is blocked.
7. **Stretch preview at multiple sizes**: Switch to Stretch Test mode. Test with Actual, Button (160x48), Panel (320x180), and Custom sizes. Verify correct 9-slice rendering.
8. **Export NinePatch**: Export a NinePatch resource. Verify the `.9.png` file has correct black border guides.

### Pack and save
9. **Pack texture atlas**: Click "Pack Texture Atlas". Verify the packing plan shows correct region count, page count, and no errors.
10. **Preview packed atlas**: Toggle "Preview Packed Atlas" or switch to "Atlas Preview" mode. Verify the composited page texture renders correctly.
11. **Save texture atlas**: Click "Save Texture Atlas". Verify `.atlas` descriptor and page PNG(s) are written. Open the saved atlas in a text editor and verify region coordinates match the preview.
12. **Reload after save**: After save, verify the editor reloads correctly with the saved atlas.

### BitmapFont workflow
13. **Import BitmapFont**: Browse to an external `.fnt` file and click "Add Font". Verify the `.fnt` and page PNG(s) are copied next to the atlas with unique names.
14. **Verify copied `.fnt` page references**: Open the copied `.fnt` in a text editor. Verify `page id=0 file="..."` points to the copied page PNG, not the original.
15. **Preview font**: Switch to Font Preview mode. Verify the page texture renders with glyph bounds overlay.
16. **Toggle Pack font in atlas = false**: Verify the font page is not included in packing inputs.
17. **Toggle Pack font in atlas = true**: Pack the atlas. Verify the font page appears as a packed region.
18. **Save atlas with packed font**: Save. Verify the copied `.fnt` is rewritten with offset glyph coordinates pointing to the atlas page PNG.
19. **Reload after font pack save**: Reload the editor and verify the font still displays correctly.
20. **Missing font page failure**: Delete a font page PNG and reload. Verify a diagnostic warning appears.
21. **Duplicate font import naming**: Import the same font twice. Verify the second import gets a unique name suffix (e.g. `font_2.fnt`).

### Edge cases
22. **Unsaved changes guard**: Make changes (add resource, modify NinePatch), then try to open another atlas or reload. Verify the operation is blocked with a status message.
23. **Repeated save/repack**: Pack and save twice without changes. Verify the atlas file is overwritten correctly with the same content.
24. **Large atlas**: Test with an atlas containing 50+ regions. Verify packing and preview performance is acceptable.

---

## Recommended Next Steps

1. **P0 fixes before merge**:
   - Fix destructive packed font `.fnt` rewrite (P0-1): either preserve original coordinates or write to a separate file.
   - Inject `FileDialogService` instead of hard-coding `AwtFileDialogService` (P0-2).

2. **P1 follow-ups (target: next sprint)**:
   - Add per-region error handling in `GdxTextureAtlasSaveService.writePage()` (P1-6).
   - Extract `rebuildResources()` from Scene into a testable service (P1-3).
   - Add packing occupancy metrics (P2-3).

3. **P1 architecture (target: next 2 sprints)**:
   - Split `TextureAtlasEditorPreviewCanvasPanel` into mode-specific renderers (P0-3).
   - Remove duplicated viewport math functions (P0-3).
   - Remove duplicated `beginPanel()` functions (P2-1).

4. **P2/P3 improvements (backlog)**:
   - Implement MaxRects packing strategy (P1-1).
   - Add multi-line sample text layout (P2-4).
   - Extract BitmapFont and NinePatch models to shared packages (P3-5, P3-6).
   - Add undo/redo command stack (P1-5).

---

## Appendix: Files Inspected

### Core model/operations (engine/tools/.../textureatlaseditor/)
| File | Lines | Purpose |
|---|---|---|
| `TextureAtlasEditorScene.kt` | 358 | Scene lifecycle, project loading, UI system setup |
| `TextureAtlasEditorModel.kt` | 211 | State model, enums, preview/canvas state |
| `TextureAtlasEditorOperations.kt` | 574 | Facade delegating to feature operations |
| `TextureAtlasEditorStateSelectors.kt` | 178 | State query helpers, constants |
| `TextureAtlasEditorProjectLoader.kt` | 416 | Directory scan, document parsing, project snapshot |
| `TextureAtlasEditorImportExportModel.kt` | 19 | Import/export state and result model |
| `TextureAtlasEditorImportExportOperations.kt` | 187 | Import texture, save atlas, browse dialogs |
| `TextureAtlasEditorImportService.kt` | 85 | File copy for texture import |
| `TextureAtlasEditorSelection.kt` | 14 | `TextureAssetId`, `AtlasRegionId` value types |
| `TextureAtlasEditorSelectionCoordinator.kt` | 67 | Selection sync across panels |
| `TextureAtlasEditorPathValidator.kt` | 61 | Path resolution and root containment validation |
| `TextureAtlasEditorFileTypes.kt` | 15 | Supported extensions |
| `TextureAtlasEditorLayout.kt` | 33 | Panel IDs and default UI layout |
| `TextureAtlasEditorDiagnostics.kt` | 26 | Diagnostic severity/category/model |
| `TextureAtlasResourceModel.kt` | 79 | Sealed resource hierarchy (Image, NinePatch, Font, Color) |
| `TextureAtlasResourceOperations.kt` | 340 | Add/delete/rename/export resource operations |
| `TextureAtlasPackingModel.kt` | 91 | Packing input/output/plan/settings model |
| `TextureAtlasPackingOperations.kt` | 339 | Pack preparation, resource-to-input conversion |
| `TextureAtlasPackingPlanner.kt` | 161 | Shelf packing algorithm |
| `TextureAtlasSaveService.kt` | 28 | Save service interface + NoOp |
| `TextureAtlasParser.kt` | 271 | libGDX `.atlas` text format parser |
| `TextureMetadataService.kt` | 18 | Image dimension reader wrapper |
| `TexturePreviewViewportMath.kt` | 150 | Viewport layout computation, hit testing |
| `TextureAtlasNinePatchOperations.kt` | 129 | NinePatch draft editing operations |
| `TextureAtlasNinePatchStretchModel.kt` | 264 | Stretch test preview computation |
| `NinePatchEditorModel.kt` | 173 | Draft model, split/pad conversion, validation |
| `NinePatchParser.kt` | 319 | `.9.png` border guide parser |
| `BitmapFontModel.kt` | 100 | Font document model, glyph/kerning/page data |
| `BitmapFontParser.kt` | 292 | Text `.fnt` parser, sample text layout |
| `BitmapFontWriter.kt` | 109 | Text `.fnt` writer with page overrides |
| `TextureAtlasBitmapFontOperations.kt` | 400 | Font import, export, pack-in-atlas rewrite |
| `TextureAtlasRegionExportService.kt` | 286 | Region PNG export with `.9.png` border reconstruction |

### GDX backend (engine/tools/.../textureatlaseditor/gdx/)
| File | Lines | Purpose |
|---|---|---|
| `GdxTextureAtlasEditorPreview.kt` | 283 | GDX texture loading, packed page composition |
| `GdxTextureAtlasSaveService.kt` | 191 | GDX Pixmap-based atlas page writing |
| `GdxNinePatchPixelReader.kt` | 37 | GDX Pixmap pixel buffer reader for `.9.png` |

### UI panels (engine/tools/.../textureatlaseditor/ui/)
| File | Lines | Purpose |
|---|---|---|
| `TextureAtlasEditorPreviewCanvasPanel.kt` | 1,371 | Canvas preview, interaction, controls |
| `TextureAtlasEditorPreviewOverlays.kt` | 691 | Overlay drawing (regions, guides, glyphs, packed) |
| `TextureAtlasEditorInspectorPanel.kt` | 344 | Resource inspector, NinePatch editor, font inspector |
| `TextureAtlasEditorResourcesPanel.kt` | 333 | Resource list, page selection, import controls |
| `TextureAtlasEditorToolsPanel.kt` | 170 | Packing settings, save, export |
| `TextureAtlasEditorDiagnosticsPanel.kt` | 91 | Diagnostics list with severity filter |
| `TextureAtlasEditorToolbarPanel.kt` | 69 | Toolbar: reload, layout, exit |
| `TextureAtlasEditorPanelHelpers.kt` | (shared helpers: `textLine`, `wrappedTextLine`, `writeBuffer`, `readBuffer`, `packImColor`, `formatBytes`) |

### Integration
| File | Purpose |
|---|---|
| `ToolsModule.kt` | Scene factory — routes `texture-atlas-editor` to `TextureAtlasEditorScene` |
| `SceneConfigPresets.kt` | Defines `TextureAtlasEditor` scene config preset |
| `AssetBrowserScene.kt` | Atlas creation dialog, "Open in Texture Atlas Editor" action |
| `Lwjgl3EditorToolLauncher.kt` | Desktop launcher passes `atlasPath` system property |
