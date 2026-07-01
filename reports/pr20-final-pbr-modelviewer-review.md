# PR #20 — Final PBR / Model Viewer Review

## 1. Scope reviewed

### Summary of what the PR changes

PR #20 (`feature/pbr_update` → `develop`) is a large, multi-commit change that establishes
glTF / PBR as the primary 3D rendering direction for KRender. It spans 28 commits,
touches 28 Kotlin source files (+2302 / −548 lines), adds ~80 generated IBL PNG assets,
and ships 23 incremental status reports.

Key deliverables:

- **HDR environment manifest v2** (`environment.json` schema `krender.hdr-environment`).
- **Default HDR environment asset** (`assets/hdr/default/`) with source EXR variants,
  cubemap-cross skybox, generated irradiance faces, generated radiance mip-chain, and
  shared BRDF LUT.
- **HDR generation toolchain** — standalone CLI entry point plus five generator classes
  (`CubemapCrossSplitter`, `IblDiffuseGenerator`, `IblSpecularGenerator`,
  `SharedBrdfLutExporter`, `HdrImageProcessing`) behind a Gradle `generateHdrEnvironment`
  task.
- **Core manifest model & codec** (`HdrEnvironmentManifest`, `HdrEnvironmentManifestCodec`,
  `HdrEnvironmentManifestLoader`, `HdrEnvironmentAssets`) in the backend-neutral `core`
  module.
- **Backend environment pipeline** (`GdxHdrEnvironmentResolver`, `GdxGltfEnvironmentAssetLoader`,
  `GdxGltfEnvironment`, `GdxHdrCubemapLoader`) in `engine:backend-gdx`.
- **Renamed & restructured glTF renderer** — `GdxGltfPbrPreviewRenderer` deleted, replaced
  by `GdxGltfRenderer` with cleaner environment integration and procedural-IBL fallback.
- **`GltfRendererSettings` render-command data** added to `DrawModel` and the core
  `Render.kt` API.
- **Model Viewer UI overhaul** — PBR is now the default renderer, dedicated
  `GltfPbrRendererOptionsPanel` and `LegacyRendererOptionsPanel`, renderer selector combo,
  per-control tooltips, "Combined" channel rename, legacy/PBR lighting separation.
- **Documentation** — `docs/assets/gltf-workflow.md`, updated agent docs and tool docs.
- **Cleanup** — removed old `PbrPreviewView` / `GdxGltfPbrPreviewRenderer`, resolved CI
  failures, stabilized tests.

### Files / areas analyzed

| Area | Key files |
|---|---|
| Core manifest & data | `HdrEnvironmentManifest.kt`, `HdrEnvironmentAssets.kt`, `Render.kt` |
| Backend environment | `GdxHdrEnvironmentResolver.kt`, `GdxGltfEnvironmentAssetLoader.kt`, `GdxGltfEnvironment.kt`, `GdxHdrCubemapLoader.kt` |
| Backend renderer | `GdxGltfRenderer.kt`, `GdxRenderer3D.kt` |
| HDR tools | `CubemapCrossSplitter.kt`, `IblDiffuseGenerator.kt`, `IblSpecularGenerator.kt`, `SharedBrdfLutExporter.kt`, `HdrImageProcessing.kt`, `HdrEnvironmentGeneratorMain.kt` |
| Model Viewer tool | `ModelViewerPanels.kt`, `ModelViewerState.kt`, `ModelViewerSystems.kt`, `ModelViewerScene.kt`, `ModelViewerTextureChannelResolver.kt` |
| Tests | `ModelViewerSystemTest.kt`, `ModelViewerTextureChannelResolverTest.kt`, `AssetBrowserSystemTest.kt` |
| Docs | `gltf-workflow.md`, `model-viewer.md`, `rendering-pipeline.md`, `backend-abstraction.md`, `tools.md` |
| Assets | `assets/hdr/default/environment.json`, generated PNGs, `model_viewer_layout.json` |

