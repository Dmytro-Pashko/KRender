# KRender — Agent Guide

> Primary entry point for AI coding agents working on the KRender SDK.
> Everything here is grounded in the current codebase. When code and this
> document disagree, the code wins — please update this file in the same change.

Related deep-dive documents (read these before large changes in their area):

- `docs/agents/core-engine.md` — runtime, lifecycle, ECS.
- `docs/agents/rendering-pipeline.md` — render command flow and the LibGDX renderer.
- `docs/agents/asset-system.md` — asset handles, registry, `.krmeta`, importers.
- `docs/agents/ui-system.md` — ImGui editor UI and Scene2D runtime UI.
- `docs/agents/backend-abstraction.md` — the core/backend boundary rules.
- `docs/agents/logging.md` — logging and diagnostics conventions.
- `docs/agents/tools/` — one context file per editor tool.
- `ARCHITECTURE_REVIEW.md` — critical review, risks, and refactoring roadmap.

---

## 1. Project Overview

KRender SDK is a **Kotlin + libGDX project shaped as a
small game engine**, not a direct libGDX application. The defining design rule is a
**backend-neutral engine core** with a separate LibGDX runtime backend, so gameplay,
scenes, and systems do not spread `Gdx.*` calls through the project.

Beyond the runtime engine, the repository ships a set of **ImGui-based editor tools**
that run as standalone scenes (Asset Browser, Model Viewer, Animation Viewer,
Terrain Editor, Scene Editor, and a placeholder UI Composer). These tools are
first-class products built on the same engine primitives.

- Language: Kotlin `2.2.21`, JVM target 11.
- Rendering/runtime: libGDX `1.14.0`, gdx-gltf `2.2.1`, LWJGL3 `3.4.1`.
- Async: kotlinx-coroutines `1.10.2`.
- Root package: `com.pashkd.krender`.

> Keep `README.md` and the docs under `docs/` aligned with the current module layout,
> scene routing, and standalone-app packaging whenever structure changes.

---

## 2. Repository Structure

```text
KRender SDK/
+-- core/                  # Backend-neutral engine/runtime API, data, and shared services
|   +-- src/main/kotlin/com/pashkd/krender/
|   |   +-- engine/
|   |   |   +-- api/                      # Backend-neutral core API (see section 5)
|   |   |   +-- render3d/                 # 3D components + ModelRenderSystem, environment system
|   |   |   +-- animation/                # AnimationComponent
|   |   |   +-- assets/                   # Shared asset registry/metadata/importers/import-export services
|   |   |   +-- terrain/                  # Shared terrain runtime/mesh/persistence infrastructure
|   |   |   +-- scene/                    # Scene config, serialization, file/launch services
|   |   |   +-- ui/editor/                # Editor UI service contracts and shared ImGui helpers
|   |   |   +-- ui/runtime/               # Scene2D-style runtime game UI service
|   |   |   +-- ui/scene/                 # `.krui` UiScene document model + validation
|   |   |   +-- material/                 # Material + terrain material libraries
|   |   |   +-- serialization/            # KRender JSON helpers
|   |   |   +-- viewport/                 # Runtime viewport scaling
|   |   |   +-- window/                   # Window service abstraction
|   |   |   +-- math/                     # Transform math
|   |   +-- game/                         # Shared game-facing helpers that are not standalone tool/player routes
|   +-- src/test/kotlin/...               # JVM unit tests (no GL needed)
+-- engine/
|   +-- backend-gdx/                      # LibGDX backend adapter; owns all Gdx.* / OpenGL / gdx-gltf code
|   +-- scene-player/                     # `.krscene` runtime/player route module
|   +-- tools/                            # Editor tool module; all development editor tools live here
+-- games/
|   +-- woolboy/                          # Standalone Woolboy gameplay/client module + bundled assets
+-- apps/
|   +-- woolboy-desktop/                  # Standalone Woolboy desktop launcher + executable JAR task
+-- desktop-lwjgl3-win/    # Windows desktop SDK host application
+-- desktop-lwjgl3-macos/  # macOS desktop SDK host application
+-- desktop-lwjgl3-linux/  # Linux desktop SDK host application
+-- android/               # Android launcher (requires Android SDK)
+-- assets/                # Shared runtime assets (models, textures, terrains, scenes, ui, skyboxes)
+-- docs/                  # Documentation + screenshots (agents/, screenshot/)
+-- build.gradle.kts, settings.gradle, gradle.properties
```

