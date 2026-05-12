# Terrain Rendering Pipeline

## Overview

Terrain rendering is split into shared core, editor adapter, and runtime
pipeline. Shared terrain code stays backend-neutral and submits render commands;
LibGDX-specific texture upload and drawing remain in the backend.

## Shared Core

- `TerrainData`: editable heightfield, terrain layers, layer weights, material ids, visibility, colors, and tiling.
- `TerrainMeshBuilder`: converts `TerrainData` into backend-neutral dynamic mesh data or a `DynamicModel`.
- `TerrainMaterialBakeService`: creates runtime CPU-baked final baseColor/albedo `RuntimeTextureData` from terrain layers.
- `TerrainRendererComponent`: stores generated terrain model state, editor preview texture state, and runtime final material state.
- `TerrainRenderCommands`: selects the active terrain texture and emits `DrawDynamicModel`.

## Editor Pipeline

`TerrainEditorScene`:

`TerrainEditorSystem -> TerrainEditorMeshSyncSystem -> TerrainRenderSystem`

`TerrainEditorSystem` owns editor workflow: input, brush strokes, layer editing,
history, persistence commands, and UI state synchronization.

`TerrainEditorMeshSyncSystem` is the editor adapter around shared mesh building.
It rebuilds terrain dynamic models with editor preview vertex colors when needed
and owns editor-only material preview work:

- `previewDiffuseTexture` is an editor-only runtime texture payload used for Terrain Editor preview modes.
- Editor preview modes include layer color, material color, material texture, and selected layer mask.
- Selected layer mask preview bakes a grayscale texture where white means full selected-layer weight and black means zero weight.
- Material preview export writes the current editor preview texture to a PNG for inspection.

The editor preview path may use LibGDX `Pixmap` through
`TerrainMaterialPreviewBaker` because it is editor tooling. Runtime terrain
rendering does not depend on that preview baker.

## Runtime Pipeline

`RuntimeScene`:

`TerrainRuntimeFactory -> RuntimeTerrainMeshSystem -> TerrainRenderSystem`

`TerrainRuntimeFactory` loads the terrain descriptor referenced by a scene
`TerrainComponent`. Runtime scenes do not use constructor terrain fallbacks; a
missing or unreadable terrain asset is logged and that terrain entity is not
prepared for rendering.

`RuntimeTerrainMeshSystem` builds the dynamic model with `TerrainMeshBuilder`,
bakes the final runtime material with `TerrainMaterialBakeService`, stores the
result on `TerrainRendererComponent.finalSplatTexture`, and updates the material
with a matching `MaterialTextureRef`.

- `finalSplatTexture` stores the runtime CPU-baked final baseColor/albedo texture.
- `finalSplatResolution` controls runtime final material resolution and is populated from `TerrainComponent.bakedTextureResolution` for runtime scenes.
- `RuntimeTextureData` is the backend-neutral RGBA8888 texture payload generated at runtime.
- `MaterialTextureRef` is the material-side texture binding. Its `id` must match the `RuntimeTextureData.id`.
- `DrawDynamicModel.runtimeTextures` carries runtime texture payloads to the backend so they can be uploaded before draw.

Runtime texture ids must be unique per terrain entity because backend runtime
texture caches are keyed by id. `runtimeTerrainFinalSplatTextureId` generates
ids from the terrain entity id and renderer model id.

## Texture Semantics

- `previewDiffuseTexture` is editor-only.
- `previewResolution` is editor preview resolution.
- `finalSplatTexture` currently stores CPU-baked final baseColor/albedo.
- `finalSplatResolution` is runtime final material resolution.
- `finalSplatTexture` is not a real GPU splat/control map yet.
- Future terrain shaders may introduce real weight maps or control textures.
- `RuntimeTextureData` is passed together with `MaterialTextureRef`; the ref tells the material which id to bind, and the runtime texture payload gives the backend the pixels for that id.

## Current Limitations

- Terrain renders as `DynamicModel`.
- There is no chunking.
- There is no LOD.
- There is no per-chunk frustum culling.
- Final material is CPU-baked.
- Bake currently uses fallback material colors or layer colors.
- There is no actual albedo texture sampling in runtime bake.
- There is no terrain-specific shader.
- There is no normal, roughness, or metallic terrain blending.
- There are no texture arrays.
- There is no GPU splatting.
- Runtime terrain is suitable for prototype scenes, not large open worlds.

## Future Work

- Chunked terrain.
- LOD.
- GPU splat shader.
- Material texture sampling.
- Albedo, normal, and roughness layers.
- Terrain collision.
- Vegetation.
- Streaming.