---

## 2. Overall assessment

### General verdict

This PR is a **strong, well-structured foundation** for making glTF / PBR the primary
3D direction in KRender. The manifest schema is thoughtful, the responsibility split
between resolver → loader → environment → renderer is clean, and the Model Viewer UI
correctly reflects the dual-renderer architecture. The old "PBR preview" naming has been
fully retired.

### Merge readiness assessment

**Ready to merge** with a small number of recommended follow-ups. No critical blockers
remain. The architecture is sound, the runtime fallback path works, documentation is
thorough, and tests cover the key viewer-system behaviors.

### What is already strong

- Clean deletion of `GdxGltfPbrPreviewRenderer` and its `PbrPreviewView` API surface.
- `GltfRendererSettings` in `Render.kt` is well-scoped and backend-neutral.
- HDR manifest v2 schema is flexible (source variants, shared BRDF LUT, face-level
  generated paths).
- Separation between `GdxHdrEnvironmentResolver`, `GdxGltfEnvironmentAssetLoader`, and
  `GdxGltfEnvironment` is clear.
- Model Viewer renderer selection UX is intuitive; legacy is explicitly secondary.
- Tooltips on every control improve discoverability.
- `HdrEnvironmentAssets` keeps path conventions in the backend-neutral `core` module.
- IBL generators are explicitly documented as MVP approximations — no pretense of
  physical correctness.

### What still needs work

- Manifest validation is duplicated between `HdrEnvironmentManifestLoader` (JVM/tools)
  and `GdxHdrEnvironmentResolver` (runtime Gdx).
- `ModelViewerState` is a growing god-data-object (173 lines, 50+ mutable fields).
- `ModelViewerPanels.kt` is 1348 lines — functional but would benefit from file splitting.
- Per-entity `GltfSceneManager` allocation in `GdxGltfRenderer` has non-trivial GPU cost.
- The cubemap loader builds all mip `Pixmap` objects synchronously on the render thread.
- No unit tests for the manifest codec/loader or the HDR generators.

---

## 3. Strengths

### Architecturally good

- **Core / backend boundary respected.** `GltfRendererSettings`, `HdrEnvironmentManifest`,
  and `HdrEnvironmentAssets` live in `core`. All Gdx/GL calls stay in `engine:backend-gdx`.
  No `com.badlogic.gdx` imports leaked into `core` or `engine:tools`.
- **Clean render-command extension.** Adding `gltfRenderer: GltfRendererSettings?` to
  `DrawModel` follows the existing `debugView` pattern — optional, nullable, backend
  resolves it.
- **Preset caching in `GdxGltfEnvironment`.** Presets are loaded once and cached by name.
  Switching renderer modes is zero-cost after first load.
- **Fallback chain.** BRDF LUT resolution tries manifest path → `hdr/_common/brdf/` →
  gdx-gltf classpath. Irradiance/radiance fall back to procedural `IBLBuilder` when
  generated maps are missing.
- **Shader config key tracking.** `GdxGltfRenderer` only recreates the `SceneManager`
  when gamma/sRGB settings actually change, minimizing GPU churn.

### Strong direction

- **glTF / PBR as default.** `DEFAULT_MODEL_VIEWER_RENDERER_MODE = GltfPbr` and the
  `GltfRendererSettings.enabled` flag make PBR the primary path. Legacy remains available
  but clearly secondary.
- **Environment as a named preset.** `environmentPreset: String` in
  `GltfRendererSettings` keeps the door open for future Asset Browser integration and
  multiple named environments.
- **Manifest v2 with source variants.** Encoding multiple EXR/HDR sources with an
  `activeVariant` selector future-proofs the asset for quality tier switching.

### What should be preserved

- The `GdxGltfEnvironment` → `GdxHdrEnvironmentResolver` → `GdxGltfEnvironmentAssetLoader`
  layering. This is the right separation.
