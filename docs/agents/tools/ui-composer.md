# UI Composer — Agent Context

> Read `AGENTS.md` and `docs/agents/ui-system.md` first. This file covers only the UI Composer
> tool.

## Purpose

Editor for `.krui` UiScene documents (Scene2D-style runtime UI). Opens from the Asset Browser
for `UI`-category assets. It decodes and validates `.krui`, renders a **Scene2D preview**, lets
the user inspect/edit selected-node scalar fields, perform hierarchy-driven structure editing,
save the document, select nodes (via hierarchy or selection-only canvas hit-testing), and pick
Skin-backed styles/backgrounds and Asset-Registry-backed Image textures.

## Current Implementation

- Scene: `engine/tools/.../uicomposer/UiComposerScene.kt`.
- Tool internals live in `engine/tools/.../uicomposer/` for scene/layout/panels/operations/model helpers.
- Shared `.krui` model + validation: `core/.../engine/ui/scene/`.
- Tool preview adapter: `engine/tools/.../uicomposer/gdx/GdxUiScenePreview.kt`,
  `GdxUiComposerSkinMetadataReader.kt`.
- Launched as a separate JVM window via `Lwjgl3EditorToolLauncher.launchUiComposer(path)`
  (`krender.scene=ui-composer`, `krender.ui.scene.path=<path>`).

## Main Files

| File | Responsibility |
|---|---|
| `engine/tools/.../uicomposer/UiComposerScene.kt` | Composition; document load/reload, preview rebuild, save, undo/redo, canvas interaction system. |
| `engine/tools/.../uicomposer/UiComposerModel.kt` | Editor document + preview state used by the tool and preview adapter. |
| `engine/tools/.../uicomposer/UiComposerOperations.kt` | Edit/save/undo/redo operations. |
| `engine/tools/.../uicomposer/UiComposerDocumentEditing.kt`, `UiComposerHistory.kt` | Structure editing + history helpers. |
| `engine/tools/.../uicomposer/UiComposerPanels.kt` | Toolbar, preview canvas, hierarchy, structure, inspector, scene bindings, diagnostics panels. |
| `engine/tools/.../uicomposer/UiComposerStyleValidation.kt`, `UiComposerBindingHelpers.kt` | Style/binding validation helpers. |
| `engine/ui/scene/UiSceneDocument.kt`, `UiSceneSerializer.kt`, `UiSceneValidation.kt` | Shared `.krui` model + validators (also used by runtime UI). |
| `engine/tools/.../uicomposer/gdx/GdxUiScenePreview.kt` | Scene2D preview Stage/Skin/overlay for the tool. |

## Main Classes

| Class | Responsibility |
|---|---|
| `UiComposerScene` | Composition + lifecycle, overlay preview render, deferred request handling. |
| `UiComposerState` | Document, selection, validation buckets, preview/camera/zoom state. |
| `UiComposerOperations` | Save, undo, redo, scalar/structure edits. |
| `GdxUiScenePreview` | Backend Scene2D preview, hit-testing, debug overlays/guides. |
| `UiComposerPreviewUpdateSystem` | Syncs canvas viewport + overlays + guides each frame. |
| `UiComposerCanvasInteractionSystem` | Selection-only hit-test, hover, Ctrl+drag pan, wheel zoom. |

## UI Panels

`UiComposerToolbarPanel`, `UiComposerPreviewCanvasPanel`, `UiComposerHierarchyPanel`,
`UiComposerStructurePanel`, `UiComposerInspectorPanel`, `UiComposerSceneBindingsPanel`,
`UiComposerDiagnosticsPanel`, `LogsPanel`.

## Engine Services Used

`engine.sceneFiles` (read/write `.krui`), `engine.assets` (texture previews), `engine.ui`
(panels + previews), `engine.input` (canvas interaction + undo/redo shortcuts),
`engine.logger`/`engine.logs`. Uses `overlayRender()` (the dedicated `Scene` hook) to draw the
Scene2D preview after the main renderer and before ImGui.

## Data Flow