Gradle subprojects (`settings.gradle`): `core`, `engine:backend-gdx`, `engine:tools`, `engine:scene-player`,
`desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, `desktop-lwjgl3-linux`, `android`,
`games:woolboy`, `apps:woolboy-desktop`. The root `assets` directory remains a shared resource folder for the
existing editor/runtime app, while the Woolboy app bundles its own curated resources from
`games/woolboy/src/main/resources/assets/woolboy/`.

---

## 3. Architecture Summary

Three conceptual layers, with a strict dependency direction:

```text
game scenes / tools  ──▶  engine.api (+ render3d, terrain, ui.editor, ...)
                                      ▲
                                      │
                              engine:backend-gdx
        │                          │                                              │
   build scenes,             backend-neutral                              owns ALL Gdx.* /
   systems, panels           contracts + data                             OpenGL / AssetManager / gdx-gltf
```

- **Core (`engine.api` and sibling backend-neutral packages)** define contracts
  (`EngineContext`, `Scene`, `System`, `AssetService`, `Renderer`, `RenderCommand`, ...)
  and plain data. They must not import `com.badlogic.gdx`.
- **Backend (`engine:backend-gdx`, package `engine.backend.gdx`)** implements those contracts using libGDX and
  is the only place allowed to touch `Gdx.*`, OpenGL, `AssetManager`, ImGui rendering, etc.
- **Game/tool scenes (`com.pashkd.krender.game.*`)** assemble entities, systems,
  and panels against the core API only.

The runtime never calls platform APIs directly — it talks to an `EngineBackend`
that is injected at startup (`GdxEngineApplication` → `LibGdxBackend`).

---

## 4. Module Responsibilities

| Module | Responsibility |
|---|---|
| `core` | Backend-neutral engine/runtime API, data, and shared services such as assets, terrain, scene files, serializers, and `.krui` documents. |
| `engine:backend-gdx` | LibGDX backend adapter and all Gdx/OpenGL/gdx-gltf implementation code. Depends on `core`. |
| `engine:tools` | Editor tool module containing Asset Browser, Model Viewer, Animation Viewer, Terrain Editor, Scene Editor, UI Composer, tool routing, and editor-only helpers. It is mostly backend-neutral, with a small explicitly allowlisted temporary GDX editor-preview adapter layer. |
| `engine:scene-player` | Runtime/player module containing `ScenePlayerScene`, `ScenePlayerBuilder`, `ScenePlayerModule`, preferred `scene-player` routing plus legacy aliases. It does not own a GDX entry point. |
| `desktop-lwjgl3-win` | Windows desktop SDK host application. Owns `WinLwjgl3Launcher`, `WinStartupPolicy`, `DesktopMain`, secondary JVM launchers, Windows run config, Windows JAR packaging, and the Windows Construo target. |
| `desktop-lwjgl3-macos` | macOS desktop SDK host application. Owns `MacOsLwjgl3Launcher`, `MacOsStartupPolicy`, `DesktopMain`, secondary JVM launchers, macOS run config, macOS JAR packaging, and macOS Construo targets. |
| `desktop-lwjgl3-linux` | Linux desktop SDK host application. Owns `LinuxLwjgl3Launcher`, `LinuxStartupPolicy`, `DesktopMain`, secondary JVM launchers, Linux run config, Linux JAR packaging, and the Linux Construo target. |
| `android` | Android launcher (`AndroidLauncher`). Creates `GdxEngineApplication` from `engine:backend-gdx` and its initial scene through `ScenePlayerModule`, uses `NoOpUiService` (no ImGui), and requires the Android SDK to build. |
| `assets` | Runtime asset files scanned by the asset registry and loaded by the backend. |

---

## 5. Core Engine Concepts

All core API types live under `core/.../engine/api/`. Key files: `EngineRuntime.kt`,
`Scene.kt`, `Ecs.kt`, `Render.kt`, `Assets.kt`, `Tasks.kt`, `Debug.kt`, `Events.kt`,
`Input.kt`, `Math.kt`.

### EngineContext
`EngineContext` (in `EngineRuntime.kt`) is a **service-locator facade** passed to every
scene. It exposes engine-wide services as read-only properties. `EngineRuntime` implements
`EngineContext`; it owns `SceneManager`, `EventBus`, `RuntimeUiService`, and
`RuntimeViewportService` directly, and delegates the rest to the injected `EngineBackend`.

Services available on `EngineContext`:
`scenes`, `assets`, `assetRegistry`, `sceneFiles`, `runtimeLauncher`, `editorToolLauncher`, `input`,
`ui`, `runtimeUi`, `events`, `logger`, `logs`, `runtimeStats`, `profiler`, `tasks`,
`viewport`, `window`, `terrainTextureSamplerFactory` (nullable), plus `requestExit()`.

Inside a `Scene`, access services via the protected `engine` property (e.g. `engine.assets`).

### Scene
`Scene` (`Scene.kt`) is the engine-owned container. It owns a `SceneWorld`, declares
`requiredAssets: List<AssetPack>`, exposes a `SceneConfig` (window + viewport), and tracks
a `SceneState`. Lifecycle hooks: `scheduleAssets`, `show`, `fixedUpdate`, `update`, `lateUpdate`,
`render`, `debugRender`, `overlayRender`, `resize`, `hide`, `dispose`. There is no `load` hook
(see §8).

`SceneManager` keeps an `ArrayDeque` stack and applies **deferred** transitions
(`replace`/`push`/`pop`) at a safe point in the frame, never mid-iteration.

### Systems
`System` (`Ecs.kt`) is the unit of behavior. Override any of: `onAdded`, `fixedUpdate`,
`update`, `lateUpdate`, `render`, `debugRender`. Systems are held in an ordered
`SystemPipeline` bound to a `SceneWorld`. **Execution order is registration order** —
the order systems are added in a scene's `show()` matters.

### Services
Services are interfaces in `engine.api` (`AssetService`, `InputService`, `TaskService`,
`Logger`/`LogService`, `RuntimeStatsService`, `ProfilerService`, `Renderer`) plus
sibling packages (`SceneFileService`, `WindowService`, `RuntimeViewportService`,
`UiService`, `RuntimeUiService`, launchers). Implementations live in the backend or as
default core implementations (`EngineLogService`, `FrameRuntimeStatsService`,
`FrameProfilerService`, `EventBus`).

### Components
`Component` is a marker interface. Components are **plain mutable data** with no behavior
and no backend dependencies. Built-ins (`Ecs.kt`): `TransformComponent`, `ParentComponent`,
`NameComponent`, `VelocityComponent`, `LifetimeComponent`, `ScriptComponent`. 3D ones
(`render3d/Components3D.kt`): `PerspectiveCameraComponent`, `ActiveCameraComponent`,
`FreeCameraControllerComponent`, `ModelComponent`, `LightComponent`, `Material`,
`ShaderPipeline`. Tools define their own component packages (terrain, sceneeditor, woolboy).

### Assets
Assets are referenced by typed handles `AssetRef<T>` (`Assets.kt`) where `T` is a marker
type (`ModelAsset`, `TextureAsset`, `TerrainAsset`, `ShaderAsset`). Core code only holds
`AssetRef` handles; the backend resolves them. `AssetService.get()` deliberately **throws**
in the LibGDX backend — core must not pull backend objects. Rich metadata is exposed
backend-neutrally via `ModelAssetInfo`, `ModelSkeletonInfo`, `ModelBonePose`,
`TexturePreviewHandle`, etc.

### Events
`EventBus` (`Events.kt`) is a small synchronous typed dispatcher. `subscribe` returns a
`Subscription` to unsubscribe. Dispatch is by **exact runtime type** (no supertype fan-out).

### Tasks
`TaskService` (`Tasks.kt`) separates `launchBackground`, `onBackground`, `onIo`, `onMain`,
and `postToMain`/`flushMainThreadQueue`. The game loop flushes the main-thread queue once
per frame. **Background jobs must return immutable results or post mutations back to the
main thread** — see the Asset Browser scan for the canonical pattern.

### Rendering
Systems never draw directly. They submit backend-neutral `RenderCommand`s (`Render.kt`,
e.g. `DrawModel`, `DrawDynamicModel`, `DrawLine`, `DrawWorldGrid`, `DrawWorldAxes`,
`DrawText`, `ApplyEnvironment`) into `world.renderCommands`. The `Renderer` backend consumes
a sorted snapshot each frame. See §7.

---

## 6. Backend Abstraction

- `EngineBackend` (`EngineRuntime.kt`) is the contract a platform must implement. It exposes
  all services plus a `Renderer` and `requestExit()`.
- `LibGdxBackend` (`engine/backend-gdx/.../backend/gdx/LibGdxBackend.kt`) is the concrete implementation. It wires:
  `GdxInputService`, `GdxImGuiService` (or `NoOpUiService` on Android), `GdxRuntimeUiBackend`,
  `GdxAssetService`, `GdxSceneFileService`, `GdxTaskService`, `GdxWindowService`,
  `GdxRenderer3D`, `EngineLogService` (+ `GdxAppLogSink`, `FileLogSink`),
  `FrameRuntimeStatsService`, `FrameProfilerService`, and the launchers.
- `GdxEngineApplication` extends libGDX `ApplicationAdapter`, creates the backend +
  `EngineRuntime`, and drives `renderFrame` / `resize` / `dispose`.

**Boundary rule:** packages other than `engine.backend.gdx` must not import
`com.badlogic.gdx`. Backend-specific objects are passed across the boundary only as opaque,
backend-neutral data (`TexturePreviewHandle`, `RuntimeTextureData`, `ModelAssetInfo`, ...).

---

## 7. Rendering Pipeline

1. Systems push `RenderCommand`s into `world.renderCommands` during the `render`/`debugRender`
   phases (`ModelRenderSystem` is the canonical example).
2. `RenderCommandBuffer.snapshot()` returns the commands sorted by `sortKey`.
3. `GameLoop` builds a `RenderContext(scene, alpha, deltaSeconds, commands)` and calls
   `backend.renderer.render(context)`.
4. `GdxRenderer3D` (`GdxRenderer3D.kt`) interprets commands: it resolves the active camera
   and environment, delegates skyboxes, lines, material-debug, and glTF-PBR-preview rendering to
   sibling backend helpers, then routes `DrawModel`/`DrawDynamicModel` through normal and
   wireframe paths. glTF models go through gdx-gltf; `.g3dj/.g3db/.obj` go through libGDX loaders.
5. Per-entity render caches (`ModelInstance`, `AnimationController`, glTF scenes) live in the
   renderer and are intentionally separate from `GdxModelPoseSampler` pose-sampling caches.

Terrain rendering details live in `docs/agents/tools/terrain-editor.md` and the shared runtime code under
`core/.../engine/terrain/`.

---

## 8. Scene Lifecycle

Activation (`SceneManager.activateScene`):

```text
New ──attach(context)──▶ setState(Loading) ──scheduleAssets(assets)──▶ setState(Ready)
    ──stack.push──▶ applySceneConfig(window+viewport) ──▶ show() ──▶ setState(Active)