- The `HdrEnvironmentManifestCodec` / `HdrEnvironmentManifestLoader` split between
  serialization and validation+resolution.
- The per-renderer options panel pattern (`GltfPbrRendererOptionsPanel`,
  `LegacyRendererOptionsPanel`).
- The `warnOnce` pattern in `GdxGltfRenderer` — prevents log flooding.

---

## 4. Critical issues

### 4.1 Duplicated manifest validation — Medium

`HdrEnvironmentManifestLoader.validate()` (core, JVM `java.nio.file.Path`) and
`GdxHdrEnvironmentResolver.validate()` (backend, `Gdx.files`) implement nearly identical
checks with different I/O backends. If a new validation rule is added to one and missed
in the other, the tool pipeline and runtime will diverge.

**Impact:** Maintenance risk, potential silent schema drift.
**Suggestion:** Extract a pure-data validation function in `core` that checks everything
except file existence. Let each caller add its own file-existence checks via a
`(String) -> Boolean` lambda.

### 4.2 Per-entity `GltfSceneManager` allocation — Medium

`GdxGltfRenderer` creates a full `GltfSceneManager` (with its own shader programs, depth
provider, and environment) for **every unique (entityId, modelPath)** pair. In the
Model Viewer this is always one entry, but Scene Editor or Scene Player could have many
glTF entities and each would get its own `SceneManager`, multiplying GPU shader objects.

**Impact:** Will not scale to scenes with multiple PBR models.
**Suggestion:** For MVP this is acceptable in Model Viewer. Before Scene Editor/Player
integration, refactor to a single shared `GltfSceneManager` that renders multiple
`Scene` instances, or implement multi-scene batching.

### 4.3 Synchronous cubemap loading on render thread — Medium

`GdxHdrCubemapLoader.loadFaces()` and `loadMipFaces()` decode PNGs and upload GL textures
synchronously. For the default environment with 10 mip levels × 6 faces × radiance + 6
irradiance + 6 skybox + 1 BRDF LUT, this is ~73 texture loads in one frame.

**Impact:** Noticeable hitch on first environment load (acceptable for editor MVP, risky
for runtime player).
**Suggestion:** Document as known limitation. For Scene Player, add async cubemap loading
through `TaskService`.

### 4.4 `ModelViewerState` field proliferation — Medium

`ModelViewerState` has grown to 50+ mutable `var` fields, mixing camera state, grid
settings, legacy lighting, PBR lighting, debug state, UV checker state, selection state,
and loader state in one flat data class. This makes it difficult to reason about which
fields belong to which subsystem.

**Impact:** Increasing maintenance cost as more features are added.
**Suggestion:** Group related fields into nested data objects:
`ModelViewerState.gltfSettings`, `ModelViewerState.legacySettings`,
`ModelViewerState.debugSettings`, `ModelViewerState.viewportSettings`.

---

## 5. PBR rendering workflow review

### Current workflow evaluation

The workflow is:

1. `ModelViewerModelRenderSystem` emits `DrawModel` with `gltfRenderer = GltfRendererSettings(...)`.
2. `GdxRenderer3D.render()` routes glTF commands to `GdxGltfRenderer.render()`.
3. `GdxGltfRenderer` resolves the environment preset via `GdxGltfEnvironment.preset()`.
4. Environment loading: `GdxHdrEnvironmentResolver.resolve()` → manifest decode → path
   resolution → `GdxGltfEnvironmentAssetLoader.loadPreset()` → `GdxHdrCubemapLoader`.
5. If IBL maps are missing, falls back to procedural `IBLBuilder`.
6. Applies environment maps, ambient, directional light, skybox, rotation to the
   per-entry `GltfSceneManager`.
7. Renders via gdx-gltf `SceneManager`.

This is correct, maintainable, and follows the established render-command pattern.

### Weaknesses

