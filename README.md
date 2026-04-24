# KRender SDK

KRender SDK is a Kotlin/libGDX project that is being shaped as a small game engine rather than a direct libGDX application. The current codebase keeps the engine core API separated from the LibGDX runtime backend, so gameplay and engine systems do not spread `Gdx.*` calls through the project.

## Architecture

```text
core
+-- engine
|   +-- api
|   |   +-- EngineRuntime / GameLoop
|   |   +-- SceneManager / Scene
|   |   +-- ECS-lite world model
|   |   +-- AssetService
|   |   +-- InputService
|   |   +-- DebugService / Logger
|   |   +-- EventBus
|   |   +-- TaskService
|   |   +-- RenderCommand layer
|   +-- render3d
|   |   +-- Camera, model, light, material components
|   |   +-- ModelRenderSystem
|   +-- backend
|       +-- gdx
|           +-- ApplicationAdapter bridge
|           +-- Gdx input/assets/render adapters
|           +-- Render-thread dispatcher
+-- game
    +-- DemoScene
```

The boundary rule is intentional:

- `engine.api` and `engine.render3d` are backend-neutral and should not import `Gdx` or `com.badlogic.gdx`.
- `engine.backend.gdx` is the adapter layer that owns LibGDX classes, OpenGL-bound objects, `AssetManager`, input processors, and rendering.

## Runtime Loop

`EngineRuntime` owns engine-wide services through `EngineContext`. `GameLoop` is called by the backend once per LibGDX `render()` frame and runs these phases:

1. `debug.beginFrame`
2. `input.beginFrame`
3. main-thread task queue flush
4. asset loading update
5. deferred scene transitions
6. fixed simulation updates at `1 / 60f`
7. variable `update`
8. `lateUpdate`
9. render command collection
10. backend render submission
11. `input.endFrame`
12. `debug.endFrame`

The loop clamps large frame deltas to avoid runaway simulation catch-up after stalls.

## Scenes

Scenes are engine-owned containers with:

- `SceneWorld`
- systems
- scene-local asset declarations through `AssetPack`
- lifecycle hooks
- scene state tracking

Scene transitions are deferred through `SceneManager`:

- `replace(scene)`
- `push(scene)`
- `pop()`

Deferred transitions prevent disposing or replacing a scene in the middle of update/render iteration.

Scene loading is structured as three stages:

- `load(context)`: CPU/IO-only preparation.
- `scheduleAssets(assets)`: queue typed `AssetRef` handles.
- `show()`: render-thread activation and final runtime binding.

## ECS-lite

The engine uses a lightweight component model:

- `Entity`
- `Component`
- `System`
- `SceneWorld`
- `CommandBuffer`
- typed `query` helpers

Components are plain data. Systems own behavior and participate in:

- `fixedUpdate`
- `update`
- `lateUpdate`
- `render`
- `debugRender`

World mutation during system iteration is deferred through `CommandBuffer`, keeping entity/component changes single-writer and predictable.

## Rendering

Systems do not render directly through LibGDX. They submit backend-neutral `RenderCommand` instances, such as `DrawModel`, to the scene render command buffer. The LibGDX backend consumes those commands and converts them to `ModelBatch`, camera, lighting, and material calls.

Current 3D support includes:

- perspective camera component
- model component
- transform component
- directional and ambient light components
- simple material descriptor
- shader pipeline descriptors as asset handles
- primitive cube demo rendering

## Services

Core services are exposed through `EngineContext`:

- `SceneManager`
- `AssetService`
- `InputService`
- `EventBus`
- `Logger`
- `DebugService`
- `TaskService`

`TaskService` separates background, IO, and render-thread work. Background jobs should return immutable results or post mutations back to the main/render queue.

## Demo

`DemoScene` creates:

- a perspective camera
- directional and ambient light
- a rotating primitive cube
- debug overlay entries for input/runtime state

The desktop launcher starts `Main`, which creates a `GdxEngineApplication` and boots the engine runtime with `DemoScene`.

## Modules

- `core`: engine API, LibGDX backend adapter, shared game code.
- `lwjgl3`: desktop launcher using LWJGL3.
- `android`: Android launcher. Requires Android SDK.
- `assets`: shared runtime assets.

## Build And Run

Run the desktop demo:

```powershell
.\gradlew.bat lwjgl3:run
```

Compile shared core and desktop launcher:

```powershell
.\gradlew.bat core:compileKotlin lwjgl3:compileKotlin
```

Build all modules:

```powershell
.\gradlew.bat build
```

Build a desktop runnable jar:

```powershell
.\gradlew.bat lwjgl3:jar
```
