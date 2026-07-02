## Files inspected

- `docs/agents/rendering-pipeline.md`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentPreviewPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerScene.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerSystems.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/sceneeditor/SceneEditorSystems.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/viewport/EditorViewportCamera.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxRenderer3D.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfRenderer.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxHdrEnvironmentResolver.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/api/Render.kt`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentAssets.kt`

## Renderer path chosen

Use the existing shared glTF / PBR renderer path that already powers Model Viewer.

The chosen MVP path is:

1. Keep Environment Editor as a normal tool scene.
2. Render the visual preview in the scene background instead of building a new embedded render-to-texture viewport.
3. Reuse the existing test asset `assets/model/tests/MetalRoughSpheres.glb` for the sphere preview.
4. Apply the currently opened `.environment.json` manifest path to `GltfRendererSettings.environmentPreset`.
5. Add a simple neutral ground plane as dynamic geometry from the tool side.

## Can Material Spheres preview reuse existing PBR renderer now?

Yes.

Why this is viable now:

- `ModelViewerModelRenderSystem` already emits `DrawModel(..., gltfRenderer = GltfRendererSettings(...))`.
- `GdxGltfRenderer` already accepts an environment manifest path through `environmentPreset`.
- `HdrEnvironmentAssets.manifestPathForPreset(...)` explicitly supports direct `.json` paths.
- `GdxHdrEnvironmentResolver` already understands the new Environment manifest schema.
- Missing generated IBL maps already fall back safely through the existing gdx-gltf environment fallback path.
- The repository already contains `assets/model/tests/MetalRoughSpheres.glb`, which is suitable for an environment material preview MVP.

## Limitations

- The preview will render in the tool scene background, not in an embedded ImGui texture viewport.
- The first implementation will reuse the existing `MetalRoughSpheres.glb` layout rather than procedurally generating every sphere mesh through a new shared renderer abstraction.
- A simple tool-side neutral ground plane is still needed because the reused glTF preview asset does not provide the requested ground plane by itself.
- Final renderer unification for arbitrary procedural PBR preview rigs can remain follow-up work in this PR series.

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
