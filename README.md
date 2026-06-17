# KRender SDK

KRender SDK is a Kotlin + libGDX engine workspace built around a backend-neutral core, a separate LibGDX runtime backend module, a dedicated scene player module, and standalone editor tools for assets, models, animations, terrain, scenes, and UI documents.

## Overview

KRender SDK is a small game-engine project written in Kotlin.

The main idea is to provide a lightweight runtime where different scenes can be created, loaded, edited, and rendered
using a reusable set of engine systems and services while keeping the engine core separate from the platform backend.

The project is focused on:

- learning how game engines are structured internally;
- experimenting with rendering, assets, scenes, terrain, models, and editor tools;
- building a Kotlin-first alternative to typical C# or C++ engine workflows;
- keeping the engine core small, modular, and backend-independent;
- supporting indie-style development where tools can grow together with the engine.

KRender provides a scene-based runtime, an ECS-style world model, a LibGDX backend, shared asset pipelines, and a set
of editor tools built on the same engine primitives. The repository now also contains a standalone Woolboy client split
into dedicated `games/` and `apps/` modules so the engine/SDK and a sample client application stay clearly separated.

## Key Features

- **Scene-based runtime** with support for scene loading, switching, stacking, and lifecycle management.
- **ECS-style architecture** for organizing scene data, entities, components, systems, and update pipelines.
- **Backend-independent engine core** with a shared `EngineContext` for accessing engine services.
- **Asset management system** for loading and working with models, textures, terrains, shaders, and related metadata.
- **Input abstraction layer** for keyboard, mouse, pointer state, actions, axes, and UI input capture.
- **Structured logging and diagnostics** with in-memory logs, file logging, runtime stats, profiling, and editor log
  panels.
- **Rendering pipeline**, including models, terrain meshes, debug grids, axes, bounding boxes, wireframes, lights, and
  UI overlays.
- **Scene Player** for runtime playback of `.krscene` scene documents.
- **Editor tools** for browsing assets and inspecting or authoring models, animations, terrain, scenes, and UI documents.

## Repository Structure

```text
KRender SDK/
+-- core/                  # Backend-neutral API/data, shared runtime services, serializers, terrain/runtime infrastructure
|   +-- src/main/kotlin/com/pashkd/krender/
|   |   +-- engine/
|   |   |   +-- api/                      # Backend-neutral runtime API
|   |   |   +-- assets/                   # Shared asset registry, metadata, import/export services
|   |   |   +-- render3d/                 # 3D components, environment, render systems
|   |   |   +-- scene/                    # Scene config, serialization, file and launcher services
|   |   |   +-- terrain/                  # Shared terrain runtime/persistence
|   |   |   +-- ui/editor/                # Editor UI service contracts and shared ImGui helpers
|   |   |   +-- ui/runtime/               # Scene2D runtime UI service
|   |   |   +-- ui/scene/                 # `.krui` model, serializer, validators
|   |   +-- game/                         # Shared game-facing helpers that are not standalone tool/player routes
|   +-- src/test/kotlin/...               # Pure JVM unit tests
+-- engine/
|   +-- backend-gdx/                      # LibGDX backend adapter; owns Gdx/OpenGL/gdx-gltf implementation code
|   +-- scene-player/                     # `.krscene` runtime player route and scene-player module docs
|   +-- tools/                            # Editor/development tools, tool routing, and editor-only helpers
+-- games/
|   +-- woolboy/                          # Standalone Woolboy gameplay/client module + bundled assets
+-- apps/
|   +-- woolboy-desktop/                  # Standalone Woolboy desktop launcher + executable JAR task
+-- desktop-lwjgl3-win/    # Windows desktop SDK host application
+-- desktop-lwjgl3-macos/  # macOS desktop SDK host application
+-- desktop-lwjgl3-linux/  # Linux desktop SDK host application
+-- android/               # Android launcher (requires Android SDK to build)
+-- assets/                # Shared assets for the main editor/runtime application
+-- docs/                  # Architecture notes, tool docs, screenshots, quality docs
```

Gradle subprojects currently loaded by `settings.gradle`:

