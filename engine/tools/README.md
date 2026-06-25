# KRender Engine Tools

`engine:tools` contains KRender's standalone editor and development tools. These tools are built on top of the shared runtime services in `core` and are launched through the platform desktop host modules.

Full user-facing documentation is published in the hosted docs:

- Hosted documentation: [dmytro-pashko.github.io/KRender](https://dmytro-pashko.github.io/KRender)
- Tools documentation source: [docs/tools.md](../../docs/tools.md)

## Included Tools

- Asset Browser
- Texture Atlas Editor
- Model Viewer
- Animation Viewer
- Terrain Editor
- Scene Editor
- Skin Editor
- UI Composer

Scene Player is not part of `engine:tools`, but it is documented together with the tools because Asset Browser and Scene Editor can launch `.krscene` files through it.

## Build

Compile the tools module and its desktop hosts:

```sh
./gradlew :core:compileKotlin :engine:tools:compileKotlin :engine:scene-player:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-linux:compileKotlin
```

Run the main JVM checks used when changing tools:

```sh
./gradlew :core:test :engine:tools:test :engine:scene-player:test
```

## Run

Tool routes are selected with `krender.scene`. When running through `:desktop-lwjgl3-win:run`, `:desktop-lwjgl3-macos:run`, or `:desktop-lwjgl3-linux:run`, pass route properties with Gradle `-P` flags.

Examples:

```sh
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=asset-browser
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=model-viewer -Pkrender.model.path=model/example.glb
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=animation-viewer -Pkrender.model.path=model/example.glb
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=terrain-editor -Pkrender.terrain.path=terrain/example.krterrain
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=scene-editor -Pkrender.scene.path=scenes/example.krscene
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=texture-atlas-editor -Pkrender.atlas.path=ui/skins/default/uiskin.atlas
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=skin-editor -Pkrender.skin.path=ui/skins/xp_ui/xp-ui.json
./gradlew :desktop-lwjgl3-linux:run -Pkrender.scene=ui-composer -Pkrender.ui.scene.path=ui/example.krui
```

Skin Editor accepts an optional `krender.skin.path=<path>` property. When omitted, the tool starts in an empty/no-skin state until a skin path is provided.

Convenience launch scripts are available in `engine/tools/scripts/`:

- `asset_browser_launcher.sh`
- `model_viewer_launcher.sh`
- `animation_viewer_launcher.sh`
- `terrain_editor_launcher.sh`
- `scene_editor_launcher.sh`
- `ui_composer_launcher.sh`

Skin Editor currently has no dedicated convenience launch script; run it through the desktop host with `krender.scene=skin-editor` and optional `krender.skin.path=<path>`.