```

Teardown (`disposeScene`): `setState(Disposing)` → `hide()` → `dispose()` → `setState(Disposed)`.
`Push` pauses the previous top (`SceneState.Paused`); `Pop` reactivates and reapplies config.

Per-frame work runs through `SceneWorld` phases: `fixedUpdate` (with `CommandBuffer` flush) →
`update` → `lateUpdate` (then flush) → `render` (clears + collects render commands) → `debugRender`.

> **Important for agents:** There is no `Scene.load()` hook and no `SceneState.Preparing` state.
> Effective lifecycle is `scheduleAssets` → `show`. Assets load **asynchronously** while the scene
> is already `Active` (the loop calls `assets.update()` each frame), so scenes must tolerate
> "not loaded yet" — viewer tools poll `AssetService.isLoaded` and show a Loading panel.
> Do not assume assets are ready in `show()`.

---

## 9. Asset Lifecycle

- Scenes declare `requiredAssets` (`AssetPack`s of `AssetRef`s); the default `scheduleAssets`
  queues them via `AssetService.queue`.
- `GdxAssetService` (backend) loads via libGDX `AssetManager` (loaders registered for
  `.g3dj`, `.g3db`, `.obj`, `.gltf`, `.glb`; textures and shaders too). `GameLoop` advances
  loading with `assets.update(budgetMs)` each frame.
- **Terrain assets are not loaded through `AssetService`** — runtime terrain loading is handled
  separately (`terrain/RuntimeTerrainService`, persistence, mesh build). `AssetService.queue`
  logs and ignores `TerrainAsset`.
- **Editor asset discovery** is a different system: `LocalAssetRegistryService`
  (`assets/AssetRegistryService.kt`) scans root folders, maintains `*.krmeta` sidecar metadata,
  and produces immutable `AssetRegistrySnapshot`s on a background thread, applied on the main
  thread. The backend creates one instance and exposes it as `EngineContext.assetRegistry`;
  Asset Browser, Scene Editor, and UI Composer all use `engine.assetRegistry`.
  On Android (and runtime-only environments), `NoOpAssetRegistryService` is used instead.
- Disposal: `AssetService.unload` releases backend state. Tools release cursor capture in
  `hide()` and flush world commands in `dispose()`.

---

## 10. UI and Tooling Architecture

KRender has **two distinct UI stacks**:

- **Editor UI (ImGui)** — `engine.ui.editor`. `UiService : UiContext` is the contract;
  `GdxImGuiService` is the desktop implementation; `NoOpUiService` is used on Android.
  Panels implement the `UiPanel` fun-interface and are registered into a `UiSystem`
  (a `System` that draws all panels during `update`). **The `GameLoop` owns the ImGui frame
  boundary** (`ui.beginFrame` before systems, `ui.endFrame` after) so input-capture flags
  are current. Layout is data-driven via `ImGuiLayoutConfig` + `ImGuiLayoutConfigLoader`
  + `ImGuiLayoutRuntimeTracker`, and window events are logged via `ImGuiWindowEventLogger`.
- **Runtime game UI (Scene2D)** — `engine.ui.runtime`. `RuntimeUiService` wraps a
  `RuntimeUiBackend` (`GdxRuntimeUiBackend`) and drives Scene2D-style screens. The `.krui`
  UiScene document model and validators live in `engine.ui.scene`.

Every tool scene follows the same composition recipe: build state objects → build systems →
create a `UiSystem` and `addPanel(...)` for each panel → add an `EditorViewportCameraSystem`
and render systems. `LogsPanel` is shared across all tools.

---

## 11. Tools

Tools are standalone `Scene`s selected by the desktop/tool layer (`DesktopMain` + `ToolsModule`) from the `krender.scene` system property.
On desktop they are opened as **separate JVM windows** by `Lwjgl3EditorToolLauncher`
(via `editorToolLauncher` on `EngineContext`). Inside the Asset Browser, the
`AssetToolRegistry` maps asset categories to `AssetTool`s that call the launcher.
Scene playback routes are handled separately by `engine:scene-player` through `ScenePlayerModule`.
Desktop bootstrap is platform-local: Windows, macOS, and Linux each have their own self-contained
`desktop-lwjgl3-*` launcher module with a local `DesktopMain` composition.
The small duplication across `DesktopMain`, `DesktopApplication`, platform `*Lwjgl3Launcher`
entry points, and the `Lwjgl3EditorToolLauncher` / `Lwjgl3RuntimeWindowLauncher` /
`Lwjgl3JvmProcessLauncher` helpers is intentional: it keeps startup/configuration local to each
platform and avoids a misleading shared launcher module. When changing duplicated
launcher/bootstrap files, review and synchronize the change across `desktop-lwjgl3-win`,
`desktop-lwjgl3-macos`, and `desktop-lwjgl3-linux`.

Each tool has a dedicated context file under `docs/agents/tools/`. Read it before changing
that tool.

### Asset Browser
`engine/tools/.../assetbrowser/AssetBrowserScene.kt` (+ shared asset infrastructure in `core/.../engine/assets/`). Default desktop tool. Scans/indexes project assets,
shows model metadata, and launches the right editor per asset via `AssetToolRegistry`.
→ `docs/agents/tools/asset-browser.md`

### Model Viewer
`engine/tools/.../modelviewer/ModelViewerScene.kt` (+ sibling files in `engine:tools`). Inspects a single model: mesh parts,
materials, texture channels, wireframe, material-debug modes, glTF PBR preview, bounds/grid/axes.
→ `docs/agents/tools/model-viewer.md`

### Animation Viewer
`engine/tools/.../animationviewer/AnimationViewerScene.kt` (+ sibling files in `engine:tools`). Plays model animation clips and
visualizes the skeleton/pose. → `docs/agents/tools/animation-viewer.md`

### Terrain Editor
`engine/tools/.../terraineditor/TerrainEditorScene.kt` (+ sibling editor files in `engine:tools`, shared terrain runtime in `core/.../engine/terrain/`). Generates and brush-edits heightfield
terrain with layers, material preview baking, and persistence. → `docs/agents/tools/terrain-editor.md`

### Scene Editor
`engine/tools/.../sceneeditor/SceneEditorScene.kt` (+ sibling editor files and bounds helpers in `engine:tools`). Composes engine scene documents
(`.krscene`): hierarchy, inspector, selection, gizmos, environment, asset panel.
→ `docs/agents/tools/scene-editor.md`

### UI Composer
`engine/tools/.../uicomposer/UiComposerScene.kt` (+ scene/layout/panel/operations/model helpers and preview adapter in `engine:tools`). Opens
`.krui` UiScene assets for validation-focused preview and inspection workflows. → `docs/agents/tools/ui-composer.md`

### Non-tool scenes
Scene Player (`engine:scene-player/.../ScenePlayerScene.kt`) is not an editor tool. It handles
`.krscene` playback through `ScenePlayerModule`, with `scene-player` as the preferred route and
`scene-viewer` / legacy `runtime-scene` preserved as aliases. It shares the same engine primitives as the tools. Woolboy now ships as the separate `games:woolboy` client module and
`apps:woolboy-desktop` app.

---

## 12. Main Classes and Interfaces

| Area | Class / Interface | Location | Responsibility |
|---|---|---|---|
| Routing (scene player) | `ScenePlayerModule` | `engine/scene-player/.../ScenePlayerModule.kt` | Backend-neutral route-to-scene factory for `.krscene` playback and legacy aliases. |
| Routing (desktop) | `DesktopMain` | `desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, `desktop-lwjgl3-linux` | Composes `ToolsModule` and `ScenePlayerModule` for desktop scene routing inside each platform launcher. |
| Entry (Windows desktop) | `WinLwjgl3Launcher` | `desktop-lwjgl3-win/.../WinLwjgl3Launcher.kt` | Windows LWJGL3 `main()` and startup policy handoff. |
| Entry (macOS desktop) | `MacOsLwjgl3Launcher` | `desktop-lwjgl3-macos/.../MacOsLwjgl3Launcher.kt` | macOS LWJGL3 `main()` and first-thread startup policy handoff. |
| Entry (Linux desktop) | `LinuxLwjgl3Launcher` | `desktop-lwjgl3-linux/.../LinuxLwjgl3Launcher.kt` | Linux LWJGL3 `main()` and NVIDIA startup policy handoff. |
| Entry (Android) | `AndroidLauncher` | `android/.../AndroidLauncher.kt` | Android entry point that creates `GdxEngineApplication` with `ScenePlayerModule`. |
| Backend bootstrap | `GdxEngineApplication` | `engine/backend-gdx/.../backend/gdx/GdxEngineApplication.kt` | libGDX `ApplicationAdapter` that boots runtime. |
| Runtime | `EngineRuntime` / `Engine` | `api/EngineRuntime.kt` | Owns scenes + services; implements `EngineContext`. |
| Facade | `EngineContext` | `api/EngineRuntime.kt` | Service locator passed to scenes. |
| Backend contract | `EngineBackend` | `api/EngineRuntime.kt` | Platform service contract. |
| Backend impl | `LibGdxBackend` | `engine/backend-gdx/.../backend/gdx/LibGdxBackend.kt` | Wires libGDX services. |
| Loop | `GameLoop` | `api/EngineRuntime.kt` | Fixed + variable step frame driver. |
| Scene | `Scene` / `SceneManager` | `api/Scene.kt` | Scene lifecycle + deferred stack. |
| ECS | `SceneWorld`, `Entity`, `System`, `CommandBuffer` | `api/Ecs.kt` | Entity/component/system model. |
| Render | `RenderCommand`, `Renderer`, `RenderCommandBuffer` | `api/Render.kt` | Backend-neutral draw layer. |
| Render impl | `GdxRenderer3D` + sibling helpers | `engine/backend-gdx/.../backend/gdx/GdxRenderer3D.kt` | Consumes commands via `ModelBatch` and delegates specialized backend rendering. |
| Assets | `AssetService`, `AssetRef`, `AssetPack` | `api/Assets.kt` | Typed asset handles + loading. |
| Assets impl | `GdxAssetService` + `GdxModelPoseSampler` | `engine/backend-gdx/.../backend/gdx/GdxAssetService.kt` | libGDX `AssetManager` loading, metadata, previews, and pose sampling. |
| Asset registry | `LocalAssetRegistryService` / `NoOpAssetRegistryService` | `assets/AssetRegistryService.kt` | Editor asset scan + `.krmeta` (desktop); no-op (Android). |
| Tasks | `TaskService` | `api/Tasks.kt` | Background/IO/main dispatch. |
| Logging | `Logger`, `LogService`, `EngineLogService` | `api/Debug.kt` | Lazy structured logging + sinks. |
| Diagnostics | `RuntimeStatsService`, `ProfilerService` | `api/Debug.kt` | Per-frame metrics + phase timings. |
| Events | `EventBus` | `api/Events.kt` | Synchronous typed dispatch. |
| Input | `InputService`, `InputSnapshot` | `api/Input.kt` | Frame-stable normalized input. |
| UI (editor) | `UiService`, `UiPanel`, `UiSystem` | `ui/editor/Ui.kt` | ImGui panel composition. |
| UI (runtime) | `RuntimeUiService` | `ui/runtime/RuntimeUiService.kt` | Scene2D runtime game UI. |
| Scene files | `SceneFileService` | `scene/SceneFileService.kt` | Text read/write abstraction. |
| Launchers | `EditorToolLauncher`, `RuntimeWindowLauncher` | `scene/` + platform desktop modules | Open tool/runtime windows. |