- **No tone-mapping application.** `GltfRendererSettings.toneMapping` and `exposure` are
  declared but only `exposure` affects `resolveEnvironmentState`. The `PbrToneMapping`
  enum (Aces, Reinhard, None) is surfaced in the data model but gdx-gltf's built-in
  tone-mapping is not explicitly switched — it relies on gdx-gltf defaults. This is
  acceptable for MVP but the UI control implies user-controllable tone mapping.
- **Environment rotation only applies PBRMatrixAttribute.** The skybox visual does not
  rotate to match, so rotation appears to affect reflections but not the visible sky.
- **`GltfRendererSettings.enabled = debugView?.active != true`.** When a debug view is
  active, the glTF renderer is implicitly disabled. This coupling is clear in code but
  not surfaced in the UI — the user sees the renderer selector still say "glTF / PBR" while
  the legacy debug renderer is drawing.

### Suggested improvements

1. Either implement tone-mapping switching in `createSceneManager` shader config, or
   remove the `toneMapping` control from the UI until it's functional. Prefer honest
   controls over aspirational ones.
2. Add a note in the tooltip when environment rotation does not rotate the skybox visual.
3. Consider showing a subtle "(Debug mode)" indicator in the viewport panel when a
   material channel override is active and the glTF renderer is bypassed.

---

## 6. Model Viewer review

### UI/UX review

The Model Viewer UI is well-organized:

- **Toolbar** — Save/Reset Layout, Reload, Exit, status display.
- **Viewport panel** — Camera, Display Mode, Renderer Selector, Renderer Options (split
  by active renderer).
- **ModelInfo, MeshParts, Materials, TextureChannels** — detailed metadata inspection.
- **Loading panel** — conditional display during asset load.
- **Logs panel** — live log viewer.

Every interactive control has a tooltip, which is a significant UX improvement.

### Renderer selection review

- `ModelViewerRendererMode.GltfPbr` / `LibGdx` enum is clean.
- Labels "glTF / PBR" and "LibGDX / Legacy" are clear and accurate.
- Default is PBR, which aligns with the intended direction.
- The renderer selector is in the Viewport panel, which is the right location.

### Control/state review

- PBR controls: Environment Preset (text input), Show Skybox, Intensity, Exposure,
  Rotation, Directional Light (enable, intensity, color, yaw, pitch), Reset buttons.
  This is a good set for MVP.
- Legacy controls: Ambient Intensity, Ambient Color, Directional Light controls, Reset.
  Matches the legacy renderer capabilities.
- Missing: **No environment preset dropdown/picker.** The environment preset is a raw
  text input, which requires the user to know valid preset names. Acceptable for MVP but
  should become a combo/picker once Asset Browser supports environments.

### Suggested improvements

1. **Split `ModelViewerPanels.kt` (1348 lines) into multiple files.** Extract
   `GltfPbrRendererOptionsPanel` and `LegacyRendererOptionsPanel` into
   `ModelViewerRendererOptionsPanels.kt`. Extract helper functions (`tooltipOnHover`,
   `textLine`, `drawColorControl`, format utilities) into `ModelViewerPanelHelpers.kt`.
2. **Group gltf-prefixed fields in `ModelViewerState`.** A nested
   `data class GltfPbrSettings(...)` would reduce the flat field count from ~50 to ~35
   and make it obvious which state belongs to PBR vs shared vs legacy.
3. **Add a "Renderer: debug override active" indicator** when `debugMode != Combined` so
   the user understands why the PBR skybox disappeared.
4. **Consider persisting renderer mode and basic PBR defaults** across sessions via the
   layout JSON or a separate prefs file.

---

## 7. HDR / Environment workflow review

### Asset layout review

```
assets/hdr/
  _common/brdf/brdfLUT.png        (shared)
  default/
    environment.json               (manifest v2)
    source/*.exr                   (original HDR files, not committed in repo — gitignored or large)
    skybox/default_skybox_studio.png
    skybox/generated/environment_{face}.png
    irradiance/irradiance_{face}.png
    radiance/radiance_{mip}_{face}.png
```

