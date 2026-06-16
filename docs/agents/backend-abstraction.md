# Backend Abstraction — Agent Deep Dive

> Supplements `AGENTS.md` §6 / §17. The core/backend boundary is KRender's most important
> architectural invariant. This file makes the rules concrete.

## The contract

- **Core** (`engine.api` and sibling backend-neutral packages: `render3d`, `terrain` shared logic,
  `assets` editor logic, `ui.editor`/`ui.runtime`/`ui.scene` contracts, `scene`, `serialization`,
  `viewport`, `window`, `math`) defines interfaces + plain data and **must not import
  `com.badlogic.gdx` or `net.mgsx.gltf`**.
- **Backend** (`engine:backend-gdx`, package `engine.backend.gdx`) implements those interfaces using libGDX/OpenGL/gdx-gltf.
- **Tools** (`engine:tools`) should stay backend-neutral except for a small temporary, explicitly
  allowlisted GDX editor-preview adapter layer.
- `EngineBackend` (`api/EngineRuntime.kt`) is the platform contract; `LibGdxBackend`
  (`engine/backend-gdx/.../backend/gdx/LibGdxBackend.kt`) is the desktop/Android implementation; `GdxEngineApplication`
  (`engine/backend-gdx/.../backend/gdx/GdxEngineApplication.kt`) is the libGDX `ApplicationAdapter` bootstrap.
- The backend is split by responsibility into sibling files in `backend/gdx/`:
  `LibGdxBackend.kt` (wiring only), `GdxEngineApplication.kt`, `GdxWindowService.kt`,
  `GdxInputService.kt`, `GdxAssetService.kt`, `GdxModelPoseSampler.kt`, `GdxTaskService.kt`,
  `GdxRenderer3D.kt`, `GdxLineShaderRenderer.kt`, `GdxModelViewerDebugRenderer.kt`,
  `GdxSkyboxRenderer.kt`, `GdxGltfPbrPreviewRenderer.kt`, and `GdxCubemap.kt`. They all share
  the `com.pashkd.krender.engine.backend.gdx` package.
- Dependency direction is `engine:backend-gdx -> core`; `core` must not depend on the backend module.
- Current tool-side GDX exceptions are limited to editor preview/import helpers allowlisted in
  `BackendBoundaryTest`; do not add new ones casually.

## EngineBackend members

`input`, `ui`, `runtimeUi`, `assets`, `assetRegistry`, `sceneFiles`, `runtimeLauncher`, `editorToolLauncher`,
`logger`, `logs`, `runtimeStats`, `profiler`, `tasks`, `terrainTextureSamplerFactory` (nullable),
`window`, `renderer`, and `requestExit()`. `EngineRuntime` exposes these through `EngineContext`
(adding its own `scenes`, `events`, `runtimeUi` wrapper, `viewport`).

## LibGdxBackend wiring

| Service | Implementation |
|---|---|
| `input` | `GdxInputService` (also a libGDX `InputProcessor`) |
| `ui` | `GdxImGuiService` on desktop, `NoOpUiService` on Android |
| `runtimeUi` | `GdxRuntimeUiBackend` (+ app-provided `RuntimeUiActorFactoryProvider` implementations) |
| `assets` | `GdxAssetService` (libGDX `AssetManager`) |
| `assetRegistry` | `LocalAssetRegistryService` (desktop), `NoOpAssetRegistryService` (Android) |
| `sceneFiles` | `GdxSceneFileService` |
| `tasks` | `GdxTaskService` (coroutines) |
| `window` | `GdxWindowService` |
| `renderer` | `GdxRenderer3D` |
| `logs`/`logger` | `EngineLogService` + `GdxAppLogSink` + `FileLogSink` |
| `runtimeStats`/`profiler` | `FrameRuntimeStatsService` / `FrameProfilerService` (core defaults) |
| `terrainTextureSamplerFactory` | `GdxTerrainMaterialTextureSampler` factory |
| launchers | injected (`Lwjgl3*Launcher` from `desktop-lwjgl3` on desktop; `Unsupported*` fallbacks otherwise) |