---

## 13. Architectural Patterns Used

Only patterns actually present in the code:

- **Service locator via `EngineContext`** — scenes pull services from `engine.*`.
- **Dependency injection of the backend** — `EngineRuntime(backend)`; backend chosen at startup.
- **ECS-lite** — `Entity` + `Component` (data) + `System` (behavior) + `SceneWorld`,
  with deferred mutation via `CommandBuffer`.
- **Scene-stack lifecycle** with deferred transitions (`SceneManager`).
- **Backend abstraction / ports-and-adapters** — core interfaces, `backend.gdx` adapters.
- **Command pattern for rendering** — `RenderCommand` + `RenderCommandBuffer` + `Renderer`.
- **ImGui panel composition** — `UiPanel` fun-interfaces aggregated by `UiSystem`.
- **Registry pattern** — `AssetToolRegistry`, `AssetImporterRegistry`, `LocalAssetRegistryService`.
- **Tool-per-process** — desktop tools launched as separate JVMs via system properties.
- **Snapshot/immutable hand-off** — background scans return immutable `AssetRegistrySnapshot`s.
- **State object + operations object per tool** — e.g. `ModelViewerState` + `ModelViewerOperations`.

---

## 14. Coding Rules for Agents

- Match the existing package and naming conventions. Use the exact tool/class names the code
  uses (`ModelViewerScene`, `AssetBrowserScene`, `TerrainEditorSystem`, ...).
