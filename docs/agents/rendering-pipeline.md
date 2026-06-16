# Rendering Pipeline — Agent Deep Dive

> Supplements `AGENTS.md` §7. Covers the render-command layer and the LibGDX renderer.
> Core types: `core/.../engine/api/Render.kt`. Backend entry point:
> `engine/backend-gdx/.../engine/backend/gdx/GdxRenderer3D.kt` (`GdxRenderer3D`) plus sub-renderers in the same
> package. Terrain rendering details now live in `docs/agents/tools/terrain-editor.md`
> and the shared runtime code under `core/.../engine/terrain/`.

## Principle

Systems **never** call libGDX/OpenGL. They submit backend-neutral `RenderCommand`s into
`world.renderCommands`. The backend `Renderer` consumes a sorted snapshot once per frame.

## Render commands (`Render.kt`)

`RenderCommand` (sealed) with an `Int sortKey`:

| Command | Purpose | Notable fields |
|---|---|---|
| `DrawModel` | File/primitive model instance | `model: AssetRef<ModelAsset>`, `transform`, `material`, `visibleMeshPartIndices`, `debugView: MaterialDebugView?`, `pbrPreview: PbrPreviewView?`, `animation: AnimationPlaybackView?` |
| `DrawDynamicModel` | Runtime-generated geometry (terrain) | `model: DynamicModel`, `material`, `runtimeTextures: List<RuntimeTextureData>` |
| `DrawLine` | World-space line | `from`, `to`, `color` |
| `DrawWorldGrid` | Editor grid | `halfExtentCells`, `cellSize`, `y`, `color` |
| `DrawWorldAxes` | Axis gizmo | `length`, `lineWidthPixels` |
| `DrawText` | Screen text | `text`, `position`, `color` |
| `ApplyEnvironment` | Skybox + ambient/env | `skyboxTexture`, `ambientColor`, intensities (default `sortKey = -100`, drawn first) |

Supporting data: `TransformSnapshot`, `Material` (`render3d`), `MaterialDebugView` +
`MaterialDebugMode`/`TextureDebugComponent`/`DebugCullingMode`/`MaterialDebugTextureRef`,
`PbrPreviewView`, `AnimationPlaybackView`, `DynamicMesh`/`DynamicModel`, `RuntimeTextureData`.

## Buffer + context

- `RenderCommandBuffer.submit(cmd)` appends; `clear()` empties; `snapshot()` returns the list
  **sorted by `sortKey`** (lower first, so `ApplyEnvironment` precedes models).
- `GameLoop` builds `RenderContext(scene, alpha, deltaSeconds, commands = snapshot())` and calls
  `backend.renderer.render(context)`.
- `Renderer` interface: `render(context)`, `resize(w,h)`, `dispose()`.

## Producing commands

Canonical producer — `ModelRenderSystem` (`render3d/Components3D.kt`):
```kotlin
world.query<TransformComponent, ModelComponent>().forEach { e ->
    world.renderCommands.submit(DrawModel(e.id, model.model, transform.snapshot(), model.material))
}
```
`WorldGridSystem` submits `DrawWorldGrid` + `DrawWorldAxes`. Tools add their own render systems
(e.g. `ModelViewerModelRenderSystem`, `TerrainRenderSystem`, `SceneEditorDocumentRenderSystem`).

## LibGDX renderer (`GdxRenderer3D`)

Per `render(context)`:
1. `prepareSceneFrame()` resets GL state (disable scissor/blend/cull, enable depth) to avoid ImGui
   state leaking into the 3D pass (documented anti-blink fix).
2. Clear color/depth; resolve the active `Camera` and `Environment` from the scene.
3. If an `ApplyEnvironment` command exists, render the skybox (`GdxSkyboxRenderer.kt`).
4. Render lines (`GdxLineShaderRenderer.kt`).
5. Iterate commands: route `DrawModel` to normal / wireframe / material-debug
   (`GdxModelViewerDebugRenderer.kt`) / glTF-PBR-preview (`GdxGltfPbrPreviewRenderer.kt`) paths; route
   `DrawDynamicModel` to dynamic/wireframe paths; uploads `runtimeTextures` first.
6. Draw deferred wireframe + overlay lines.

Shader sources used by the line and material-debug renderers live in `assets/shaders/` and are
loaded through `GdxShaderSources`.

Loaders: `.g3dj`/`.g3db` (`G3dModelLoader`), `.obj` (`ObjLoader`), `.gltf`/`.glb`
(gdx-gltf `GLTFAssetLoader`/`GLBAssetLoader`). Models batch through libGDX `ModelBatch` with a
`DefaultShaderProvider` (configurable `numBones` via `krender.gl.maxBones`).

## Caches

The renderer keeps **per-entity** caches (`ModelInstance`, `AnimationController`, glTF scenes,
primitives, dynamic models) keyed by `ModelCacheKey`. These are intentionally **separate** from
`GdxModelPoseSampler`'s asset-scoped pose-sampling caches, so animation/skeleton sampling never
mutates visible per-entity instances.

## Rules

- Add new visualizations by extending a `RenderCommand` (core) and its handling in the renderer —
  never by drawing from a system.
- Keep all GL/libGDX/gdx-gltf usage inside `GdxRenderer3D` and its sibling backend render helpers.
- Backend-neutral texture data crosses the boundary as `RuntimeTextureData` (pixels) +
  `MaterialTextureRef` (binding by id) or `TexturePreviewHandle` (opaque preview) — never raw GL ids.

## Testability

No GL in CI, so the renderer is not unit tested. You **can** test render systems at the
command-emission level (assert which `RenderCommand`s a system pushes given a world) without GL —
recommended for new render systems.
