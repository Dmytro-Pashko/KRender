# Skin Editor — Agent Context

> Read `AGENTS.md` and `docs/agents/ui-system.md` first. This file covers only the Skin Editor
> tool.

## Purpose

Standalone Scene2D Skin JSON / `.uiskin` inspection and editing tool. It focuses on style and
resource diagnostics, atlas/font/color preview, Scene2D canvas preview, in-memory editing, and a
save workflow that writes draft style/resource edits back to the loaded skin file.

## Current Implementation

- Scene: `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/SkinEditorScene.kt`
- Core tool files:
  - `SkinEditorModel.kt`
  - `SkinEditorOperations.kt`
  - `SkinEditService.kt`
  - `SkinStyleSaveService.kt`
  - `SkinEditorEditing.kt`
  - `SkinProjectLoader.kt`
  - validator and resource-indexing files in the same package
- UI panels under `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/ui/`:
  - `SkinEditorToolbarPanel`
  - `SkinEditorStyleTreePanel`
  - `SkinEditorStyleInspectorPanel`
  - `SkinEditorResourceBrowserPanel`
  - `SkinEditorResourcePreviewPanel`
  - `SkinEditorAtlasPreviewViewport`
  - `SkinEditorAtlasPreviewControls`
  - `SkinEditorResourcePreviewOverlays`
  - `SkinEditorPreviewCanvasPanel`
  - `SkinEditorPreviewControlsPanel`
  - `SkinEditorProblemsPanel`
- GDX preview adapters under `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/`:
  - `GdxSkinEditorPreview`
  - `GdxSkinResourcePreview`
  - `GdxBitmapFontPreviewLoader`
  - `GdxSkinStyleEditApplier`
  - `SkinReloadService`

## Main Classes / Responsibilities

| Class | Responsibility |
|---|---|
| `SkinEditorScene` | Scene composition, reload lifecycle, systems, preview render hooks. |
| `SkinEditorState` | Tool state, selection, preview, resource preview, edit session, pending events. |
| `SkinEditorOperations` | UI-facing command facade. |
| `SkinEditSession` | In-memory editable projection of loaded skin. |
| `SkinEditService` | In-memory mutations only; no file I/O. |
| `SkinStyleSaveService` | Backend-neutral JSON save workflow for draft edits. |
| `GdxSkinEditorPreview` | Scene2D Stage build/render/input forwarding. |
| `GdxSkinResourcePreview` | Texture/atlas/font/color preview handles. |

## Data Flow

1. Skin path is resolved and loaded by `SkinReloadService`.
2. Loader code builds `SkinLoadResult` with project, style index, resource index, and problems.
3. `SkinEditSessionFactory` creates the editable in-memory projection.
4. UI panels mutate tool state only through `SkinEditorOperations` and `SkinEditService`.
5. Scene2D preview rebuilds from the loaded skin plus the active edit session.
6. Resource preview resolves atlas/texture/font/color preview data.
7. `Save Changes` calls `SkinStyleSaveService`, writes JSON, creates `.bak`, and requests reload.

## Save Workflow Rules

- `Save Changes` writes only draft style/resource edits.
- `Save Panel Layout` is unrelated to skin persistence.
- The writer must consume `SkinEditSession.toEditedSnapshot()`.
- The writer must not depend on ImGui, GDX preview adapters, texture handles, UI buffers, camera state, or viewport state.
- The writer must not save atlas, texture, or font binary files.
- A `.bak` backup is created before each save.
- Reload after save should leave the tool in a clean state.

## GDX Boundary Rules

- Neutral model state must not contain `Texture`, `BitmapFont`, `Skin`, `Stage`, `Actor`, `FrameBuffer`, `Pixmap`, or `Vector2`.
- GDX-specific preview/render/input code stays under `skin/gdx`.
- UI panels may use `TexturePreviewHandle` only as an opaque backend-neutral preview handle.
- Mouse and keyboard interaction should be translated through neutral state/events where practical.

## Current Features

- Load and reload Scene2D skin descriptors.
- Build style tree and resource browser views from indexed data.
- Show diagnostics/problems with style/resource linking.
- Render Scene2D widget preview with preview layouts and controls.
- Support interactive widget preview in the canvas.
- Preview atlas, texture, font, and color resources.
- Support interactive atlas viewport controls and overlays.
- Provide in-memory style/color editing with pending changes.
- Save draft edits back to the loaded skin file with `.bak` backup creation.

## Missing / Deferred

- No atlas packing.
- No texture region editing.
- No bitmap font glyph editing.
- No Skin Composer integration.
- No advanced diff UI.
- No multi-file dependency save.
- Keyboard text input forwarding may remain limited.
- Screenshots and image-based docs are still pending.

## Safe Change Rules

- Read this file before modifying Skin Editor.
- Do not put persistence logic in ImGui panels.
- Do not put ImGui or GDX preview state into `SkinEditSession`.
- Keep `SkinEditService` file-I/O free.
- Keep `SkinStyleSaveService` UI- and GDX-free.
- Preserve `.bak` behavior unless the save policy is intentionally changed.
- If save format or workflow changes, update `docs/tools.md` and this file in the same change.