- Keep core packages (`engine.api`, `render3d`, tool logic packages) **free of `com.badlogic.gdx`**.
- Components are data; systems are behavior. Do not put rendering or `Gdx.*` in components.
- Mutate the world via `world.createEntity`/`addEntity`/`removeEntity`; during system iteration,
  changes are auto-deferred through the `CommandBuffer` — rely on that, do not hand-edit the map.
- Add systems in `Scene.show()` in the order they must run; document non-obvious ordering.
- Prefer `data class` for components/state and keep them serialization-friendly.
- Follow Kotlin idioms already in the file (`also {}`, lazy logging lambdas, `require(...)`).
- Do not add or remove comments/KDoc unless the task is about documentation.

---

## 15. Logging Rules

See `docs/agents/logging.md` for detail. Conventions in code:

- Use the injected `engine.logger` (`Logger`), never `println` or `Gdx.app.log` from core/tools.
  (`println` appears only in the desktop launcher startup banner.)
- Each class defines a `private const val TAG = "ClassName"` in its `companion object` and uses it.
- Logging is **lazy**: `logger.info(TAG) { "message ${expensive()}" }` — the lambda only runs if
  the level is enabled.
- Levels: `Trace`, `Debug`, `Info`, `Warn`, `Error`. Use `warn`/`error` with the `Throwable`
  overload to attach exceptions.
