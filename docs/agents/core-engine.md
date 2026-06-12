# Core Engine — Agent Deep Dive

> Supplements `AGENTS.md` §5 / §8. Covers the runtime, game loop, scene lifecycle, and ECS.
> All paths are under `core/src/main/kotlin/com/pashkd/krender/engine/api/`.

## Files

| File | Contents |
|---|---|
| `EngineRuntime.kt` | `EngineConfig`, `EngineContext`, `EngineBackend`, `GameLoop`, `EngineRuntime` (`typealias Engine`). |
| `Scene.kt` | `SceneState`, `SceneLoadContext`, `Scene`, `SceneOp`, `SceneManager`. |
| `Ecs.kt` | `Component` + built-ins, `Entity`, `System`, `SystemPipeline`, `WorldCommand`, `CommandBuffer`, `SceneWorld`. |
| `Debug.kt` | Logging + diagnostics (see `logging.md`). |
| `Events.kt`, `Input.kt`, `Tasks.kt`, `Render.kt`, `Assets.kt`, `Math.kt` | Supporting subsystems. |

## EngineRuntime & EngineContext

- `EngineRuntime(backend, config)` implements `EngineContext` (the scene-facing service locator).
- It **owns** `SceneManager`, `EventBus`, `RuntimeUiService`, `RuntimeViewportService`, and
  delegates the rest of the services to the injected `EngineBackend`.
- `start(scene)` queues a `replace`, applies the transition, and resizes to the current window.
- `requestExit()` sets a flag; `completeExitIfRequested()` disposes + tells the backend to exit at
  safe points inside the loop.
- `resize(w, h)` reapplies the active scene's viewport policy (idempotent), then propagates to
  scenes, runtime UI, editor UI, and the renderer.

## GameLoop.renderFrame(rawDelta)

Order (from `GameLoop`):

1. `runtimeStats.beginFrame()`, `profiler.beginFrame(frame)`
2. `ui.beginFrame(delta)` — **before** input so ImGui capture flags are current
3. `input.beginFrame()` + `input.snapshot()`
4. `tasks.flushMainThreadQueue()`
5. `assets.update()` (budgeted async loading)
6. `scenes.applyPendingTransitions(runtime)` (+ `resize` if a transition applied)
7. fixed-step loop: `scene.fixedUpdate(fixedStepSeconds)` while `accumulator >= step`
8. `scene.update(delta)` (then exit check)
9. `scene.lateUpdate(delta)` (then exit check)
10. `runtimeUi.update(delta)`
11. `ui.endFrame()` — editor UI frame closed after panels draw
12. `scene.render(alpha)` + `scene.debugRender()` (collect commands)
13. `renderer.render(RenderContext(...))` (submit)
14. `scene.overlayRender()` (backend overlays, e.g. UI Composer Scene2D preview)
15. `runtimeUi.render()`, `ui.render()`
16. `finally`: ensure `ui.endFrame()`, `input.endFrame()`, `runtimeStats.endFrame`, `profiler.endFrame`

Delta is clamped to `config.maxFrameDeltaSeconds` (default `0.25`); fixed step defaults to `1/60`.

> The README's loop description is close but not exact (it predates `runtimeUi` and the profiler).
> This list reflects the current `GameLoop`.

## Scene Lifecycle

States (`SceneState`): `New`, `Loading`, `Preparing`, `Ready`, `Active`, `Paused`, `Disposing`,
`Disposed`.

`SceneManager.activateScene`:
```
attach(context) → setState(Loading) → scheduleAssets(assets) → setState(Ready)
→ stack.push → applySceneConfig(window+viewport) → show() → setState(Active)
```

> **Dead paths (verified):** `Scene.load(context)` is **never called**, and `SceneState.Preparing`
> is **never assigned**. Do not implement scene logic in `load()` expecting it to run; put setup in
> `show()` and tolerate assets that are still loading.

Transitions (`SceneOp`) are queued (`replace`/`push`/`pop`) and applied only in
`applyPendingTransitions` — never mid-update/render. `push` pauses the previous top; `pop`
reactivates the new top and reapplies its `SceneConfig`.

Teardown: `setState(Disposing)` → `hide()` → `dispose()` → `setState(Disposed)`. Base `dispose()`
flushes world commands.

## ECS-lite

- **Entity**: stable `EntityId`, a `ComponentContainer` (typed map keyed by `KClass`), `active`
  flag, and a back-reference to its `SceneWorld`. `entity.transform` lazily adds a
  `TransformComponent`.
- **Component**: marker interface; data only, no behavior, no backend deps.
- **System**: behavior; override `onAdded`/`fixedUpdate`/`update`/`lateUpdate`/`render`/`debugRender`.
- **SystemPipeline**: ordered list bound to one world; **runs systems in registration order**.
- **SceneWorld**: owns entities, the `SystemPipeline`, a `CommandBuffer`, and a
  `RenderCommandBuffer`. During phase execution it sets an `iterating` flag; while iterating,
  `addEntity`/`removeEntity` are auto-deferred to the command buffer and flushed at safe points
  (`fixedUpdate` start, `lateUpdate` end). `render()` clears render commands then collects.
- **Queries**: `query<A>()`, `query<A,B>()`, `query<A,B,C>()` filter active entities by component
  presence (allocating + scanning per call).

## Practical Rules

- Add systems in `Scene.show()` in the exact order they must execute.
- Never mutate the entity map directly during iteration — use `world.addEntity`/`removeEntity`
  (auto-deferred) or `world.commands`.
- Keep `Gdx.*` out of all of this — it is core, backend-neutral code.
- See `rendering-pipeline.md` for how `render()`/`debugRender()` commands reach the screen.