- `core` - Backend-neutral engine API/data plus shared assets, scene, terrain, UI contracts, rendering commands, and serialization services.
- `engine:backend-gdx` - LibGDX backend adapter and all Gdx/OpenGL/gdx-gltf implementation code.
- `engine:scene-player` - Runtime route for loading and playing `.krscene` scene documents.
- `engine:tools` - Standalone editor/development tools for assets, models, animations, terrain, scenes, and UI documents.
- `desktop-lwjgl3-win` - Windows desktop SDK host application with Windows-local startup policy, runtime composition, secondary JVM launchers, and packaging.
- `desktop-lwjgl3-macos` - macOS desktop SDK host application with first-thread startup policy, runtime composition, secondary JVM launchers, and packaging.
- `desktop-lwjgl3-linux` - Linux desktop SDK host application with NVIDIA startup policy, runtime composition, secondary JVM launchers, and packaging.
- `android` - Android launcher for scene playback with Android-specific backend setup.
- `games:woolboy` - Standalone Woolboy gameplay/client module built on top of `core`.
- `apps:woolboy-desktop` - Desktop Woolboy application and executable JAR packaging module.

The root Gradle conventions now live in `build.gradle.kts`, which owns the shared JVM defaults,
repositories, root `detekt`/`ktlint` wiring, and repository-wide static-analysis source coverage.

The root `assets/` directory remains the shared asset tree for the editor/runtime app. Woolboy ships its own curated
runtime content from `games/woolboy/src/main/resources/assets/woolboy/`.

The desktop launcher modules intentionally duplicate a small amount of bootstrap code. Keeping
`DesktopMain`, `DesktopApplication`, the platform `*Lwjgl3Launcher` entry points, and secondary
JVM launchers local to each platform keeps startup/configuration easy to inspect and avoids a
misleading shared launcher module. When changing duplicated launcher files, review and synchronize
the update across `desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, and `desktop-lwjgl3-linux`.

The intended dependency direction is:

```text
engine:tools -> core
engine:scene-player -> core
engine:backend-gdx -> core
desktop-lwjgl3-win -> core + engine:backend-gdx + engine:tools + engine:scene-player
desktop-lwjgl3-macos -> core + engine:backend-gdx + engine:tools + engine:scene-player
desktop-lwjgl3-linux -> core + engine:backend-gdx + engine:tools + engine:scene-player
android -> core + engine:backend-gdx + engine:scene-player
games:woolboy -> core
apps:woolboy-desktop -> games:woolboy + core + engine:backend-gdx
```

```mermaid
flowchart LR
    Core["core"]
    BackendGdx["engine:backend-gdx"] --> Core
    Tools["engine:tools"] --> Core
    ScenePlayer["engine:scene-player"] --> Core
    DesktopWin["desktop-lwjgl3-win"] --> Core
    DesktopWin --> BackendGdx
    DesktopWin --> Tools
    DesktopWin --> ScenePlayer
    DesktopMac["desktop-lwjgl3-macos"] --> Core
    DesktopMac --> BackendGdx
    DesktopMac --> Tools
    DesktopMac --> ScenePlayer
    DesktopLinux["desktop-lwjgl3-linux"] --> Core
    DesktopLinux --> BackendGdx
    DesktopLinux --> Tools
    DesktopLinux --> ScenePlayer
    DesktopWin --> DesktopBackend["desktop backend libraries"]
    DesktopMac --> DesktopBackend
    DesktopLinux --> DesktopBackend
    Android["android"] --> Core
    Android --> BackendGdx
    Android --> ScenePlayer
    Woolboy["games:woolboy"] --> Core
    WoolboyDesktop["apps:woolboy-desktop"] --> Woolboy
    WoolboyDesktop --> Core
    WoolboyDesktop --> BackendGdx
