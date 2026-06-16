# KRender Scene Player

`engine:scene-player` contains the standalone `.krscene` playback route for KRender. It is separate from `core` because `core` stays focused on shared engine/runtime infrastructure, and it is separate from `engine:tools` because Scene Player is not an editor tool.

Dependency direction:

```text
engine:scene-player -> core
```

## Routes

- `scene-player` preferred route
- `scene-viewer`
- `runtime-scene` legacy alias

All routes require:

- `krender.scene.path=<path>`

Example:

```sh
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=scene-player -Pkrender.scene.path=scenes/example.krscene
```

Legacy alias:

```sh
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=runtime-scene -Pkrender.scene.path=scenes/example.krscene
```

Paths are relative to the `assets/` working directory unless the current launcher documents otherwise.

`runtime-scene` is kept only as a legacy route alias. The preferred route name for new docs, launchers, and examples is `scene-player`.

## Validation

```sh
./gradlew :core:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin
./gradlew :core:test :engine:scene-player:test
```