This layout is clean and convention-based. The `_common/` prefix for shared assets and
per-environment directories under `hdr/` is a good pattern.

### Manifest review

The v2 manifest schema is well-designed:

- **Source variants** with `activeVariant` selector supports quality tiers.
- **Face token patterns** (`{face}`, `{mip}`) for generated paths are flexible.
- **`defaults` block** encodes per-environment rendering hints (exposure, tone mapping,
  gamma, skybox, rotation, ambient intensity).
- **`generated: boolean`** flags on irradiance/radiance track generation status.
- **`shared: boolean`** on BRDF LUT supports the common LUT pattern.

Schema issues:

- **`defaults.toneMapping` is a `String` ("ACES")** rather than using the `PbrToneMapping`
  enum. This creates a loose coupling — the manifest says "ACES", the runtime enum says
  `Aces`. A case mismatch would silently fail.
- **No `defaults.environmentIntensity`** — only `ambientIntensity` is in the manifest.
  This may cause confusion between the two concepts.

### Future Environment concept readiness

The current layout is a **good base** for `Environment` as a reusable engine asset:

- The manifest already functions as an asset descriptor.
- The preset-by-name resolution in `GdxGltfEnvironment.preset()` maps naturally to
  `AssetRef<EnvironmentAsset>`.
- The `_common/brdf/` shared asset pattern is correct.
- The face-based cubemap layout supports both generated and hand-authored environments.

**What must change before Environment Editor:**

1. Promote `HdrEnvironmentManifest` to an engine-level `EnvironmentAsset` type with a
   typed `AssetRef<EnvironmentAsset>`.
2. Add `.krmeta` generation for `environment.json` files so Asset Browser discovers them.
3. Move environment preset resolution from the backend (`GdxHdrEnvironmentResolver`) to
   a core `EnvironmentService` or `AssetRegistry` extension, with the backend only
   handling GPU resource loading.
4. Add runtime environment switching support (currently `GdxGltfEnvironment.preset()` is
   load-once-cache-forever).

**What is still too ad-hoc:**

- The CLI generator (`HdrEnvironmentGeneratorMain`) is functional but has no progress
  reporting, no incremental regeneration, and no validation of output quality.
- The irradiance/radiance generators use box blur as an approximation — this is fine for
  MVP but should be prominently flagged in any Environment Editor preview.

### Suggested improvements

1. Add `defaults.environmentIntensity` to the manifest schema for completeness.
2. Normalize `defaults.toneMapping` to match the `PbrToneMapping` enum case exactly, or
   add case-insensitive parsing.
3. Plan `.krmeta` support for `environment.json` to enable Asset Browser discovery.

---

## 8. Architectural cleanup opportunities

### Classes to split

| File | Lines | Suggested split |
|---|---|---|
| `ModelViewerPanels.kt` | 1348 | Extract renderer-options panels → `ModelViewerRendererOptionsPanels.kt`, extract helpers → `ModelViewerPanelHelpers.kt` |
| `GdxRenderer3D.kt` | 1054 | Already delegates to sub-renderers — acceptable size, but wireframe/dynamic-model code could eventually move to dedicated files |
| `ModelViewerState.kt` | 173 | Group PBR fields into `GltfPbrSettings`, legacy fields into `LegacyRendererSettings` |

### Names to change

| Current | Suggested | Reason |
|---|---|---|
| `GdxGltfEnvironment` | `GdxGltfEnvironmentCache` or `GdxEnvironmentPresetCache` | The class is a preset cache, not the environment itself |
| `GdxGltfEnvironmentAssetLoader` | `GdxEnvironmentPresetLoader` | Clarifies it loads preset GPU resources, not generic assets |
| `GdxHdrEnvironmentResolver` | `GdxEnvironmentManifestResolver` | "HDR" is implementation detail; this resolves manifest paths |
| `gltfEnvironmentPreset` (in State) | `environmentPreset` | When glTF/PBR is the default, the "gltf" prefix is redundant noise |

