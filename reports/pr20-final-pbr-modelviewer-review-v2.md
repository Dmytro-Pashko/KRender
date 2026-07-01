# PR #20 — PBR / Model Viewer Review (Re-analysis, v2)

> Second, deeper review pass. This document supersedes
> `reports/pr20-final-pbr-modelviewer-review.md` where they disagree. It corrects two
> factual errors from the first pass and adds several findings that the first pass missed.

## 0. Corrections to the first review

| First pass claimed | Actual (verified) |
|---|---|
| "source EXR files not committed in repo — gitignored or large" | **False.** Both EXR files (~41 MB total) are committed directly to git. |
| "the UI implies user-controllable tone mapping" | **Imprecise.** There is *no* tone-mapping UI control at all. `gltfToneMapping` is fully dead state. |
| Manifest schema is "well-designed, `defaults` block encodes rendering hints" | **Partly true, but** 5 of 7 `defaults` fields are decoded and never read at runtime. |

---

## 1. Scope reviewed

PR #20 (`feature/pbr_update` → `develop`): 28 Kotlin files (+2302 / −548), plus assets and
docs. Establishes glTF / PBR as the primary 3D direction. Adds HDR environment manifest v2,
a default environment, an offline IBL generator toolchain, a rewritten `GdxGltfRenderer`,
`GltfRendererSettings` render-command data, and a Model Viewer UI overhaul with dual-renderer
selection.

Files verified this pass (beyond the first review): `Render.kt` (`GltfRendererSettings`,
`PbrToneMapping`), `HdrEnvironmentManifest.kt` (data model + codec + loader, 390 lines),
`environment.json`, the committed asset tree, and cross-module usage of every
`GltfRendererSettings` field and every `HdrEnvironmentDefaults` field.

---

## 2. Overall assessment

**Verdict: mergeable, but with one thing worth deciding before merge** — the 44 MB of
binary assets committed without git-LFS. Everything else is post-merge cleanup.

The engineering is genuinely good: clean core/backend separation, a sensible
resolver→loader→cache→renderer pipeline, graceful procedural fallback, and an honest,
well-documented MVP IBL generator. But this pass surfaced a cluster of **"threaded but
inert" state** — settings and manifest fields that are plumbed end-to-end yet never
actually affect rendering. That's the main quality signal to address.

---

## 3. Strengths (confirmed)

- **Core/backend boundary is clean.** `GltfRendererSettings`, `HdrEnvironmentManifest`,
  `HdrEnvironmentAssets` live in `core` with zero `com.badlogic.gdx` imports. All GL work
  stays in `engine:backend-gdx`.
- **Render-command extension is idiomatic.** `DrawModel.gltfRenderer: GltfRendererSettings?`
  mirrors the existing `debugView` pattern; debug has priority via
  `enabled = debugView?.active != true`.
- **Preset caching + shader-config-key tracking.** `GdxGltfEnvironment` caches presets by
  name; `GdxGltfRenderer.ensureShaderConfiguration` only rebuilds the `SceneManager` when
  gamma/sRGB actually change.
- **Robust fallback chain.** BRDF LUT: manifest → `hdr/_common/brdf/` → gdx-gltf classpath.
  Missing irradiance/radiance → procedural `IBLBuilder`.
- **`warnOnce` everywhere.** Prevents per-frame log flooding on missing/broken assets.
- **Honest documentation.** `gltf-workflow.md` explicitly labels the IBL generator as an
  MVP box-blur approximation, not physically correct convolution.

---

## 4. Critical / high-priority issues

### 4.1 44 MB of binary assets committed without git-LFS — High (decide before merge)

The PR commits, directly into git history:

- `source/aerial-green-landscape-clouds_2K.exr` — 9.2 MB
- `source/aerial-green-landscape-clouds_4K.exr` — 32 MB
- 74 generated PNGs (skybox faces, 6 irradiance, 60 radiance mips, 1 shared BRDF LUT)
- `skybox/default_skybox_studio.png` — 1.2 MB

Total ≈ **44 MB**. `.gitattributes` has no LFS/filter rules; `.gitignore` does not exclude
EXR/HDR. Every clone will carry this weight permanently, and it will grow with each new
environment.

**Why it matters:** git stores binaries poorly; future environments multiply this; the
generated PNGs are *derived* artifacts reproducible from the Gradle `generateHdrEnvironment`
task.

**Options (pick one before merge):**
1. Move `assets/hdr/**/*.exr`, `*.png` under **git-LFS** via `.gitattributes`.
2. Commit only the source EXR + manifest; treat generated PNGs as build output and
   `.gitignore` them (regenerate in CI / on first run). Downside: first-run generation cost.
3. Accept the weight explicitly and document the decision. (Least preferred.)

I recommend option 1 or 2. This is the single most consequential item in the PR because it
is effectively irreversible once it lands in shared history.