## Crossing the boundary safely

Pass only backend-neutral data:

- Assets: `AssetRef`, `AssetPack`, `ModelAssetInfo`/`ModelSkeletonInfo`/`ModelBonePose`/
  `ModelAssetBounds`, `TexturePreviewHandle`, `MaterialTextureRef`, `RuntimeTextureData`.
- Rendering: `RenderCommand` subtypes, `RenderContext`, `TransformSnapshot`, `Material`,
  `DynamicMesh`/`DynamicModel`, `MaterialDebugView`/`PbrPreviewView`/`AnimationPlaybackView`.
- Input: `InputSnapshot`, `Key`, `MouseButton`, `PointerState`, `Action`, `Axis`.
- Diagnostics: `LogEntry`, `RuntimeMetric`, `PhaseTiming`, snapshots.

Never expose `ModelInstance`, `Texture`, `Pixmap`, `Camera`, `Environment`,
`Stage`, `Skin`, or other libGDX/gdx-gltf types to `core`, `engine:scene-player`,
game modules, or backend-neutral tool state. The only current exception is the
explicitly allowlisted `engine:tools` GDX editor-preview adapter layer documented below.
## Tool/runtime windows

Desktop tools and the runtime player run as **separate JVM processes**, not in-process scenes:

- `Lwjgl3EditorToolLauncher` (implements `EditorToolLauncher`) spawns a new JVM via
  `Lwjgl3JvmProcessLauncher`, passing `krender.scene` + a path system property
  (`krender.model.path`, `krender.terrain.path`, `krender.scene.path`, `krender.ui.scene.path`).
- `Lwjgl3RuntimeWindowLauncher` (implements `RuntimeWindowLauncher`) launches the runtime player.
- `DesktopMain` composes `ToolsModule` and `ScenePlayerModule` for desktop routes.
- Android creates `GdxEngineApplication` directly and gets its initial `.krscene` scene from
  `ScenePlayerModule`.
- Backends that cannot spawn windows use `UnsupportedEditorToolLauncher` /
  `UnsupportedRuntimeWindowLauncher` (which throw).

## Current tool-side GDX debt

`engine:tools` currently depends on `gdx` for a small explicitly allowlisted editor-preview adapter
surface:

- `TerrainMaterialPreviewBaker`
- `uicomposer/gdx/GdxUiComposerSkinMetadataReader.kt`
- `uicomposer/gdx/GdxUiSceneBuilder.kt`
- `uicomposer/gdx/GdxUiScenePreview.kt`

This is temporary technical debt. New GDX usage in `engine:tools` must be explicitly justified and
added to `BackendBoundaryTest`; the preferred long-term direction is a separate adapter module such
as `engine:tools-gdx` rather than expanding GDX imports through tool logic.

Known follow-up: `GdxUiSceneBuilder` is duplicated between `engine:backend-gdx` runtime UI and the
UI Composer GDX preview adapter in `engine:tools`. Keep behavior identical for now; extract a shared
backend-owned adapter or `engine:tools-gdx` bridge in a focused follow-up.

## Rules

- One concrete backend per process, injected at startup (`EngineRuntime(backend)`).
- New platform capability → add a core interface first, then a backend implementation.
- Editor-only GL work (e.g. terrain `Pixmap` preview baking) stays in editor/backend code and must
  not be required by the runtime path.
- Enforced guard: `BackendBoundaryTest` (`core` tests) fails the build on violations of four rules:
  - **Rule A** — `import com.badlogic.gdx` is allowed in `engine:backend-gdx`, launcher/app modules
    (`desktop-lwjgl3`, `android`, `apps:woolboy-desktop`), and the explicit `engine:tools` preview adapter
    allowlist only.
  - **Rule B** — `import net.mgsx.gltf` is allowed only in `engine:backend-gdx`.
  - **Rule C** — `core` must not import `engine.backend.gdx`.
  - **Rule D** — Backend implementation imports are allowed only in `engine:backend-gdx`,
    `desktop-lwjgl3`, `android`, and `apps:woolboy-desktop`.
  The test reports module, source root, file path, import line, and rule.
