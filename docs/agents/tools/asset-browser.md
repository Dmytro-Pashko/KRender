# Asset Browser — Agent Context

> Read `AGENTS.md` first. This file covers only the Asset Browser tool.

## Purpose

Default landing scene of KRender. Scans and indexes project assets, lets the user filter/sort/
inspect them (with model metadata previews), perform basic asset operations (create, rename,
duplicate, delete, reveal), and **open each asset in the correct editor tool** via a registry.

## Current Implementation

- Scene: `engine/tools/.../assetbrowser/AssetBrowserScene.kt`.
- Tool internals package: `engine/tools/.../assetbrowser/`, with shared asset infrastructure kept in `core/.../engine/assets/`.
- Desktop startup resolves it through `engine/tools/ToolsModule.kt` and each platform launcher's local `DesktopMain.kt`.
  (`krender.scene` unset → `asset-browser` on desktop).
- Tool routing uses `AssetToolRegistry`; there is intentionally **no** `when(category)` in the scene.

## Main Files

| File | Responsibility |
|---|---|
| `engine/tools/.../assetbrowser/AssetBrowserScene.kt` | Wires registries, services, systems, and panels; defines the `AssetTool` implementations and `SceneOperationsHandler`. |
| `engine/tools/.../assetbrowser/AssetBrowserSystem.kt` | Drives background scans, filtering/sorting, selected-model metadata sync, activation requests. |
| `engine/tools/.../assetbrowser/AssetBrowserState.kt` | Mutable UI/runtime state for the browser. |
| `engine/tools/.../assetbrowser/AssetBrowserPanels.kt` | `AssetControlsPanel`, `AssetBrowserPanel`, `AssetDetailsPanel`. |
| `engine/tools/.../assetbrowser/AssetBrowserLayout.kt` | `AssetBrowserUiLayoutDefaults` (panel layout config). |
| `engine/tools/.../assetbrowser/AssetBrowserUiOperations.kt` | UI-side operations helper. |
| `engine/tools/.../assetbrowser/EnvironmentAssetCreation.kt` | Creates Environment manifests from HDR source assets and launches Environment Editor. |
| `engine/tools/.../common/EditorTexturePreviewService.kt` | Shared editor-facing texture preview handle/status lookup used by texture details and other tools. |
| `engine/assets/AssetRegistryService.kt` | `LocalAssetRegistryService` — filesystem scan + `.krmeta`. |
| `engine/assets/AssetOperationsService.kt` | `LocalAssetOperationsService` — create/rename/duplicate/delete/reveal. |
| `engine/tools/.../assetbrowser/AssetToolRegistry.kt` | Maps assets to `AssetTool`s; resolves default + "open with" tools. |
| `engine/assets/AssetImporter.kt`, `AssetTypeDetector.kt`, `AssetMetadataCodec.kt` | Import + type detection + sidecar codec. |
| `engine/assets/AssetDomain.kt` | `AssetDescriptor`, `AssetCategory`, `AssetType`, `AssetId`. |

## Main Classes

| Class | Responsibility |
|---|---|
| `AssetBrowserScene` | Scene composition + `openAsset` routing through the tool registry. |
| `AssetBrowserSystem` | System: scan scheduling, filter/sort, model metadata, activation. |
| `LocalAssetRegistryService` | Background-safe `scanSnapshot()` + main-thread `applySnapshot()`. |
| `LocalAssetOperationsService` | File operations; triggers `state.refreshRequested`. |
| `AssetToolRegistry` | Registry of `AssetTool`s by category, default-action aware. |
| `AssetTool` (interface) | `open(asset, context)`; impls call `editorToolLauncher`/`runtimeLauncher`. |

## UI Panels

- `AssetControlsPanel` — search, category filter, sort mode, create/refresh actions.
- `AssetBrowserPanel` — asset list, selection, activation, per-asset context operations.
- `AssetDetailsPanel` — selected asset metadata + model preview info.
- `LogsPanel` (shared) — engine log stream.

## Engine Services Used

`engine.assets` (model metadata + queueing), `engine.tasks` (background scan),
`engine.logger`/`engine.logs`, `engine.ui`, `engine.editorToolLauncher`, `engine.runtimeLauncher`.

## Data Flow

