# Environment Editor

> Context for the Environment Editor MVP. Read this before changing Environment manifests,
> preview rendering, background behavior, or Model Viewer Environment defaults.

## Scope

Environment Editor opens one `.environment.json` asset and provides:

- editable exposure, rotation, diffuse intensity, and specular intensity;
- `Skybox`, `Solid Color`, `Transparent`, and `None` background modes;
- source-variant selection and manifest validation;
- a read-only Tools panel for generated resource references;
- a live PBR preview using the bundled `model/tests/MetalRoughSpheres.glb`;
- file save/reload/revert, layout persistence, diagnostics, and logs.

The MVP does not generate skybox, irradiance, radiance, or BRDF LUT resources. The manifest may
carry generation metadata for future tooling, but no generator service or generation UI is
installed in Environment Editor.

## Ownership

| Layer | Main types | Responsibility |
|---|---|---|
| Core domain | `EnvironmentAsset`, `EnvironmentSettings`, `EnvironmentGeneratedResources` | Backend-neutral manifest data. |
| Persistence | `EnvironmentManifestCodec`, `EnvironmentManifestMapper`, `DefaultEnvironmentService` | JSON decode/encode and file IO. |
| Validation | `EnvironmentValidator` | Source/generated-resource checks through `SceneFileService`. |
| Tool state | `EnvironmentEditorState`, `EnvironmentEditorController` | Mutable editing session, dirty state, disk commands. |
| Tool UI | `EnvironmentEditorUiFactory`, `Environment*Panel` | ImGui panels and layout tracking. |
| Preview adapter | `EnvironmentPreviewController` | Converts the current Environment into `GltfRendererSettings`. |
| Preview scene | `EnvironmentPreviewSceneAssembler`, camera/render systems | Creates camera/model entities and emits `DrawModel`. |
| Backend | `GdxHdrEnvironmentResolver`, `GdxGltfRenderer`, `GdxRenderer3D` | Loads generated maps, configures PBR, draws skybox/model, clears background. |

No tool class imports LibGDX or gdx-gltf. The preview crosses the backend boundary only through
`AssetRef`, ECS components, `DrawModel`, and `GltfRendererSettings`.

## Lifecycle

`EnvironmentEditorScene.show()` performs the composition:

1. Create `EnvironmentEditorState` and `DefaultEnvironmentService`.
2. Load the panel layout and create `EnvironmentEditorController`.
3. Reload the manifest into a clean state and validate it.
4. Install state logging plus preview camera/model/render systems.
5. Build all editor panels through `EnvironmentEditorUiFactory`.

The test model is declared in `requiredAssets`, so loading remains asynchronous. The renderer and
asset service tolerate the model not being ready during early active frames.

## PBR Preview Flow

```text
.environment.json
        |
        v
EnvironmentEditorState.environment
        |
        v
EnvironmentPreviewController
        |
        +-- availability/status for Preview panel
        |
        +-- GltfRendererSettings
                 |
                 v
EnvironmentPreviewRenderSystem -> DrawModel
                 |
                 v
GdxRenderer3D -> GdxGltfRenderer -> gdx-gltf scene manager
                 |
                 v
       skybox + IBL + material spheres
```

`environmentPreset` points to the manifest. `GdxHdrEnvironmentResolver` reads only generated
resources for runtime PBR lighting; it does not sample the source `.hdr`/`.exr` directly.

### PBR resources

- **Skybox** is the visible cubemap background. It does not replace lighting maps.
- **Irradiance** is the low-frequency cubemap used for diffuse lighting on dielectric surfaces.
- **Radiance** is a prefiltered cubemap mip chain used for roughness-dependent specular
  reflections. Smooth metal samples sharper levels; rough materials sample blurrier levels.
- **BRDF LUT** stores the view/roughness integration term used by split-sum specular IBL.

If generated maps are absent, the preview remains usable: the controller disables unavailable
skybox rendering, reports specific warnings, and increases direct-light fallback intensity.
Backend load failures after manifest resolution are reported in Logs.

## Runtime Settings

| Setting | Effect |
|---|---|
| `exposure` | Scales final environment/PBR brightness before tone mapping. |
| `rotationDegrees` | Rotates environment lookup directions around the vertical axis. |
| `skyboxIntensity` | Scales only the visible skybox; valid editor range is `0..1`. |
| `diffuseIntensity` | Scales ambient/irradiance contribution. |
| `specularIntensity` | Scales radiance/specular IBL; valid editor range is `0..1`. |

Changes replace the immutable `EnvironmentAsset` in state and mark the session dirty. The render
system reads state every frame, so no event bus or renderer restart is needed.

## Background Semantics

`BackgroundMode` is the single source of truth for background visibility:

- `Skybox` draws the generated cubemap when available.
- `Solid Color` clears the viewport using `backgroundColor`.
- `Transparent` clears with alpha zero. Actual window transparency depends on the target
  backbuffer/compositor.
- `None` draws no background and uses the renderer's neutral clear color. Environment lighting
  remains active.

`showSkybox` in `GltfRendererSettings` is a derived backend request, not a second editor option. It
is true only when mode is `Skybox` and the manifest defines a skybox.

## Save and Reload

- **Save** writes current state, revalidates, and clears dirty.
- **Revert** reloads disk state while retaining the current in-memory asset if loading fails.
- **Reload** reloads disk state and clears the asset on failure, which exposes the load error UI.
- **Persist UI** saves the current ImGui layout.
- **Reset UI** restores built-in layout defaults.

## Extension Points

Add preview models in `EnvironmentEditorConfig.testModels`; the first item is the active MVP model.
Model selection and persisted preview preferences are future work. Add generation as a separate
service/panel only when a real backend implementation exists; do not restore placeholder actions.

When changing manifest settings, update all of:

- serializer/domain defaults;
- `EnvironmentValidator`;
- Asset Registry Environment metadata;
- `EnvironmentPreviewController`;
- Model Viewer Environment-default synchronization;
- this document.

## Validation

PR checks for this tool are the repository checks: `detekt`, `ktlintCheck`, formatting script,
all desktop/core/backend/tool compile targets, and core/scene-player JVM tests. Rendering still
requires manual verification with a desktop OpenGL context.