```

## Architecture

KRender is organized around a small backend-facing runtime core:

```mermaid
flowchart TD
    Desktop["Platform Desktop LWJGL3 Host"] --> DesktopMain["DesktopMain"]
    DesktopMain --> ToolsModule["ToolsModule"]
    DesktopMain --> ScenePlayerModule["ScenePlayerModule"]

    AndroidLauncher["AndroidLauncher"] --> GdxApp["GdxEngineApplication"]
    GdxApp --> ScenePlayerModule
    ScenePlayerModule --> PlayerScene["Scene Player Scene"]

    ToolsModule --> ToolScene["Editor Tool Scene"]

    ToolScene --> Runtime["EngineRuntime"]
    PlayerScene --> Runtime

    Runtime --> Context["EngineContext"]
    Runtime --> Backend["EngineBackend"]
    Context --> Services["Assets / Scene Files / Input / UI / Logs / Tasks / Stats / Profiler"]
    Runtime --> Renderer["Renderer"]
    Backend --> Renderer
    Renderer --> Gdx["LibGDX / OpenGL"]
```

`EngineRuntime` owns the game loop and exposes backend services through `EngineContext`. Scenes and systems use the
context instead of directly constructing backend services.

## Engine Components

| Component                | Responsibility                                                                                                                                                                   | Location                                                      |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `EngineRuntime`          | Starts the runtime, owns `SceneManager`, advances frames, resizes, disposes services, and exposes `EngineContext`.                                                               | `engine/api/EngineRuntime.kt`                                 |
| `EngineContext`          | Stable facade for scenes and systems to access scenes, assets, scene files, runtime/tool launchers, input, UI, events, logging, logs, stats, profiler, tasks, and exit requests. | `engine/api/EngineRuntime.kt`                                 |
| `EngineBackend`          | Backend contract implemented by platform/runtime integrations.                                                                                                                   | `engine/api/EngineRuntime.kt`                                 |
| `SceneManager`           | Deferred scene stack transitions and scene activation/disposal.                                                                                                                  | `engine/api/Scene.kt`                                         |
| `Scene`                  | Base runtime scene with `world`, required assets, lifecycle hooks, and ECS forwarding.                                                                                           | `engine/api/Scene.kt`                                         |
| `SceneWorld`             | Entity storage, system pipeline, deferred mutation buffer, render command buffer, and typed queries.                                                                             | `engine/api/Ecs.kt`                                           |
| `Entity` / `Component`   | Runtime data model. Components include `TransformComponent`, `NameComponent`, `ParentComponent`, `VelocityComponent`, `LifetimeComponent`, and domain-specific components.       | `engine/api/Ecs.kt`                                           |
| `System`                 | Behavior unit with `onAdded`, `fixedUpdate`, `update`, `lateUpdate`, `render`, and `debugRender`.                                                                                | `engine/api/Ecs.kt`                                           |
| `AssetService`           | Schedules, updates, checks, inspects, previews, and unloads typed assets.                                                                                                        | `engine/api/Assets.kt`, `engine/backend-gdx/.../LibGdxBackend.kt` |
| `InputService`           | Frame-stable normalized input snapshots and cursor capture.                                                                                                                      | `engine/api/Input.kt`, `engine/backend-gdx/.../LibGdxBackend.kt`  |
| `UiService` / `UiSystem` | ImGui frame lifecycle, capture state, panel drawing, and texture preview drawing.                                                                                                | `engine/ui/editor/Ui.kt`, `engine/backend-gdx/.../GdxImGuiService.kt`    |
| `Logger` / `LogService`  | Structured log entries, levels, history, sinks, and panels.                                                                                                                      | `engine/api/Debug.kt`, `engine/ui/editor/LogsPanel.kt`               |
| `TaskService`            | Coroutine-based background, IO, main/render queue, and in-flight job tracking.                                                                                                   | `engine/api/Tasks.kt`, `engine/backend-gdx/.../LibGdxBackend.kt`  |
| `Renderer`               | Backend render submission for collected `RenderCommand` instances.                                                                                                               | `engine/api/Render.kt`, `engine/backend-gdx/.../LibGdxBackend.kt` |
| `SceneSerializer`        | Encodes and decodes `.krscene` scene descriptors and applies them to `SceneWorld`.                                                                                               | `engine/scene/SceneSerializer.kt`                             |

## Scene Lifecycle

The base `Scene` has lifecycle hooks for asset scheduling, showing, updating, rendering, resizing, hiding, and
disposal.
The `SceneManager` defers scene transitions until the end of the current frame to avoid mid-frame state changes.
Effective activation is `scheduleAssets` then `show`; assets keep loading asynchronously afterwards, so scenes must
tolerate assets that are not ready yet.

```kotlin
open fun scheduleAssets(assets: AssetService)
open fun show()
open fun fixedUpdate(dt: Float)
open fun update(dt: Float)
open fun lateUpdate(dt: Float)
open fun render(alpha: Float)
open fun debugRender()
open fun resize(width: Int, height: Int)
open fun hide()
open fun dispose()
```

## Game Loop

`GdxEngineApplication.render()` calls `EngineRuntime.renderFrame(Gdx.graphics.deltaTime)`. `GameLoop` clamps large frame
deltas with `EngineConfig.maxFrameDeltaSeconds` and runs a fixed-step accumulator using
`EngineConfig.fixedStepSeconds` (`1 / 60f` by default).

Current frame order:

1. Begin runtime stats and profiler collection for the frame.
2. Begin the ImGui frame.
3. Process input and capture the current input state.
4. Execute pending main-thread tasks.
5. Advance asset loading.
6. Apply pending scene transitions and resize if needed.
7. Run fixed updates if required.
8. Run the main scene update.
9. Run late update logic.
10. Update runtime UI.
11. End the ImGui frame.
12. Collect render and debug render commands from the active scene.
13. Submit the render context to the backend renderer.
14. Run scene overlay rendering.
15. Render runtime UI, then render editor UI.

Simplified pseudocode:

```kotlin
backend.runtimeStats.beginFrame()
backend.profiler.beginFrame(frame)
backend.ui.beginFrame(delta)
backend.input.beginFrame()
backend.input.snapshot()
backend.tasks.flushMainThreadQueue()
backend.assets.update()