1. `AssetBrowserSystem.update` requests an initial scan (and re-scans on `state.refreshRequested`).
2. Scan runs via `tasks.launchBackground` → `registry.scanSnapshot()` (immutable result).
3. Result is posted back with `tasks.postToMain` → `registry.applySnapshot` + state update.
4. Filtering/sorting derive `state.filteredAssets`; selected model is queued via `assets.queue`
   and its `ModelAssetInfo` is read once loaded.
5. Activation: panel sets `state.activationRequestedAssetId`; system resolves the descriptor and
   calls `onAssetActivated` → `AssetBrowserScene.openAsset` → `AssetToolRegistry.defaultToolFor`
   → `tool.open(...)` → launcher spawns a separate tool window.

## Lifecycle

`show()` builds importers, registry, tool registry (registers `ModelViewerAssetTool`,
`AnimationViewerAssetTool`, `TerrainEditorAssetTool`, `UiComposerAssetTool`, `SceneEditorAssetTool`,
`SceneRuntimeAssetTool`), operations service, and the `AssetBrowserSystem` + `UiSystem`. Uses
`SceneConfigPresets.AssetBrowser`. No custom `dispose`/`hide` beyond base.

## Supported Asset Types

All discovered categories (`AssetDomain.kt`): `Model`, `Texture`, `Material`, `Terrain`,
`Scene2D`, `UI` (`.krui`), `Environment`, `Scene`, `Other`. The `Environment` category is the
single browser home for `.environment.json`, HDR source images, legacy `.krskybox` descriptors,
and generated environment resources. Scanned roots: `model`, `textures`, `skyboxes`, `materials`,
`terrains`, `ui/scenes`, `scenes`, `shaders`, `assets`.

## Current Features

- Background filesystem scan with `.krmeta` sidecar creation/repair.
- Search, category filter, multiple sort modes.
- Model metadata preview (triangle/vertex/material counts via `ModelAssetInfo`).
- Create / rename / duplicate / delete / reveal operations with sensible default content
  (skybox, scene, terrain, `.krui`, material templates).
- The Create Asset flow now includes `Environment`, with an EXR/HDR file picker that copies an
  external source into `environments/<name>/sources/`, creates `<name>.environment.json`, and opens
  the result in Environment Editor.
- HDR source (`.exr` / `.hdr`) context actions that create a new Environment manifest,
  copy the selected source into `environments/<name>/sources/`, and open the result in Environment Editor.
- Generated Environment resources expose `Open Parent Environment`, which locates the closest
  surrounding `.environment.json` and launches Environment Editor for that manifest.
- `.krui` UI assets route to UI Composer for validation, Scene2D preview, hierarchy/inspector editing, undo/redo, and save workflows.
- "Open" (default tool) and "Open with" (alternate tools) per asset.

## Missing / Incomplete Features

- No thumbnail/texture grid previews in the list (text + metadata only).
- No drag/drop, multi-select, or batch operations.
- No audio/script tooling despite the categories existing.

## Known Problems

- Scan walks all roots on every refresh (no incremental/file-watch updates); large asset trees
  re-scan fully.
- `AssetBrowserScene` is large and mixes scene wiring, tool definitions, and default-content
  templates (`SceneOperationsHandler`) in one file.

## Extension Points

- Add a new tool: implement `AssetTool` and `register(...)` it in `AssetBrowserScene.show()`.
- Add a new asset type: extend `AssetType`/`AssetCategory` (`AssetDomain.kt`),
  `AssetTypeDetector`, and optionally an `AssetImporter`.
- Add metadata: extend `LocalAssetRegistryService.describe` (see `textureMetadata`,
  `terrainMetadata`, `sceneMetadata`).

## Safe Change Rules

- Keep scan IO inside `scanSnapshot()` (background-safe, immutable result); apply only via
  `applySnapshot()` on the main thread. Never mutate registry state from a background coroutine.
- Route activation through `AssetToolRegistry` — do not add category `when` branches in the scene.
- Keep `com.badlogic.gdx` out of this package (it is core editor logic).

## Recommended Improvements

- Split tool definitions and default-content templates out of `AssetBrowserScene.kt`.
- Incremental/watched scanning to avoid full re-walks.
- Texture/model thumbnail previews using `TexturePreviewHandle`.

## Related Code Patterns

- Background-scan-then-post pattern: `AssetBrowserSystem.requestScan`.
- The same `LocalAssetRegistryService` is reused by the Scene Editor asset panel
  (`SceneAssetBrowserModel` in `SceneEditorScene`).
- The state+operations+panels recipe mirrors Model Viewer / Terrain Editor / Scene Editor.
