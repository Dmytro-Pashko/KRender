# Scene Editor — Agent Context

> Read `AGENTS.md` first. This file covers only the Scene Editor tool.

## Purpose

Compose and inspect engine scene documents (`.krscene`): build an entity hierarchy, place
models/lights/terrain, edit transforms and properties in an inspector, select objects in a
viewport with gizmos, and configure scene environment. Described in code as the "MVP foundation
scene for composing and inspecting engine scene data".

## Current Implementation

- Scene: `engine/tools/.../sceneeditor/SceneEditorScene.kt`.
- Tool internals: `engine/tools/.../sceneeditor/`.
- Shared bounds helpers reused by other tools remain in `core/.../engine/sceneeditor/SceneEditorBounds.kt`.
- Launched as a separate JVM window by `Lwjgl3EditorToolLauncher.launchSceneEditorWithScene(path)`
  (`krender.scene=scene-editor`, `krender.scene.path=<path>`). Can also open empty (in-memory).
- Note the two-world design: the scene's own `world` hosts editor systems/camera, while
  `SceneEditorDocument` wraps a **separate** `SceneWorld` holding the edited scene data.

## Main Files

| File | Responsibility |
|---|---|
| `engine/tools/.../sceneeditor/SceneEditorScene.kt` | Composition: editor camera, document, operations, systems, panels. |
| `engine/tools/.../sceneeditor/SceneEditorDocument.kt` | Holds the edited scene's `SceneWorld` + document model. |
| `engine/tools/.../sceneeditor/SceneEditorOperations.kt` | New/open/save, entity add/remove, edits. |
| `engine/tools/.../sceneeditor/SceneEditorState.kt` | Editor UI state (selection, camera, scene name/path). |
| `engine/tools/.../sceneeditor/SceneEditorSystems.kt` | Selection, bounding box, light gizmo/sync, terrain sync, environment, document render systems. |
| `engine/tools/.../sceneeditor/SceneEditorComponents.kt` | `EditorOnlyComponent` + editor components. |
| `engine/sceneeditor/SceneEditorBounds.kt` | `SceneEditorBoundsProvider` + bounds services. |
| `engine/tools/.../sceneeditor/SceneEditorPanels.kt` | Toolbar, hierarchy, inspector, viewport panels. |
| `engine/tools/.../sceneeditor/SceneAssetPanel.kt` | Asset panel + `SceneAssetBrowserModel`. |
| `engine/scene/SceneSerializer.kt`, `SceneDescriptors.kt`, `SceneAssetCollector.kt` | `.krscene` (de)serialization + asset collection. |

## Main Classes

| Class | Responsibility |
|---|---|
| `SceneEditorScene` | Composition + lifecycle. |
| `SceneEditorDocument` | Owns the edited scene world + data model. |
| `SceneEditorOperations` | All mutating operations (create/open/save/edit). |
| `SceneEditorSelectionSystem` | Ray/picking selection driven by input + bounds. |
| `SceneEditorBoundingBoxSystem` | Selection bounds rendering. |
| `SceneEditorLightSyncSystem` / `SceneEditorLightGizmoSystem` | Light data sync + gizmos. |
| `SceneEditorDocumentTerrainSyncSystem` | Terrain entity sync within the document. |
| `SceneEditorEnvironmentRenderSystem` | Emits `ApplyEnvironment` (skybox/ambient) from document settings. |
| `SceneEditorDocumentRenderSystem` | Emits draw commands for document entities. |

## UI Panels

`SceneEditorToolbarPanel`, `SceneHierarchyPanel`, `SceneAssetPanel`, `SceneInspectorPanel`,
`SceneViewportPanel`, `LogsPanel`.

## Engine Services Used

`engine.input` (camera + picking), `engine.assets` (model bounds via
`AssetServiceModelBoundsService`), `engine.sceneFiles` (read/write `.krscene`),
`engine.tasks` (asset panel scan), `engine.logger`/`engine.logs`, `engine.ui`.

## Data Flow

1. `show()` creates the editor camera (tagged `EditorOnlyComponent`, not part of scene data),
   builds the `SceneEditorDocument` (separate world), and `operations.createNewScene()`; if a
   path was provided, `operations.open(path)`.
2. The asset panel (`SceneAssetBrowserModel`) reuses `LocalAssetRegistryService` to list assets;
   the user adds them to the document via `SceneEditorOperations`.
3. Editor systems read input → update selection/gizmos; sync systems mirror document data into
   renderable state; render systems emit `RenderCommand`s (`ApplyEnvironment`, `DrawModel`, ...).
4. Save serializes the document via `SceneSerializer` through `engine.sceneFiles`.

## Lifecycle

`show()` builds layout, state, document, operations, asset browser model, creates the editor
camera, then adds systems in a deliberate order (guide, asset browser, UI, camera, selection,
bounding box, light gizmo, light sync, terrain sync, environment render, document render).
`hide()` releases cursor capture. Uses `SceneConfigPresets.EditorTool`.

## Supported Asset Types

`.krscene` scene documents (read/write). Within a scene: models, lights, terrain references,
skybox/environment settings. Asset panel surfaces all registry categories for placement.

## Current Features

- New / open / save `.krscene` documents.
- Entity hierarchy + inspector editing.
- Viewport selection with bounding boxes and light gizmos.
- Environment (skybox/ambient) configuration and preview.
- Terrain entity sync within a scene.
- Asset panel backed by the shared asset registry.

## Missing / Incomplete Features

- Described as an "MVP foundation" — transform gizmo manipulation, multi-select, copy/paste,
  and richer property editing are limited or absent (verify against current panels before
  relying on them).
- No undo/redo stack comparable to the Terrain Editor's history (verify in `SceneEditorOperations`).

> Needs verification: the exact set of inspector-editable properties and whether interactive
> transform gizmos exist should be confirmed in `SceneEditorPanels.kt` / `SceneEditorSystems.kt`
> before documenting them as complete.

## Known Problems

- **Two-world design** (`scene.world` for editor + `SceneEditorDocument.world` for data) is
  powerful but easy to confuse; changes must be clear about which world they target.
- The scene wires 11 systems with strict ordering; reordering can silently break selection or
  rendering.

## Extension Points

- Add a component type to scenes: extend the serializer (`SceneSerializer`/`SceneDescriptors`)
  + document handling + an inspector section.
- Add an editor gizmo/behavior: add a `System` to the document world and register it in
  `show()` at the correct position.
- Add a panel via `UiSystem.addPanel`.

## Safe Change Rules

- Be explicit about which world you mutate (editor vs document). Editor-only entities must carry
  `EditorOnlyComponent` and must not be serialized into the scene.
- Keep serialization changes backward-compatible (there are `SceneSerializerTest`s and
  `schemaVersion` fields).
- Persist only through `engine.sceneFiles`; do not touch the filesystem directly.

## Recommended Improvements

- A unified undo/redo stack for document operations.
- Interactive transform gizmos (translate/rotate/scale).
- Consolidate the system-ordering contract into a documented constant or builder.

## Related Code Patterns

- Asset panel reuses the Asset Browser's `LocalAssetRegistryService`.
- `EditorViewportCameraSystem` is shared with the Model/Animation viewers.
- Serialization round-trip pattern: `SceneSerializerTest`, `SceneAssetCollectorTest`,
  `RuntimeSceneValidatorTest`.