runtime.scenes.applyPendingTransitions(runtime)
val scene = runtime.scenes.currentScene ?: return

while (accumulator >= fixedStep) {
    scene.fixedUpdate(fixedStep)
    accumulator -= fixedStep
}

scene.update(delta)
scene.lateUpdate(delta)
runtime.runtimeUi.update(delta)

backend.ui.endFrame()
scene.render(alpha)
scene.debugRender()

backend.renderer.render(
    RenderContext(scene, alpha, delta, scene.world.renderCommands.snapshot())
)

scene.overlayRender()
runtime.runtimeUi.render()
backend.ui.render()
backend.input.endFrame()
backend.runtimeStats.endFrame(delta, fixedUpdates)
backend.profiler.endFrame(frame)
```

## Tools

KRender's editor and development tools live in `engine:tools`. They include Asset Browser, Model Viewer, Animation Viewer, Terrain Editor, Scene Editor, and UI Composer.

Detailed tool descriptions and run commands are documented in [engine/tools/README.md](engine/tools/README.md).

Convenience launch scripts live under `engine/tools/scripts/`:

- `asset_browser_launcher.sh` - launches the Asset Browser.
- `model_viewer_launcher.sh [model/path.glb]` - launches the Model Viewer, defaulting to `model/wool_boy_animated.glb`.
- `animation_viewer_launcher.sh [model/path.glb]` - launches the Animation Viewer, defaulting to `model/wool_boy_animated.glb`.
- `terrain_editor_launcher.sh [terrains/file.json]` - launches the Terrain Editor, defaulting to `terrains/terrain_02_small_flat.json`.
- `scene_editor_launcher.sh [scenes/file.krscene]` - launches the Scene Editor, optionally opening a scene file.
- `ui_composer_launcher.sh [ui/scenes/file.krui]` - launches the UI Composer, defaulting to `ui/scenes/test_scene_01.krui`.

## AI-Oriented Development

KRender is maintained with AI and coding-agent collaboration in mind.

- Repository-wide guidance lives in `AGENTS.md`.
- Deeper architecture and subsystem docs live under `docs/agents/`.
- Tool-specific context files live under `docs/agents/tools/`.
- Agents should read the relevant tool or subsystem doc before making non-trivial changes.

## Example Apps

### Woolboy

Woolboy is now packaged as a **standalone client application** on top of KRender SDK instead of an in-core sandbox
scene.

Module split:

- `core` — KRender backend-neutral engine API/data and shared services
- `engine:backend-gdx` — LibGDX backend adapter
- `games:woolboy` — Woolboy gameplay/client module
- `apps:woolboy-desktop` — executable desktop app and fat-JAR task

Woolboy runtime assets live in:

```text
games/woolboy/src/main/resources/assets/woolboy/
```

Build the executable JAR:

```powershell
.\gradlew.bat :apps:woolboy-desktop:woolboyJar
```

Run it:

```powershell
java -jar apps/woolboy-desktop/build/libs/woolboy-demo.jar
```

The Woolboy app bundles its curated `assets/woolboy` runtime content inside the JAR and does not require
`-Dkrender.scene=...`. See `games/woolboy/woolboy.md` for the app-specific layout and build notes.

Run the SDK desktop host from IntelliJ IDEA:

![Run SDK from IntelliJ IDEA](docs/images/run_sdk_intelij_idea.png)

Run Woolboy from IntelliJ IDEA:

![Run Woolboy from IntelliJ IDEA](docs/images/run_woolboy_intelij_idea.png)

## Example: Creating a Scene

This example uses the current `Scene`, `AssetRef`, `SceneWorld`, component, and system APIs.

```kotlin
package com.example

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Component
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem

