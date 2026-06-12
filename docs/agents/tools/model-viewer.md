# Model Viewer — Agent Context

> Read `AGENTS.md` first. This file covers only the Model Viewer tool.

## Purpose

Inspect a single 3D model (`.gltf`/`.glb`/`.g3dj`/`.g3db`/`.obj`): view geometry, mesh parts,
materials, and texture channels; toggle wireframe, grid, axes, and bounding box; switch between
material-debug visualizations (base color, normal, roughness, metallic, alpha, UV checker, ...)
and a glTF PBR preview with a skybox.

## Current Implementation

- Scene: `core/.../game/ModelViewerScene.kt`.
- Tool internals: `core/.../engine/modelviewer/`.
- Launched as a separate JVM window by `Lwjgl3EditorToolLauncher.launchModelViewer(path)`
  (`krender.scene=model-viewer`, `krender.model.path=<path>`).

## Main Files

| File | Responsibility |
|---|---|
| `game/ModelViewerScene.kt` | Builds camera/light/model entities, systems, and 8 ImGui panels. |
| `engine/modelviewer/ModelViewerState.kt` | All viewer UI/runtime state (display mode, debug mode, toggles, selection). |
| `engine/modelviewer/ModelViewerSystems.kt` | `ModelViewerSystem`, `ModelViewerModelRenderSystem`, `ModelViewerBoundingBoxSystem`, `ModelViewerViewportGuideSystem`. |
| `engine/modelviewer/ModelViewerOperations.kt` | UI-driven actions applied to state. |
| `engine/modelviewer/ModelViewerPanels.kt` | The panel implementations. |
| `engine/modelviewer/ModelViewerTextureChannelResolver.kt` | Resolves texture channels for debug views. |
| `engine/modelviewer/ImGuiLayoutConfig.kt` | `ModelViewerUiLayoutDefaults`. |
| `engine/editor/viewport/EditorViewportCamera.kt` | Shared editor orbit/fly camera system. |

## Main Classes

| Class | Responsibility |
|---|---|
| `ModelViewerScene` | Scene composition; logs first-frame/update/render; per-panel error wrapping. |
| `ModelViewerState` | Source of truth for the viewer; read by render systems and panels. |
| `ModelViewerSystem` | Applies UI actions (display/debug modes, toggles), handles exit. |
| `ModelViewerModelRenderSystem` | Emits `DrawModel` with material/debug/PBR/visible-mesh-part data. |
| `ModelViewerBoundingBoxSystem` | Computes bounds (via `engine.assets.modelBounds`) and draws box lines. |
| `EditorViewportCameraSystem` | Orbit/zoom/pan camera driven by `engine.input`. |

## UI Panels

`ModelViewerToolbarPanel`, `ModelViewerViewportPanel`, `ModelViewerInfoPanel`,
`ModelViewerMeshPartsPanel`, `ModelViewerMaterialsPanel`, `ModelViewerTextureChannelsPanel`,
`ModelViewerLoadingPanel`, `LogsPanel` (8 total).

## Engine Services Used

`engine.assets` (model metadata, bounds, texture preview handles), `engine.input` (camera),
`engine.ui` (panels + texture previews), `engine.logger`/`engine.logs`, `engine.requestExit`.

## Data Flow

1. `requiredAssets` queues the model plus the default PBR skybox and the UV-checker textures.
2. Assets load asynchronously; `ModelViewerLoadingPanel` shows progress until ready.
3. Panels mutate `ModelViewerState` (via `ModelViewerOperations`); `ModelViewerSystem` reconciles.
4. `ModelViewerModelRenderSystem` reads state and submits a `DrawModel` carrying
   `MaterialDebugView` / `PbrPreviewView` / `visibleMeshPartIndices` as appropriate.
5. `GdxRenderer3D` routes the command to the normal / wireframe / debug / PBR path.

## Lifecycle

`show()` → load layout, build `ModelViewerState`/`ModelViewerOperations`, create camera +
ambient light + model entity, add systems (guide, viewer, UI, camera, bounding box, render).
`hide()` releases cursor capture. Uses `SceneConfigPresets.EditorTool`.

## Supported Asset Types

Models: glTF (`.gltf`/`.glb`), libGDX (`.g3dj`/`.g3db`), Wavefront (`.obj`). Textures for the
UV-checker options and the default skybox preview.

## Current Features

- Mesh-part list with per-part isolation (`visibleMeshPartIndices`).
- Material inspection + texture-channel previews via `TexturePreviewHandle`.
- Material debug modes (`MaterialDebugMode`) and a glTF PBR preview (`PbrPreviewView`).
- Wireframe, wireframe overlay, grid, axes, bounding box toggles.
- Async load with a dedicated loading panel.

## Missing / Incomplete Features

- No animation playback (that is the Animation Viewer's job).
- No material editing/saving — inspection only.
- No multi-model comparison.

## Known Problems

- `ModelViewerScene` carries heavy per-frame `info`-level logging and wraps every panel/ system,
  which is verbose; useful for debugging but noisy in normal runs.
- Material/texture debug correctness depends on backend metadata ordering
  (see `ModelViewerTextureChannelResolver` and `selectedMaterialId` handling in `Render.kt`).

## Extension Points

- Add a panel: implement `UiPanel`, register via `addPanel(uiSystem, name, panel)` in
  `createUiSystem`.
- Add a debug mode: extend `MaterialDebugMode` (core) + the backend
  `GdxModelViewerDebugRenderer`.
- Add render behavior: extend `DrawModel` fields (core) and the renderer handling.

## Safe Change Rules

- Keep all model inspection backend-neutral: pull data through `AssetService` metadata
  accessors, never `ModelInstance`/`Texture` directly.
- New visualizations must flow through `DrawModel`/`RenderCommand`, not direct GL.
- Preserve the loading-tolerant design — never assume the model is loaded in `show()`.

## Recommended Improvements

- Make the verbose per-frame logging level-gated (`debug`/`trace`) to reduce noise.
- Share more viewer scaffolding with the Animation Viewer (they are near-duplicates).

## Related Code Patterns

- Animation Viewer (`game/AnimationViewerScene.kt`) is structurally almost identical and is the
  best reference for adding skeleton/animation-related features.
- `EditorViewportCameraSystem` is shared with Animation Viewer and Scene Editor.