- `LogService` (`EngineLogService`) buffers entries and fans out to sinks (`GdxAppLogSink`,
  `FileLogSink`). The shared `LogsPanel` renders `engine.logs` inside every tool.

---

## 16. UI Rules

- Editor panels implement `UiPanel` and are registered via `UiSystem.addPanel(...)` inside a
  scene's `createUiSystem(...)`. Do not call `ui.beginFrame`/`ui.endFrame` yourself — the
  `GameLoop` owns the frame boundary.
- Keep ImGui calls inside `engine.ui.editor` panels / the backend. Panels receive plain state
  objects and operation handlers; they should not reach into the renderer or `Gdx.*`.
- Use `ImGuiLayoutConfig` + `ImGuiLayoutConfigLoader` for window placement (with a fallback
  config and an asset path), and wrap panels with `ImGuiWindowEventLogger` for diagnostics
  as existing tools do.
- Runtime (in-game) UI belongs to `engine.ui.runtime` (Scene2D), not the ImGui editor stack.
- Texture previews go through `AssetService.texturePreviewHandle` →
  `UiContext.drawTexturePreview` using the opaque `TexturePreviewHandle` (never raw GL ids).

---

## 17. Backend Rules

- Only `engine.backend.gdx` may import `com.badlogic.gdx` / `net.mgsx.gltf` / OpenGL.
- Cross-boundary data must be backend-neutral (`AssetRef`, `RenderCommand`, `ModelAssetInfo`,
  `RuntimeTextureData`, `TexturePreviewHandle`, `ModelBonePose`, ...). Do not leak `ModelInstance`,
  `Texture`, `Pixmap`, `Camera`, etc. into core/tool code.