data class RotationComponent(
    var degreesPerSecond: Float = 45f,
) : Component

class RotationSystem(
    private val logger: Logger,
) : System() {
    override fun onAdded(world: SceneWorld) {
        logger.info(TAG) { "RotationSystem added" }
    }

    override fun update(world: SceneWorld, dt: Float) {
        world.query<TransformComponent, RotationComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val rotation = entity.get<RotationComponent>() ?: return@forEach
            transform.eulerDegrees.y += rotation.degreesPerSecond * dt
        }
    }

    companion object {
        private const val TAG = "RotationSystem"
    }
}

class SpinningModelScene(
    private val modelPath: String,
) : Scene("spinning_model") {
    private val model = AssetRef.model(modelPath)

    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOf(model)
        },
    )

    override fun show() {
        val entity = world.createEntity("Spinning Model")
        entity.add(ModelComponent(model = model, material = Material()))
        entity.add(RotationComponent(degreesPerSecond = 30f))

        world.systems.add(RotationSystem(engine.logger))
        world.systems.add(ModelRenderSystem())
    }
}
```

## Example: Creating a Custom System

Systems are added to `SceneWorld.systems` and receive the world plus phase timing. Use constructor injection for
services such as `Logger`, `InputService`, or `AssetService`.

```kotlin
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.VelocityComponent

