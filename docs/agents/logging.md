# Logging & Diagnostics — Agent Deep Dive

> Supplements `AGENTS.md` §15. Core types: `core/.../engine/api/Debug.kt`. Backend sinks:
> `core/.../engine/backend/gdx/` (`GdxAppLogSink`, `FileLogSink.kt`).

## Logger

- Use the injected `engine.logger` (`Logger`). It is the same object as `engine.logs`
  (`EngineLogService` implements both `LogService` and `Logger`).
- **Lazy by design:** the message is a lambda evaluated only if the level is enabled.
  ```kotlin
  logger.info(TAG) { "Loaded model '${ref.path}' tris=${expensiveCount()}" }
  logger.warn(TAG, error) { "Falling back: ${error.message}" }   // Throwable overload
  ```
- Levels (`LogLevel`): `Trace`, `Debug`, `Info`, `Warn`, `Error`. Convenience methods exist for
  each; `warn`/`error` take an optional `Throwable`.

## TAG convention

Every class that logs defines:
```kotlin
companion object { private const val TAG = "ClassName" }
```
and passes `TAG` to the logger. Use the class/feature name (e.g. `"ModelViewerScene"`,
`"TerrainEditor"`, `"AssetBrowserSystem"`, `"GdxAssetService"`).

## LogService + sinks

- `EngineLogService` buffers up to `maxEntries` (default 2000) `LogEntry`s, is thread-safe, filters
  by `minLevel`, and fans each accepted entry out to registered `LogSink`s.
- A `LogEntry` carries `level`, `tag`, `message`, `frame`, `threadName`, `timestampMillis`, and an
  optional `error`.
- Backend sinks: `GdxAppLogSink` (routes to `Gdx.app`) and `FileLogSink` (file output; failure to
  open it is logged and tolerated). Add sinks via `logs.addSink(...)`.
- The shared `LogsPanel` (`ui/editor/LogsPanel.kt`) renders `engine.logs` in every tool with
  auto-scroll.

## Frame diagnostics

- `RuntimeStatsService` (`FrameRuntimeStatsService`): per-frame key/value metrics. The loop seeds
  scene id/state, entity/command/job counts, and JVM memory each frame; `lastCompletedFrame`
  exposes a `RuntimeFrameSnapshot`.
- `ProfilerService` (`FrameProfilerService`): `measure(name) { ... }` records `PhaseTiming`s; the
  loop wraps each phase (`tasks.flush`, `assets.update`, `fixedUpdate`, `update`, `render.collect`,
  `render.submit`, ...). `lastCompletedFrame` exposes a `ProfileFrameSnapshot`.
- Tools surface these via panels (e.g. `TerrainEditorStatisticsPanel`).

## Rules & anti-patterns

- **Do** use `engine.logger` with a `TAG` and lazy lambdas.
- **Don't** use `println` or `Gdx.app.log` from core/tool code. (`println` is acceptable only in the
  desktop `Lwjgl3Launcher` startup banner.)
- **Don't** build expensive log strings eagerly outside the lambda.
- Prefer `debug`/`trace` for per-frame or high-frequency messages; reserve `info` for meaningful
  state changes. (Several viewers currently over-use `info` per frame — see `ARCHITECTURE_REVIEW.md`.)
- Attach the `Throwable` via the `warn`/`error` overload rather than interpolating a stack trace.
