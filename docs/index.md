# KRender SDK

KRender SDK is a Kotlin and libGDX engine workspace built around a backend-neutral core, a LibGDX runtime backend, a scene player, and a set of standalone editor tools.

This site is the user-facing documentation portal for the SDK and is published through GitHub Pages.

## Documentation Areas

- `Architecture` gives an overview of the runtime, module layout, and the core engine building blocks.
- `Tools` covers the standalone editor tools, scene-player workflow, and desktop launch options.
- `Git Workflow` documents branch strategy, feature PR checks, release promotion, and publishing.
- `Quality` collects notes about static analysis and test coverage.
- `AI Integration` explains how the repository provides structured context to coding agents through `AGENTS.md` and the `docs/agents/` directory.

## What KRender Includes

- A backend-neutral engine core for scenes, ECS-style systems, assets, rendering commands, and runtime services.
- A LibGDX backend implementation for desktop and Android runtime integration.
- Standalone editor tools for assets, models, animations, terrain, scenes, and UI documents.
- A scene-player route for `.krscene` runtime playback.
- A standalone Woolboy sample application that demonstrates the SDK as a client project.

## Key Features

- **Scene-based runtime** with support for scene loading, switching, stacking, and lifecycle management.
- **ECS-style architecture** for organizing scene data, entities, components, systems, and update pipelines.
- **Backend-independent engine core** with a shared `EngineContext` for accessing engine services.
- **Asset management system** for loading and working with models, textures, terrains, shaders, and related metadata.
- **Input abstraction layer** for keyboard, mouse, pointer state, actions, axes, and UI input capture.
- **Structured logging and diagnostics** with in-memory logs, file logging, runtime stats, profiling, and editor log panels.
- **Rendering pipeline** for models, terrain meshes, debug grids, axes, bounding boxes, wireframes, lights, and UI overlays.
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

The root Gradle conventions live in `build.gradle.kts`, which owns the shared JVM defaults, repositories, root `detekt`/`ktlint` wiring, and repository-wide static-analysis source coverage.

The root `assets/` directory remains the shared asset tree for the editor/runtime app. Woolboy ships its own curated runtime content from `games/woolboy/src/main/resources/assets/woolboy/`.

The desktop launcher modules intentionally duplicate a small amount of bootstrap code. Keeping platform launchers local to `desktop-lwjgl3-win`, `desktop-lwjgl3-macos`, and `desktop-lwjgl3-linux` makes startup and packaging behavior easier to inspect.

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

## Repository

- Source repository: [Dmytro-Pashko/KRender](https://github.com/Dmytro-Pashko/KRender)
- Main project overview: [README.md](https://github.com/Dmytro-Pashko/KRender/blob/master/README.md)
- Tools guide: [engine/tools/README.md](https://github.com/Dmytro-Pashko/KRender/blob/master/engine/tools/README.md)