class VelocitySystem(
    private val logger: Logger,
) : System() {
    override fun onAdded(world: SceneWorld) {
        logger.debug(TAG) { "VelocitySystem added to world with ${world.all().size} entities" }
    }

    override fun fixedUpdate(world: SceneWorld, dt: Float) {
        world.query<TransformComponent, VelocityComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val velocity = entity.get<VelocityComponent>() ?: return@forEach

            transform.position.x += velocity.value.x * dt
            transform.position.y += velocity.value.y * dt
            transform.position.z += velocity.value.z * dt
        }
    }

    companion object {
        private const val TAG = "VelocitySystem"
    }
}
```

## Logging

Logging is implemented in `engine/api/Debug.kt`.

Current types:

- `LogLevel`: `Trace`, `Debug`, `Info`, `Warn`, `Error`.
- `LogEntry`: structured log event with level, tag, message, frame, thread name, timestamp, and optional error.
- `Logger`: lazy message API with `trace`, `debug`, `info`, `warn`, and `error`.
- `LogService`: in-memory recent log history with `minLevel`, clear, sink registration, and sink removal.
- `EngineLogService`: default in-memory implementation and logger.
- `LogSink`: sink abstraction.
- `GdxAppLogSink`: mirrors structured logs to LibGDX application logging.
- `FileLogSink`: writes session-scoped log files under `logs/` relative to the current working directory.
- `LogsPanel`: ImGui panel used by Asset Browser, Model Viewer, Animation Viewer, Terrain Editor, Scene Editor, and UI Composer.

`LibGdxBackend` creates one `EngineLogService`, exposes it as both `logger` and `logs`, and registers the LibGDX and
file sinks.

Example:

```kotlin
engine.logger.info("MyScene") { "Scene started with ${world.all().size} entities" }
engine.logger.warn("Assets") { "Asset metadata is not available yet" }
engine.logger.error("Runtime", error) { "Failed to load scene: ${error.message}" }
```

## Git Workflow

KRender uses a two-branch development and release flow with short-lived feature branches:

- `develop` - default protected development branch.
- `master` - stable release branch.
- `feature/<feature-name>` - short-lived feature branches created for isolated work.

### Feature PR Flow

Feature work should branch from `develop` and return to `develop` through pull requests.

1. Create `feature/<feature-name>` from `develop`.
2. Implement the change on the feature branch.
3. Open a pull request targeting `develop`.
4. Merge only after the pull request passes CI and review.

Feature pull requests should pass:

- static analysis;
- lint / ktlint;
- formatting check;
- compile checks;
- `core` and `engine:scene-player` unit tests.

Feature pull requests do not require a full release build.

### Release Flow

Releases are promoted from `develop` into `master`.

1. Open a release pull request from `develop` into `master`.
2. Run the full release validation set on the release pull request.
3. Merge the release pull request into `master`.
4. Create a version tag on `master` using the format `vX.Y.Z`.
5. Let the version tag trigger the GitHub Release workflow.

Release pull requests should pass:

- full compile;
- tests;
- static analysis;
- docs build;
- desktop artifacts build.

### Distribution

- GitHub Releases publish desktop binaries, zip bundles, and demo artifacts.
- GitHub Packages publish Gradle engine modules for consumption outside this repository.

### Documentation

- The documentation portal is planned to use MkDocs.
- The published docs site is intended to deploy through GitHub Pages.
- Docs source and portal structure will be created in a follow-up task.

### Repository Setup

Manual GitHub repository configuration for this workflow:

1. Create `develop` from the current `feature/v2` branch.
2. Set `develop` as the default branch.
3. Protect `develop`.
4. Protect `master`.
5. Require pull requests before merging into `develop` and `master`.
6. Require status checks before merging.
7. Disallow force pushes and branch deletion on protected branches.
8. Configure GitHub Pages to use GitHub Actions as the source.
9. Configure package publishing permissions for release workflows.

## Getting Started

### Requirements

- JDK 11 or newer for normal development.
- Android SDK for the `android` module / full multi-project builds.
- IntelliJ IDEA is recommended for Kotlin/Gradle development.

### Build

Fast JVM compile check on Windows:

```powershell
.\gradlew.bat :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin
```

Run JVM tests:

```powershell
.\gradlew.bat :core:test :engine:scene-player:test
```

Build the standalone Woolboy modules only:

```powershell
.\gradlew.bat :games:woolboy:build :apps:woolboy-desktop:build
.\gradlew.bat :apps:woolboy-desktop:woolboyJar
```

Full workspace build:

```powershell
.\gradlew.bat build
```

The full workspace build includes the `android` module and may require a configured Android SDK.

On Linux/macOS:

```bash
./gradlew :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin
./gradlew :core:test :engine:scene-player:test
./gradlew build
```

### Quality Scripts

Quality scripts live under `scripts/` and can be run from the repository root:

```bash
./scripts/format_check.sh
./scripts/static_analysis.sh
./scripts/unit_test_coverage.sh
./scripts/full_report.sh
```

Safe Kotlin formatting plus verification:

```bash
./scripts/format_check.sh --fix
```

`full_report.sh` runs formatting checks, static analysis, and unit test coverage. Reports are written under `build/reports/`, including `build/reports/static-analysis/`, `build/reports/unit-test-coverage/`, `build/reports/full-report/`, and `build/reports/detekt/`. The legacy `scripts/static-analysis.sh` wrapper remains available for compatibility.

## License

KRender is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