### 4.2 Hand-written 390-line JSON codec duplicates kotlinx.serialization — High

`HdrEnvironmentManifestCodec` manually decodes/encodes every field via helper extensions
(`requiredString`, `requiredEnum`, etc.), even though the project already uses
kotlinx-serialization (`KRenderJson`) and the manifest data classes are plain data classes.

Problems:
- ~390 lines of hand-maintained boilerplate that must be kept in sync with the data model.
- `requiredEnum` uses `enumValueOf<T>(value)`, which throws a cryptic
  `IllegalArgumentException` (not a friendly manifest error) and is **case-sensitive** — a
  manifest `"format": "exr"` instead of `"EXR"` would crash.
- The `optionalObject` helper contains a hack:
  `takeUnless { it is JsonPrimitive && it.contentOrNull == "null" }`.

**Suggestion:** Annotate the manifest data classes with `@Serializable` and delete the
codec, keeping only `HdrEnvironmentManifestLoader.validate()` for semantic checks. This
would remove the largest single chunk of accidental complexity in the PR. If bespoke error
messages are the reason for the manual codec, they can be produced far more cheaply by
validating a deserialized object.

### 4.3 "Threaded but inert" settings — Medium/High

A cluster of settings are plumbed from UI/manifest into `GltfRendererSettings` but never
influence a pixel:

| Field | State? | Passed to settings? | UI control? | Consumed by backend? |
|---|---|---|---|---|
| `gltfToneMapping` / `PbrToneMapping` | yes | yes | **no** | **no** (0 refs in backend) |
| `gltfGammaCorrection` | yes | yes | **no** | yes (`createSceneManager`) |
| `gltfSrgbTextures` | yes | yes | **no** | yes (`createSceneManager`) |
| manifest `defaults.toneMapping` | decoded | — | — | **no** |
| manifest `defaults.gammaCorrection` | decoded | — | — | **no** |
| manifest `defaults.srgbTextures` | decoded | — | — | **no** |
| manifest `defaults.skyboxEnabled` | decoded | — | — | **no** |
| manifest `defaults.environmentRotationDegrees` | decoded | — | — | **no** |

Only `defaults.exposure` and `defaults.ambientIntensity` of the 7 manifest defaults are
actually read (in `GdxGltfRenderer.resolveEnvironmentState`).

**Impact:** Dead API surface invites incorrect assumptions. A reader will reasonably assume
`toneMapping` works, or that editing `defaults.gammaCorrection` in a manifest changes
rendering — neither is true.

**Suggestion (pick per field):**
- `toneMapping`: either wire it into the gdx-gltf shader config, or remove the enum and the
  field until it's implemented. Prefer removal — it can be re-added when real.