1. `show()` builds state, a `UiComposerDocumentLoader` (over `engine.sceneFiles::readText`), the
   backend `GdxUiScenePreview`, a Skin metadata reader, and a **local** `LocalAssetRegistryService`
   used only for the Image texture picker.
2. `reloadDocumentAndPreview()` decodes `.krui`, refreshes Skin metadata + validation buckets,
   and rebuilds the Scene2D preview.
3. Per frame, `update()` handles deferred save/reload/rebuild/undo-redo requests; the canvas
   systems sync the viewport/overlays and perform selection-only interaction.
4. `overlayRender()` calls `preview.render()`.

## Lifecycle

`show()` → load + preview build + 3 systems (`UiComposerCanvasInteractionSystem`, `UiSystem`,
`UiComposerPreviewUpdateSystem`). `resize()` resizes the preview viewport. `dispose()` releases
the preview's Stage/Skin/Texture resources and the Skin metadata reader. Uses
`SceneConfigPresets.UiComposer`.

## Supported Asset Types

`.krui` UiScene documents (`AssetType.UiScene`, `AssetCategory.UI`). Skin JSON referenced by the
document (`ui/skins/...`). Texture assets (for Image node texture picking).

## Current Features

- Decode + validate `.krui`; live Scene2D preview with debug overlays + visual guides.
- Selected-node scalar field editing and hierarchy-driven structure editing.
- Undo/redo (Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z) and save.
- Node selection via hierarchy and selection-only canvas hit-testing; hover highlight.
- Ctrl+drag camera pan and mouse-wheel zoom on the preview canvas.
- Skin-backed style/background pickers; Asset Registry-backed Image texture picking.
- Style / texture / binding validation diagnostics.

## Missing / Incomplete Features

(Per `UiComposerScene` KDoc, intentionally deferred.) No canvas drag/drop, no actor resizing on
canvas, no multi-select, no canvas-based structure editing, no Skin editing, no texture
import/copy, no atlas region picking, no texture thumbnails, no Asset Browser drag/drop, no
asset-id references in `.krui`, no snapping, no transform gizmos, no JSON source editing, no
generic visual layout solving, and no full Scene2D actor serialization. Reload-dirty handling is
a simple toolbar state.

## Known Problems

- Uses a **local** `LocalAssetRegistryService` for the texture picker because `EngineContext`
  exposes the runtime `AssetService`, not a shared `AssetRegistryService`. The code itself flags
  this as something to replace with a shared registry once Asset Browser and Composer align.
- Stale "placeholder" comments elsewhere misrepresent this tool's current capabilities.
- The preview is the one place a tool draws Scene2D directly via a backend hook
  (`overlayRender`) — keep that plumbing contained to editor/backend code.

## Extension Points

- Add a panel via `addPanel(...)` in `createUiSystem`.
- Add `.krui` node types / fields: extend the shared `engine.ui.scene` model + serializer +
  validators (this also affects runtime UI — coordinate the change).
- Add validators: implement a `UiSceneValidationRule` (see `engine/ui/scene/validation/`).

## Safe Change Rules

- Changes to the `.krui` model (`engine.ui.scene`) are **shared with runtime UI** — keep them
  backward-compatible and run `UiSceneSerializerTest` + validation tests.
- Keep Scene2D preview rendering inside backend code (`GdxUiScenePreview`); the scene only calls
  it through `overlayRender`/`resize`/`dispose`.
- Do not add the deferred features (drag/drop, gizmos, Skin editing, asset-id references) without
  an explicit decision — they are intentionally out of scope right now.

## Recommended Improvements

- Replace the local texture registry with a shared engine `AssetRegistryService` on
  `EngineContext`.
- Update stale comments carefully when they misdescribe current UI Composer scope or routing.

## Related Code Patterns

- `overlayRender()` is defined on `Scene` (`api/Scene.kt`) specifically for this preview path.
- Shared `.krui` model + validators are exercised by `UiSceneSerializerTest`,
  `UiSceneFocusedValidatorsTest`, and `UiSceneAssetIntegrationTest`.
- The state+operations+panels recipe matches the other editor tools.
