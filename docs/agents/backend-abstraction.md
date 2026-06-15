# Backend Abstraction — Agent Deep Dive

> Supplements `AGENTS.md` §6 / §17. The core/backend boundary is KRender's most important
> architectural invariant. This file makes the rules concrete.

## The contract

- **Core** (`engine.api` and sibling backend-neutral packages: `render3d`, `terrain` shared logic,
  `assets` editor logic, `ui.editor`/`ui.runtime`/`ui.scene` contracts, `scene`, `serialization`,
  `viewport`, `window`, `math`, tool logic packages) defines interfaces + plain data and **must not
  import `com.badlogic.gdx` or `net.mgsx.gltf`**.
- **Backend** (`engine.backend.gdx` only) implements those interfaces using libGDX/OpenGL/gdx-gltf.
- `EngineBackend` (`api/EngineRuntime.kt`) is the platform contract; `LibGdxBackend`
  (`backend/gdx/LibGdxBackend.kt`) is the desktop/Android implementation; `GdxEngineApplication`
  (`backend/gdx/GdxEngineApplication.kt`) is the libGDX `ApplicationAdapter` bootstrap.
- The backend is split by responsibility into sibling files in `backend/gdx/`:
  `LibGdxBackend.kt` (wiring only), `GdxEngineApplication.kt`, `GdxWindowService.kt`,
  `GdxInputService.kt`, `GdxAssetService.kt`, `GdxModelPoseSampler.kt`, `GdxTaskService.kt`,
  `GdxRenderer3D.kt`, `GdxLineShaderRenderer.kt`, `GdxModelViewerDebugRenderer.kt`,
  `GdxSkyboxRenderer.kt`, `GdxGltfPbrPreviewRenderer.kt`, and `GdxCubemap.kt`. They all share
  the `com.pashkd.krender.engine.backend.gdx` package.
- A handful of pre-existing leaks (`Gdx`/`Pixmap`/JSON utils in `ui.editor` and `terrain`) are
  allow-listed in `BackendBoundaryTest`; do not add new ones.

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
| launchers | injected (`Lwjgl3*Launcher` on desktop; `Unsupported*` fallbacks otherwise) |

## Crossing the boundary safely

Pass only backend-neutral data:

- Assets: `AssetRef`, `AssetPack`, `ModelAssetInfo`/`ModelSkeletonInfo`/`ModelBonePose`/
  `ModelAssetBounds`, `TexturePreviewHandle`, `MaterialTextureRef`, `RuntimeTextureData`.
- Rendering: `RenderCommand` subtypes, `RenderContext`, `TransformSnapshot`, `Material`,
  `DynamicMesh`/`DynamicModel`, `MaterialDebugView`/`PbrPreviewView`/`AnimationPlaybackView`.
- Input: `InputSnapshot`, `Key`, `MouseButton`, `PointerState`, `Action`, `Axis`.
- Diagnostics: `LogEntry`, `RuntimeMetric`, `PhaseTiming`, snapshots.

**Never** expose `ModelInstance`, `Texture`, `Pixmap`, `Camera`, `Environment`, `Stage`, `Skin`,
or other libGDX/gdx-gltf types to core/tool code.

## Tool/runtime windows

Desktop tools and the runtime player run as **separate JVM processes**, not in-process scenes:

- `Lwjgl3EditorToolLauncher` (implements `EditorToolLauncher`) spawns a new JVM via
  `Lwjgl3JvmProcessLauncher`, passing `krender.scene` + a path system property
  (`krender.model.path`, `krender.terrain.path`, `krender.scene.path`, `krender.ui.scene.path`).
- `Lwjgl3RuntimeWindowLauncher` (implements `RuntimeWindowLauncher`) launches the runtime player.
- `DesktopMain` composes `ToolsModule` and `ScenePlayerModule` for desktop routes.
- `ScenePlayerMain` is the dedicated `.krscene` playback entry point used by Android.
- Backends that cannot spawn windows use `UnsupportedEditorToolLauncher` /
  `UnsupportedRuntimeWindowLauncher` (which throw).

## Rules

- One concrete backend per process, injected at startup (`EngineRuntime(backend)`).
- New platform capability → add a core interface first, then a backend implementation.
- Editor-only GL work (e.g. terrain `Pixmap` preview baking) stays in editor/backend code and must
  not be required by the runtime path.
- Enforced guard: `BackendBoundaryTest` (`core` tests) fails the build on violations of four rules:
  - **Rule A** — `import com.badlogic.gdx` only allowed inside `engine/backend/gdx/`.
  - **Rule B** — `import net.mgsx.gltf` only allowed inside `engine/backend/gdx/`.
  - **Rule C** — `engine.api` must not import `engine.backend.gdx`.
  - **Rule D** — Non-backend files must not import `engine.backend.gdx` (known bootstrap/preview
    exceptions are allow-listed).
  Pre-existing leaks are allow-listed per rule. The test reports file path, import line, and rule.