- New platform features go behind a core interface first, then a backend implementation.
- `AssetService.get()` intentionally throws in the LibGDX backend — keep core code on handles
  and backend-neutral accessors (`modelInfo`, `triangleCount`, `modelSkeleton`, ...).
- Editor-only GL work (e.g. terrain preview via `Pixmap`) must stay in editor/backend code and
  must not be required by the runtime path.

---

## 18. Refactoring Rules

- Preserve the core/backend boundary above all else. If a refactor needs a backend type in core,
  add a neutral abstraction instead of moving the import.
- Keep scene transitions deferred — never dispose/replace a scene mid-update/render.
- Keep world mutations going through the `CommandBuffer` during iteration.
- When touching a tool, change only that tool's package + its `game/*Scene.kt`; shared engine
  changes need wider review (they affect all tools).
- The scene lifecycle is `scheduleAssets` → `show`; if you reintroduce a `load`/`Preparing`
  stage, update `SceneManager`, this guide, and add tests — do not silently change lifecycle
  semantics.
- The core/backend boundary is enforced by `BackendBoundaryTest` (four rules: LibGDX imports,
  glTF imports, engine.api→backend, non-backend→backend). New violations fail the build.
- Run the JVM tests after engine/tool changes (`core:test`, `engine:scene-player:test`); they cover serialization,
  viewport, terrain runtime, scene player validation, scene editor systems, UI scene validation, and more.

