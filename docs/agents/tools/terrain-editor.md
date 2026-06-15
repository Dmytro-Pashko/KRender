# Terrain Editor — Agent Context

> Read `AGENTS.md` and `docs/terrain-rendering.md` first. This file covers only the Terrain
> Editor tool.

## Purpose

Generate and brush-edit heightfield terrain: choose a generator, sculpt with brushes, manage
material layers, preview blended materials, and persist the result to a terrain file. The
authoritative reference for the rendering side is `docs/terrain-rendering.md`.

## Current Implementation

- Scene: `engine/tools/.../engine/tools/terraineditor/TerrainEditorScene.kt`.
- Tool internals: `engine/tools/.../engine/tools/terraineditor/` plus shared runtime terrain infrastructure in `core/.../engine/terrain/`.
- Launched as a separate JVM window by `Lwjgl3EditorToolLauncher.launchTerrainEditor(path)`
  (`krender.scene=terrain-editor` or `terrain-generator`, `krender.terrain.path=<path>`).
- Requires an existing terrain file — the scene fails fast if the path is missing/unreadable.

## Main Files

| File | Responsibility |
|---|---|
| `engine/tools/terraineditor/TerrainEditorScene.kt` | Builds camera/lights/terrain entity, generators, material library, systems, panels. |
| `engine/terrain/TerrainData.kt` | Editable heightfield + layers + weights + materials (backend-neutral). |
| `engine/terrain/TerrainComponents.kt` | `TerrainDataComponent`, `TerrainRendererComponent`, camera controller component. |
| `engine/terrain/TerrainSystems.kt` | `TerrainEditorSystem`, `TerrainEditorMeshSyncSystem`, `TerrainRenderSystem`, camera controller system. |
| `engine/terrain/TerrainBrush.kt` | Brush stroke model. |
| `engine/terrain/TerrainGenerators.kt` | `Flat`, `Perlin`, `Simplex`, `Fractal` generators. |
| `engine/terrain/TerrainEditHistory.kt` | Undo/redo history. |
| `engine/terrain/TerrainMesh.kt` | `TerrainMeshBuilder` → backend-neutral `DynamicMesh`/`DynamicModel`. |
| `engine/terrain/TerrainPersistence.kt` | Encode/decode terrain files + descriptors. |
| `engine/terrain/TerrainMaterialPreviewBaker.kt` | Editor-only `Pixmap`-based preview bake (allowed in editor). |
| `engine/terrain/RuntimeTerrainService.kt` | Runtime (non-editor) terrain loading/baking. |
| `engine/tools/terraineditor/TerrainEditorPanels.kt`, `TerrainEditorState.kt` | Panels + editor state. |

## Main Classes

| Class | Responsibility |
|---|---|
| `TerrainEditorScene` | Composition; loads terrain file, material library, generators. |
| `TerrainEditorState` | Editor state: brush, layers, preview mode, persistence, stats. |
| `TerrainEditorSystem` | Input, brush strokes, layer editing, history, persistence, UI sync. |
| `TerrainEditorMeshSyncSystem` | Editor adapter around `TerrainMeshBuilder` + preview material bake. |
| `TerrainRenderSystem` | Selects active terrain texture and emits `DrawDynamicModel`. |
| `TerrainMaterialLibrary` | Loads `materials/terrain_materials.json`. |

## UI Panels

`TerrainEditorStatisticsPanel`, `TerrainEditorTerrainPanel`, `TerrainEditorBrushPanel`,
`TerrainEditorLayersPanel`, `TerrainEditorControlsPanel`, `LogsPanel`.

## Engine Services Used

`engine.input` (brush + camera), `engine.logger`/`engine.logs`, `engine.runtimeStats` +
`engine.profiler` (stats panel), `engine.ui`, and `terrainTextureSamplerFactory` (backend,
for sampling material albedo during runtime bake).

## Data Flow

System order: `TerrainCameraControllerSystem → TerrainEditorSystem → TerrainEditorMeshSyncSystem
→ UiSystem → TerrainRenderSystem`.

1. `TerrainEditorSystem` applies brush/layer/generator edits to `TerrainDataComponent`.
2. `TerrainEditorMeshSyncSystem` rebuilds the dynamic mesh (with editor preview vertex colors)
   and bakes editor preview textures (`RuntimeTextureData`), tracking bake stats.
3. `TerrainRenderSystem` emits `DrawDynamicModel` carrying the active texture + material.
4. The backend uploads `runtimeTextures` and draws the dynamic model.

## Lifecycle

`show()` loads the terrain file (`require` exists), the material library, builds state and the
five systems, then creates camera, lights, and the terrain entity. `dispose()` disposes
`meshSyncSystem`. Uses `SceneConfigPresets.EditorTool`.

## Supported Asset Types

Terrain files (default extension `.json` for terrain via Asset Browser create flow; loaded via
`TerrainPersistence`). Terrain material library JSON (`materials/terrain_materials.json`).
**Terrain is not loaded through `AssetService`** — it has its own persistence/runtime path.

## Current Features

- Four generators (Flat, Perlin, Simplex, Fractal).
- Brush sculpting with undo/redo history.
- Multiple material layers with weights, blend mode, and layer color preview.
- Editor preview modes (layer color, material color, material texture, selected-layer mask).
- Material preview bake with stats + PNG export.
- Save by name to the terrain file.

## Missing / Incomplete Features

(From `docs/terrain-rendering.md`.) No chunking, LOD, frustum culling, GPU splatting, terrain
shader, normal/roughness/metallic blending, texture arrays, collision, or streaming. The final
material is CPU-baked. Suitable for prototype scenes, not large open worlds.

## Known Problems

- Final material is a CPU-baked baseColor texture, not a real GPU splat/control map.
- `TerrainEditorMeshSyncSystem` is wired with many provider/sink lambdas in the scene — a large,
  intricate constructor that is easy to misconfigure.
- Editor preview baking uses `Pixmap` (acceptable, editor-only) but must never become a runtime
  dependency.

## Extension Points

- Add a generator: implement `TerrainGenerator` and add it to `terrainGenerators` in the scene.
- Add a preview mode: extend `TerrainEditorMeshSyncSystem` preview handling + a panel toggle.
- Add a material: extend `materials/terrain_materials.json` / `TerrainMaterialLibrary`.
- Move toward GPU splatting by extending `DrawDynamicModel`/`RuntimeTextureData` + backend.

## Safe Change Rules

- Keep shared terrain logic (`TerrainData`, `TerrainMeshBuilder`, runtime bake) backend-neutral;
  only editor preview baking may touch `Pixmap`, and only in editor code.
- Runtime terrain rendering must not depend on `TerrainMaterialPreviewBaker`.
- Runtime texture `id`s must be unique per terrain entity (backend caches key on id) — use the
  existing id generator.
- Respect the documented system order; mesh sync must run before render.

## Recommended Improvements

- Real GPU splat shader + weight/control maps (replace CPU bake for runtime).
- Chunking + LOD + frustum culling for larger terrains.
- Simplify `TerrainEditorMeshSyncSystem` wiring (config object instead of many lambdas).

## Related Code Patterns

- Runtime counterpart: `ScenePlayerScene` uses `RuntimeTerrainMeshSystem` + `TerrainRenderSystem`
  (see `docs/terrain-rendering.md`).
- Dynamic mesh rendering: `DrawDynamicModel` + `RuntimeTextureData` in `api/Render.kt`.
