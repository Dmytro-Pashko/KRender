# Scene Player — Agent Context

> Read `AGENTS.md` first. This file covers the standalone `.krscene` playback route.

## Purpose

Scene Player is the non-editor runtime/player route for `.krscene` files. It loads a serialized scene document, validates its dependencies, prepares runtime terrain and environment state, and plays the scene using the shared engine runtime without depending on editor-tool code.

## Current Implementation

- Module: `engine:scene-player`
- Scene: `engine/scene-player/.../ScenePlayerScene.kt`
- Builder: `engine/scene-player/.../ScenePlayerBuilder.kt`
- Routing: `engine/scene-player/.../ScenePlayerModule.kt`
- Entry point: `engine/scene-player/.../ScenePlayerMain.kt`
- Desktop composition: `lwjgl3/.../DesktopMain.kt`
- Supported routes:
  - `scene-player`
  - `scene-viewer`
  - `runtime-scene` legacy alias

## Main Files

| File | Responsibility |
|---|---|
| `engine/scene-player/.../ScenePlayerScene.kt` | Scene lifecycle, dependency scheduling, descriptor loading, runtime build logging. |
| `engine/scene-player/.../ScenePlayerBuilder.kt` | Applies scene descriptors to the world and wires runtime render/terrain/environment systems. |
| `engine/scene-player/.../ScenePlayerModule.kt` | Route-to-scene factory for player aliases. |
| `engine/scene-player/.../ScenePlayerMain.kt` | Standalone playback entry point used by Android and available to other launchers. |
| `core/.../engine/scene/SceneSerializer.kt` | Decodes and applies `.krscene` scene descriptors. |
| `core/.../engine/scene/RuntimeSceneValidator.kt` | Dependency validation and active-camera / active-terrain requirements. |
| `core/.../engine/terrain/RuntimeTerrainService.kt` | Runtime terrain preparation for active terrain entities. |

## Behavior Notes

- Keep playback behavior equivalent to the old `RuntimeScene`.
- Do not change `.krscene` schema, scene lifecycle, or terrain runtime file format here.
- `runtime-scene` must remain supported as an alias, but `scene-player` is the preferred route.
- Scene Player is intentionally separate from `engine:tools` so Android can depend on it without pulling editor tooling.

## Validation

```sh
./gradlew :core:compileKotlin :engine:scene-player:compileKotlin :lwjgl3:compileKotlin
./gradlew :core:test :engine:scene-player:test
```
