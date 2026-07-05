# Environment Editor

> Context for the Environment Editor MVP. Read this before changing Environment manifests,
> preview rendering, background behavior, or Model Viewer Environment defaults.

## Scope

Environment Editor opens one `.environment.json` asset and provides:

- editable exposure, rotation, diffuse intensity, and specular intensity;
- `Skybox`, `Solid Color`, `Transparent`, and `None` background modes;
- source-variant selection and manifest validation;
- a `Resource Inspector` panel for skybox, irradiance, radiance, BRDF LUT, and imported skybox source texture inspection;
- a separate `Selected Resource Preview` panel with its own viewport state;
- a renamed `Import Skybox Atlas` workflow for atlas/cross/row textures that exports separate runtime face files and updates `environment.skybox.faces`;
- explicit HDR/EXR generation dialogs for skybox, irradiance, radiance, BRDF LUT, and `Generate All IBL`, with clear unavailable-state messaging when a real HDR reader/generator is not available;
- a live PBR preview using the bundled `model/tests/MetalRoughSpheres.glb`;
- file save/reload/revert, layout persistence, diagnostics, and logs.

Desktop route:

- `krender.scene=environment-editor`
- `krender.environment.path=<path>`

Environment Editor now clearly separates atlas import from HDR/EXR generation. `Import Skybox Atlas`
only splits previewable 2D source textures into separate runtime face files and updates the manifest.
The HDR/EXR generation dialogs are separate and keep generation inputs distinct from runtime resource
references. The current editor build includes a verified HDR/EXR reader plus skybox, irradiance,
and radiance generators that write separate runtime face files. BRDF LUT generation is still a
follow-up task and must remain explicitly unavailable instead of silently pretending to run. Future
IBL generation must also target separate face-file outputs instead of shared runtime atlases.

## Ownership

| Layer | Main types | Responsibility |
|---|---|---|
| Core domain | `Environment`, `EnvironmentSettings` | Backend-neutral runtime data and `.environment.json` schema. |
| Persistence | `EnvironmentSerializer`, `EnvironmentLoader`, `DefaultEnvironmentService` | JSON encode/decode, file IO, validation handoff, and editor/runtime access. |
| Validation | `EnvironmentValidator` | Source/resource checks through `SceneFileService`. |
| Tool state | `EnvironmentEditorState`, `EnvironmentEditorController` | Mutable editing session, dirty state, disk commands. |
| Tool UI | `EnvironmentEditorUiFactory`, `Environment*Panel` | ImGui panels and layout tracking. |
| Preview adapter | `EnvironmentPreviewController` | Converts the current Environment into `GltfRendererSettings`. |
| Preview scene | `EnvironmentPreviewSceneAssembler`, camera/render systems | Creates camera/model entities and emits `DrawModel`. |
| Backend | `GdxHdrEnvironmentResolver`, `GdxGltfRenderer`, `GdxRenderer3D` | Loads Environment-owned skybox/IBL resources, configures PBR, draws skybox/model, clears background. |

No tool class imports LibGDX or gdx-gltf. The preview crosses the backend boundary only through
`AssetRef`, ECS components, `DrawModel`, and `GltfRendererSettings`.

## Panels

- `Environment Editor Control Panel` — file/session actions, dirty state, path, resolved path, size, status, and exit.
- `Inspector` — flat label/value manifest summary.
- `Settings` — runtime settings, background mode selector, and mode-specific options.
- `Sources` — source variants with default-source switching.
- `Resource Inspector` — 2D resource canvas for runtime skybox/irradiance/radiance/BRDF LUT inspection plus imported skybox source region overlays.
- `Selected Resource Preview` — isolated selected face/region viewport with independent fit/reset/zoom/pan controls and selected-item diagnostics.
- `Diagnostics` — validation status and issue list.
- `Preview` — preview test-model list, auto-rotate, camera distance, yaw, pitch, camera reset, resource availability, fallback mode, and live preview status.
- `Logs` — shared engine log stream for Environment Editor activity.

## Lifecycle