### Systems to reorganize

- **Validation deduplication.** `HdrEnvironmentManifestLoader.validate()` and
  `GdxHdrEnvironmentResolver.validate()` share ~80% of logic. Extract common checks.
- **Light direction calculation.** `gltfLightDirection()` in `GdxGltfRenderer.kt` and
  `modelViewerLightDirection()` in `ModelViewerSystems.kt` are the same formula
  implemented twice with different math imports. Extract to a shared utility.

### Legacy/workaround paths to remove

- `GdxGltfPbrPreviewRenderer.kt` — **already deleted** ✓.
- `PbrPreviewView` type — **already removed from `Render.kt`** ✓.
- The old `ApplyEnvironment.skyboxTexture` path for the legacy skybox remains but is
  harmless; it serves Scene Editor's existing skybox workflow.

---

## 9. Performance / optimization opportunities

### Resource loading

- **73+ PNG decode+upload on first environment load.** Radiance alone is 10 mips × 6
  faces = 60 PNGs. All loaded synchronously in `GdxGltfEnvironmentAssetLoader`. For
  editor this is acceptable (one-time cost), but Scene Player should use async loading.
- **Repeated `Gdx.files.internal(it).exists()` checks.** Both
  `GdxHdrEnvironmentResolver.warnForMissingGeneratedMaps()` and
  `GdxGltfEnvironmentAssetLoader.loadCubemap()` / `loadRadiance()` check face existence
  individually. For 60+ radiance faces this means 60+ filesystem probes on every
  environment load. Acceptable for a one-time cost but could be batched.

### Memory

- **Per-entity SceneManager in `GdxGltfRenderer`.** Each `GltfSceneEntry` owns its own
  `GltfSceneManager`, procedural cubemaps (`envMap`, `irradianceMap`, `radianceMap`), and
  `SceneSkybox`. For Model Viewer (1 entity) this is fine. For multi-entity scenes, GPU
  memory would multiply.
- **Preset cubemaps are not shared across entries.** `GdxGltfEnvironment` caches presets
  globally, but the `GltfSceneEntry.ensureSceneSkybox()` creates a new `SceneSkybox` per
  entry even when using the same preset cubemap. The `SceneSkybox` itself is lightweight
  (just a mesh + reference), so this is low priority.

### Caching

- **`GdxGltfEnvironment.presets` never evicts.** If many presets are loaded during a
  session, they remain in memory. For Model Viewer this is one preset. For a future
  Environment Editor that loads/previews many presets, add LRU eviction.
- **Procedural fallback key is string-formatted floats.** `GltfEnvironmentFallbackKey`
  uses `"%.3f".format(...)` for cache keys. This is correct for avoiding float comparison
  issues but creates string allocations every frame. Low priority.

### IBL asset handling

- **Box-blur IBL is an approximation.** Documented correctly. The irradiance maps use
  downsampling + 4-pass box blur, radiance uses roughness-scaled blur. The visual result
  is passable for editor preview. Runtime quality will improve when cosine-weighted
  convolution and GGX prefiltering are implemented.
- **PNG-based IBL workflow.** Using PNG for IBL faces means 8-bit-per-channel LDR data.
  HDR information from the source EXR is lost during generation. Acceptable for MVP; a
  future pipeline should output 16-bit or EXR cubemap faces for better specular highlights.

### Optimizations worth doing now vs later

| Optimization | Priority | When |
|---|---|---|
| Async cubemap loading for Scene Player | High | Before Scene Player PBR integration |
| Shared SceneManager for multi-entity scenes | High | Before Scene Editor PBR integration |
| Light direction function deduplication | Low | Next cleanup PR |
| Validation deduplication | Medium | Next cleanup PR |
| LRU eviction for environment presets | Low | When Environment Editor ships |

