# KRender Engine Tools

`engine:tools` contains KRender's standalone editor and development tools. These tools are built on top of the shared engine/runtime services in `core` and are launched through the desktop `lwjgl3` application.

Dependency direction:

```text
engine:tools -> core
```

Tool routes are selected with `krender.scene`. When running through `:lwjgl3:run`, pass route properties with Gradle `-P` flags; the launcher forwards supported properties to JVM system properties. Asset paths are relative to the `assets/` working directory unless the current launcher documents otherwise.

## Tools

### Asset Browser

Browses, filters, inspects, creates, imports, and opens project assets with the registered editor tools.

Required properties:

- `krender.scene=asset-browser`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=asset-browser
```

### Model Viewer

Inspects a single model asset, including mesh parts, materials, texture debug views, bounds, grid/axis controls, and preview rendering modes.

Required properties:

- `krender.scene=model-viewer`
- `krender.model.path=<path>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=model-viewer -Pkrender.model.path=model/example.glb
```

### Animation Viewer

Inspects animation clips for a model, with playback controls and skeleton/pose visualization.

Required properties:

- `krender.scene=animation-viewer`
- `krender.model.path=<path>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=animation-viewer -Pkrender.model.path=model/example.glb
```

### Terrain Editor

Loads and edits terrain files with sculpting, painting, layer/material previews, save support, and diagnostics.

Required properties:

- `krender.scene=terrain-editor`
- `krender.terrain.path=<path>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=terrain-editor -Pkrender.terrain.path=terrain/example.krterrain
```

### Scene Editor

Creates, opens, edits, saves, and previews `.krscene` scene documents with hierarchy, inspector, placement, selection, gizmos, cameras, lights, and runtime preview support.

Required properties:

- `krender.scene=scene-editor`

Optional properties:

- `krender.scene.path=<path>`
- `krender.scene.name=<name>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=scene-editor -Pkrender.scene.path=scenes/example.krscene
```

### UI Composer

Opens `.krui` UI scene documents for validation, inspection, preview, structure edits, and document-focused UI workflows.

Required properties:

- `krender.scene=ui-composer`
- `krender.ui.scene.path=<path>`

Example:

```sh
./gradlew :lwjgl3:run -Pkrender.scene=ui-composer -Pkrender.ui.scene.path=ui/example.krui
```

## Validation

Use these checks after changing tool code:

```sh
./gradlew :core:compileKotlin :engine:tools:compileKotlin :lwjgl3:compileKotlin
./gradlew :core:test
```
