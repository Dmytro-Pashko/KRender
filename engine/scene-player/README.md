# KRender Scene Player

`engine:scene-player` contains the standalone `.krscene` playback route for KRender. It is separate from `core` because `core` stays focused on shared engine/runtime infrastructure, and it is separate from `engine:tools` because Scene Player is not an editor tool.

Dependency direction:

```text
engine:scene-player -> core
```

## Routes

- `scene-player`
- `scene-viewer`
- `runtime-scene` legacy alias

All routes require:

- `krender.scene.path=<path>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=scene-player -Pkrender.scene.path=scenes/example.krscene
```

Legacy alias:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=runtime-scene -Pkrender.scene.path=scenes/example.krscene
```

Paths are relative to the `assets/` working directory unless the current launcher documents otherwise.

## Validation

```sh
./gradlew :core:compileKotlin :engine:scene-player:compileKotlin :lwjgl3:compileKotlin
./gradlew :core:test :engine:scene-player:test
```