---

## 19. Testing / Validation Rules

- Tests live in `core/src/test/kotlin` and are pure-JVM (no GL context). Examples:
  `RuntimeViewportTest`, `SceneSerializerTest`, `TerrainRuntimePipelineTest`,
  `SceneEditor*SystemTest`, `UiSceneSerializerTest`, `AssetBrowserSceneTest`,
  `ModelViewerTextureChannelResolverTest`. Scene Player tests live in `engine/scene-player/src/test/kotlin`.
- Prefer adding/adjusting tests in the same package as the code under test.
- Things requiring a real OpenGL context (renderer, ImGui, texture upload) are **not** unit
  tested — validate those manually by running the relevant scene.
- Useful Gradle commands (the README uses `.\gradlew.bat` on Windows; use `./gradlew` on macOS/Linux):
  - `./gradlew :core:compileKotlin :engine:tools:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin` — fast compile check.
  - `./gradlew :core:test :engine:scene-player:test` — run unit tests.
  - `./gradlew :desktop-lwjgl3-win:run` / `:desktop-lwjgl3-macos:run` / `:desktop-lwjgl3-linux:run` — run the default scene (Asset Browser) on the matching platform.
  - Run a specific tool/scene with system properties, e.g.
    `-Dkrender.scene=model-viewer -Dkrender.model.path=model/...` or
    `-Dkrender.scene=scene-player -Dkrender.scene.path=scenes/...`.

---

## 20. Anti-Patterns to Avoid

- Importing `com.badlogic.gdx` (or gdx-gltf) outside `engine.backend.gdx`.
- Calling `Gdx.*`, OpenGL, or `AssetManager` from scenes/systems/components.
- Rendering directly from a system instead of submitting a `RenderCommand`.
- Mutating `SceneWorld` entities directly during system iteration (bypassing `CommandBuffer`).
- Replacing/disposing scenes synchronously instead of via `SceneManager` deferred ops.
- Calling `ui.beginFrame`/`ui.endFrame` from a panel or scene.
- Using `println`/`Gdx.app.log` instead of `engine.logger` with a `TAG`.
- Assuming assets are loaded inside `show()` (they load asynchronously afterward).
- Doing blocking IO on the main thread instead of `TaskService.launchBackground` + `postToMain`.
- Leaking backend objects (`Texture`, `ModelInstance`, `Camera`) across the boundary.

---

## 21. Before Making Changes Checklist

1. Identify whether the change is **core**, **backend**, or a **single tool**. Read the matching
   doc (`docs/agents/...`) and the relevant `game/*Scene.kt`.
2. Confirm you are not introducing a `com.badlogic.gdx` import outside the backend.
3. For a tool change: open `docs/agents/tools/<tool>.md`, find the panels/systems/state involved,
   and follow that tool's "Safe Change Rules".
4. Keep components as data and behavior in systems; keep rendering as `RenderCommand`s.
5. Respect lifecycle: deferred scene transitions, `CommandBuffer` mutations, async asset loading.
6. Add or update a unit test in `core/src/test/kotlin` when logic is testable without GL.
7. Compile (`:core:compileKotlin :engine:tools:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin`) and run `:core:test :engine:scene-player:test`.
8. If behavior is GL/UI-dependent, run the scene with the matching platform module (`:desktop-lwjgl3-win:run`, `:desktop-lwjgl3-macos:run`, or `:desktop-lwjgl3-linux:run`) and the right system properties.
9. Update this guide and the relevant `docs/agents/*` file if you changed architecture.
