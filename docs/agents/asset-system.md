# Asset System — Agent Deep Dive

> Supplements `AGENTS.md` §9. There are **two** asset subsystems: runtime loading and editor
> discovery. Keep them distinct.

## 1. Runtime loading (`AssetService`)

Core contract: `core/.../engine/api/Assets.kt`. Backend: `GdxAssetService` in
`core/.../engine/backend/gdx/GdxAssetService.kt`.

- **Typed handles.** `AssetRef<T>` carries a normalized `path` + a marker type
  (`ModelAsset`, `TextureAsset`, `TerrainAsset`, `ShaderAsset`). Build via
  `AssetRef.model/texture/terrain/shader/primitiveModel`. `isPrimitive` flags `primitive:` paths.
- **Packs.** `AssetPack.assets: List<AssetRef<*>>`. A `Scene` declares `requiredAssets`; the
  default `Scene.scheduleAssets` queues them.
- **Lifecycle.** `queue(ref)` → `update(budgetMs)` (called each frame by `GameLoop`) →
  `isLoaded(ref)` → backend-neutral accessors. `progress()` reports normalized progress.
  `unload(ref)` releases backend state.
- **`get()` throws on the LibGDX backend** — core must keep handles, not pull backend objects.
- **Backend-neutral metadata** (no libGDX leakage): `triangleCount`, `modelInfo` →
  `ModelAssetInfo` (nodes/meshes/materials/vertices/triangles, vertex/uv/texture channels,
  skeleton flags, `ModelAnimationInfo`, `ModelMeshPartInfo`, `ModelMaterialInfo`),
  `modelSkeleton` → `ModelSkeletonInfo`, `modelSkeletonPose(...)` → `List<ModelBonePose>`,
  `modelBounds` → `ModelAssetBounds`, `texturePreviewHandle(...)` → `TexturePreviewHandle`.
- **Loaders registered** by `GdxAssetService`: `.g3dj`/`.g3db` (libGDX), `.obj` (libGDX),
  `.gltf`/`.glb` (gdx-gltf). Missing files are tracked and throw a descriptive error on `queue`.
- **Terrain is special:** `queue` **logs and ignores** `TerrainAsset`. Runtime terrain loading is
  handled outside `AssetService` (`engine/terrain/RuntimeTerrainService.kt` + persistence).
- **Caches:** the backend keeps model infos, skeletons, bounds, triangle counts, texture preview
  registry, runtime textures, and dedicated **pose-sampling** instances/controllers/scenes
  (separate from renderer per-entity caches).

### Async caveat
Assets load asynchronously while the scene is already `Active`. Scenes/systems must poll
`isLoaded`/`progress` and tolerate "not ready" — viewer tools show a Loading panel. Do not assume
readiness in `show()`.

## 2. Editor discovery (`AssetRegistryService`)

Core + impl: `core/.../engine/assets/`.

- **Domain** (`AssetDomain.kt`): `AssetId` (value class), `AssetCategory` (Model, Texture, Skybox,
  Material, Terrain, UI, Shader, Scene, Audio, Script, Unknown), `AssetType` (GltfModel, ObjModel,
  GdxModel, Texture, Skybox, Terrain, UiScene, Scene, Material, Shader, Unknown), `AssetDescriptor`
  (id, name, path, category, type, extension, size, mtime, tags, metadata map).
- **`LocalAssetRegistryService`** scans root folders (`model`, `textures`, `skyboxes`, `materials`,
  `terrains`, `ui/scenes`, `scenes`, `shaders`, `assets`), resolving type via `AssetImporter`
  registry or `AssetTypeDetector`.
- **`.krmeta` sidecars:** each asset gets a sibling `<file>.krmeta` JSON (`AssetMetadataCodec`)
  with a stable `asset:<uuid>` id, type/category, display name, tags, importer id, import settings.
  Missing/malformed metadata is recreated. Sidecars are not themselves indexed.
- **Background-safe scan pattern (model to follow):**
  - `scanSnapshot()` — pure IO, returns an immutable `AssetRegistrySnapshot` (assets + errors +
    timing). Safe on a background coroutine.
  - `applySnapshot(snapshot)` — mutates the in-memory descriptor list. **Main thread only.**
  - See `AssetBrowserSystem.requestScan`: `tasks.launchBackground { scanSnapshot() }` then
    `tasks.postToMain { applySnapshot(...) }`.
- **Category metadata:** texture (resolution/alpha/format), terrain (size/layers), and scene
  metadata are read during `describe`.

### Shared registry
- `EngineContext.assetRegistry` exposes a single backend-provided `AssetRegistryService`
  (`LibGdxBackend` creates a `LocalAssetRegistryService`). `AssetBrowserScene`, `SceneEditorScene`,
  and `UiComposerScene` all use `engine.assetRegistry` instead of constructing their own.

### Known gaps
- Full re-walk on every refresh (no incremental/watch).

## Rules

- Use `AssetRef` handles in core; never store backend objects.
- Do background scans with `scanSnapshot()`; apply only via `applySnapshot()` on the main thread.
- Add a new asset type by extending `AssetType`/`AssetCategory`, `AssetTypeDetector`, and
  optionally an `AssetImporter`; persist through `engine.sceneFiles`, not raw filesystem calls
  (registry scanning uses `java.io.File`, which is acceptable for the editor-only registry).
- Remember terrain's separate loading path.