`EnvironmentEditorScene.show()` performs the composition:

1. Create `EnvironmentEditorState` and `DefaultEnvironmentService`.
2. Load the panel layout and create `EnvironmentEditorController`.
3. Reload the manifest into a clean state and validate it.
4. Install state logging plus preview camera/model/render systems.
5. Build all editor panels through `EnvironmentEditorUiFactory`, including the resource inspector stack and selected-preview stack.

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

`environmentPreset` points to the manifest. `GdxHdrEnvironmentResolver` reads only the resource
paths stored directly in the Environment manifest; it does not sample the source `.hdr`/`.exr`
directly.

### PBR resources

- **Skybox** is the visible cubemap background. It does not replace lighting maps.
- **Irradiance** is the low-frequency cubemap used for diffuse lighting on dielectric surfaces.
- **Radiance** is a prefiltered cubemap mip chain used for roughness-dependent specular
  reflections. Smooth metal samples sharper levels; rough materials sample blurrier levels.
- **BRDF LUT** stores the view/roughness integration term used by split-sum specular IBL.

### Runtime resource layout

Runtime Environment resources must resolve to separate files:

- `skybox/<face>.png`
- `irradiance/<face>.png`
- `radiance/mip_<n>/<face>.png`
- `brdf_lut.png`

Atlas/cross/row textures are editor/import formats only. Environment Editor V2 does not depend on a
shared runtime cubemap atlas.

If required resources are absent, the preview remains usable: the controller disables unavailable
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

Changes replace the immutable `Environment` in state and mark the session dirty. The render
system reads state every frame, so no event bus or renderer restart is needed.

## Background Semantics

`BackgroundMode` is the single source of truth for background visibility:

- `Skybox` draws the configured skybox cubemap when available.
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

## Skybox Import

The `Imported Skybox Source` mode plus `Skybox Import` controls provide:

- manual source texture path entry;
- preset-based default face regions for `CubeCross4x3`, `HorizontalRow6x1`, `VerticalColumn1x6`, and `Custom`;
- numeric per-face region editing;
- per-face rotate/flip metadata used during export;
- split/export to separate PNG face files;
- in-memory Environment manifest update for `skybox.faces`.

Saving still happens through the normal Environment save workflow after import.

## HDR / EXR Generation

The `Generation` block now exposes six explicit actions:

- `Import Skybox Atlas`
- `Generate Skybox From HDR/EXR`
- `Generate Irradiance`
- `Generate Radiance`
- `Generate BRDF LUT`
- `Generate All IBL`

HDR/EXR dialogs keep authoring inputs separate from runtime resources:

- selected HDR/EXR files are generation inputs, not automatic runtime dependencies;
- `Remember HDR/EXR source in Environment metadata` is optional and defaults to off;
- runtime output still targets `skybox/<face>.png`, `irradiance/<face>.png`,
  `radiance/mip_<n>/<face>.png`, and `brdf_lut.png`.

`Generate Skybox From HDR/EXR`, `Generate Irradiance`, `Generate Radiance`, and the corresponding
enabled stages inside `Generate All IBL` now run against the verified HDR/EXR reader and emit
runtime PNG face files. `Generate BRDF LUT` remains explicitly unavailable with a visible reason,
and `Generate All IBL` must stop with a clear message when BRDF LUT is still enabled.

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

## Current limitations

- Imported skybox source preview currently targets previewable 2D textures and does not split HDR/EXR sources directly.
- Import region editing is numeric-only; drag/resize handles are not implemented yet.
- BRDF LUT generation is still not implemented and must stay explicitly unavailable.
- Irradiance and radiance outputs currently follow the existing PNG runtime contract, so HDR range is reduced during export.
- The selected-resource preview panel is wired for cropped-region previews, but only skybox source import currently provides source-region data.

## Validation

PR checks for this tool are the repository checks: `detekt`, `ktlintCheck`, formatting script,
all desktop/core/backend/tool compile targets, and core/scene-player JVM tests. Rendering still
requires manual verification with a desktop OpenGL context.