- `gammaCorrection` / `srgbTextures`: they *work*, so either expose them in the PBR options
  panel (they're advanced but legitimate) or add a code comment that they're intentionally
  fixed defaults.
- manifest `defaults.*`: wire the manifest defaults to *seed* `ModelViewerState`'s PBR
  settings when a preset loads (the obviously-intended behavior), or trim the unused fields
  from the schema. Right now the schema promises hints it never delivers.

### 4.4 Skybox visual does not rotate with `environmentRotationDegrees` — Medium

`applyEnvironmentRotation` sets `PBRMatrixAttribute.createEnvRotation(...)`, which rotates
IBL reflections. But `applySkybox` builds a `SceneSkybox` and never calls its rotation
setter. Result: dragging the Rotation slider rotates reflections/lighting but the visible
sky stays fixed — a confusing mismatch.

**Suggestion:** Apply the same rotation to the `SceneSkybox` (gdx-gltf `SceneSkybox`
supports a rotation), or note the limitation in the slider tooltip.

---

## 5. Medium-priority issues (confirmed from first pass, still valid)

- **Duplicated `validate()`** between `HdrEnvironmentManifestLoader` (core, `java.nio.file`)
  and `GdxHdrEnvironmentResolver` (backend, `Gdx.files`) — ~80% identical. Extract a pure
  validation over data + inject a `(String)->Boolean` existence probe.
- **Duplicated light-direction math** — `gltfLightDirection()` in `GdxGltfRenderer.kt` and
  `modelViewerLightDirection()` in `ModelViewerSystems.kt` are the same spherical formula.
  Extract one shared function.
- **Per-entity `GltfSceneManager`** in `GdxGltfRenderer` — fine for Model Viewer (1 entity),
  but multiplies shader/GPU objects in multi-entity scenes. Refactor to a shared
  `SceneManager` before Scene Editor / Scene Player PBR integration.
- **Synchronous cubemap load** — first environment load decodes/uploads ~73 PNGs on the
  render thread (60 radiance + 6 irradiance + 6 skybox + 1 LUT). One-time editor hitch is
  tolerable; add async loading via `TaskService` before Scene Player.
- **`ModelViewerState` god-object** — ~50 flat mutable fields. Group into nested
  `GltfPbrSettings`, `LegacyRendererSettings`, `DebugSettings`.
- **`ModelViewerPanels.kt` = 1348 lines** — split renderer-options panels and helper
  functions into separate files.

---

## 6. Testing gap

No unit tests were added for the new core/backend logic:

- `HdrEnvironmentManifestCodec` (390 lines, hand-written) — **untested**. Highest-value
  target: round-trip encode/decode, missing-field errors, bad enum case.
- `HdrEnvironmentManifestLoader.validate()` — **untested**.
- `HdrEnvironmentAssets.resolveRelativeToManifest` / `manifestPathForPreset` — pure,
  trivially testable, **untested**.
- IBL generators (`CubemapCrossSplitter` region math, mip sizing) — **untested**.

Only three existing tool tests were modified (`ModelViewerSystemTest`,
`ModelViewerTextureChannelResolverTest`, `AssetBrowserSystemTest`). For a change of this
size introducing a new file format, the codec and loader deserve tests.

Note: if 4.2 is adopted (delete the codec, use `@Serializable`), the codec test need
shrinks dramatically — another reason to prefer that route.

---

## 7. HDR / Environment direction (forward-looking)

The layout is a good base for a future first-class `Environment` engine asset:

- The manifest already functions as an asset descriptor.
- `GdxGltfEnvironment.preset(name)` maps naturally to `AssetRef<EnvironmentAsset>`.
- The `_common/brdf/` shared pattern and per-environment face layout are correct.
- A `.krmeta` already exists for the skybox PNG (migrated from the old
  `assets/textures/...` location), showing the asset-pipeline intent.

Before an Environment Editor:
1. Promote `HdrEnvironmentManifest` to a typed engine `EnvironmentAsset` +
   `AssetRef<EnvironmentAsset>`.
2. Generate `.krmeta` for `environment.json` so Asset Browser discovers environments.
3. Move preset resolution from the backend to a core `EnvironmentService` /
   `AssetRegistry`; backend keeps only GPU loading.
4. Support runtime environment switching (today `preset()` is load-once-cache-forever with
   no eviction).
5. Decide the generated-PNG storage policy (ties back to 4.1) — LFS vs build-output.

Also worth deciding: the IBL PNGs are 8-bit LDR, so HDR range from the source EXR is lost
during generation. Acceptable for MVP preview; a future pipeline should emit 16-bit/EXR
faces for better specular highlights.

---

## 8. Naming cleanup (low priority)

| Current | Suggested | Reason |
|---|---|---|
| `GdxGltfEnvironment` | `GdxEnvironmentPresetCache` | It's a preset cache |
| `GdxGltfEnvironmentAssetLoader` | `GdxEnvironmentPresetLoader` | Loads preset GPU resources |
| `GdxHdrEnvironmentResolver` | `GdxEnvironmentManifestResolver` | Resolves manifest paths |
| `gltf*` fields in `ModelViewerState` | drop `gltf` prefix | Redundant once PBR is the default |

Already correctly removed: `GdxGltfPbrPreviewRenderer`, `PbrPreviewView` (verified 0 refs).

---

## 9. Priority checklist

### Decide before merge
- **Binary asset strategy (4.1)** — adopt git-LFS or treat generated PNGs as build output.
  44 MB entering shared history is effectively permanent.

### Strongly recommended (soon after merge)
- Replace the hand-written codec with `@Serializable` and delete `HdrEnvironmentManifestCodec` (4.2).
- Resolve the "threaded but inert" settings: remove `toneMapping` or implement it; wire manifest `defaults.*` to seed UI, or trim them (4.3).
- Add tests for the manifest loader/codec and `HdrEnvironmentAssets` path helpers (§6).
- Deduplicate `validate()` and light-direction math (§5).

### Good future improvements
- Rotate the `SceneSkybox` with `environmentRotationDegrees` (4.4).
- Shared `GltfSceneManager` before Scene Editor PBR; async cubemap load before Scene Player (§5).
- Group `ModelViewerState` fields; split `ModelViewerPanels.kt` (§5).
- Case-insensitive enum parsing in the manifest (moot if 4.2 adopted).
- Environment preset picker instead of raw text input; LRU eviction for preset cache.
- 16-bit/EXR IBL output for better specular fidelity (§7).

---

## 10. Merge recommendation

**Approve, conditional on a decision about the 44 MB binary-asset strategy (4.1).**

The architecture is sound and the direction is right. Nothing in the code paths blocks
merge — the runtime works and fails gracefully. The two items I'd genuinely resolve before
this becomes shared history are (a) the binary-asset/git-LFS decision, because it's hard to
undo, and (b) ideally the codec simplification (4.2), because it removes the largest source
of ongoing maintenance the PR introduces. Everything else is legitimate, well-scoped
post-merge cleanup.