---

## 10. Recommended follow-up tasks

1. **Extract shared manifest validation** — Deduplicate `HdrEnvironmentManifestLoader.validate()` and `GdxHdrEnvironmentResolver.validate()` into a common pure-data validation function in `core`.

2. **Add unit tests for `HdrEnvironmentManifestCodec`** — Round-trip encode/decode tests, malformed JSON handling, missing required fields. The codec is non-trivial (390 lines) and currently untested.

3. **Split `ModelViewerPanels.kt`** — Extract renderer option panels and helper functions into separate files to keep each file under ~500 lines.

4. **Group `ModelViewerState` PBR/legacy fields** — Introduce nested `GltfPbrSettings` and `LegacyRendererSettings` data classes.

5. **Deduplicate light direction calculation** — `gltfLightDirection()` and `modelViewerLightDirection()` are the same math. Extract to a shared `MathUtils` function.

6. **Clarify tone-mapping control** — Either wire `PbrToneMapping` into the gdx-gltf shader config, or add a tooltip noting it's not yet functional.

7. **Plan async cubemap loading** — Before Scene Player PBR integration, add background cubemap loading via `TaskService` to avoid render-thread hitches.

8. **Plan shared `GltfSceneManager`** — Before Scene Editor PBR integration, refactor `GdxGltfRenderer` to use a single shared `SceneManager` for multiple entities.

9. **Add `.krmeta` support for `environment.json`** — Enable Asset Browser discovery of environment presets.

10. **Normalize manifest `toneMapping` case** — Align `defaults.toneMapping` string values with the `PbrToneMapping` enum to prevent silent mismatches.

---

## 11. Merge recommendation

### **Ready to merge.**

The PR delivers a solid, well-documented foundation for glTF / PBR as the primary
3D rendering direction. The architecture follows KRender's core/backend separation,
the Model Viewer UI is clean and functional, the HDR manifest schema is flexible, and
the fallback paths handle missing assets gracefully. The code has been through multiple
cleanup passes and CI is green.

The identified issues are either medium-priority cleanup tasks or future scalability
concerns that do not affect the current Model Viewer use case. None are merge blockers.

**Rationale:**

- All old "PBR preview" naming has been removed.
- The `GdxGltfRenderer` replaces the deleted `GdxGltfPbrPreviewRenderer` with cleaner
  ownership.
- Environment loading, caching, and fallback are well-structured.
- The UI correctly reflects the dual-renderer architecture.
- Documentation is thorough and honest about limitations.
- Test coverage addresses the key Model Viewer system behaviors.
- The 23 incremental reports provide good traceability for each step.

---

## Priority Checklist

### Must fix before merge
- (None — no critical blockers identified.)

### Strongly recommended soon after merge
- Extract shared manifest validation (deduplicate `validate()` logic between `HdrEnvironmentManifestLoader` and `GdxHdrEnvironmentResolver`).
- Add unit tests for `HdrEnvironmentManifestCodec` (round-trip, malformed input, missing fields).
- Split `ModelViewerPanels.kt` into multiple files (renderer options, helpers).
- Deduplicate `gltfLightDirection()` / `modelViewerLightDirection()`.

### Good future improvements
- Group `ModelViewerState` PBR/legacy fields into nested data classes.
- Clarify or remove non-functional tone-mapping UI control.
- Add async cubemap loading for Scene Player use case.
- Refactor `GdxGltfRenderer` to shared `GltfSceneManager` before Scene Editor integration.
- Add `.krmeta` support for `environment.json` for Asset Browser discovery.
- Add environment preset combo/picker to replace raw text input.
- Add LRU eviction to `GdxGltfEnvironment` preset cache.
- Normalize manifest `defaults.toneMapping` case to match `PbrToneMapping` enum.
- Consider 16-bit PNG or EXR output for IBL generators to preserve HDR range.
