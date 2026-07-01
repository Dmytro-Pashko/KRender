# Step 00 — Environment Editor Baseline Inspection

## Goal

Prepare branch and inspect existing renderer/HDR/tooling architecture before modifying code.

## Branch

`feature/envinroment_editor_tool`

## Files / Modules Inspected

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentManifest.kt` — existing HDR manifest model, loader, codec, validation
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentAssets.kt` — HDR preset resolution helpers
- `core/src/main/kotlin/com/pashkd/krender/engine/api/Render.kt` — `GltfRendererSettings`, `ApplyEnvironment` render command
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetDomain.kt` — `AssetCategory`, `AssetType`, `AssetDescriptor`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetTypeDetector.kt` — extension-based asset type detection
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetImporter.kt` — `AssetImporter` interface, `AssetImporterRegistry`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetRegistryService.kt` — asset indexing
- `core/src/main/kotlin/com/pashkd/krender/engine/scene/EditorToolLauncher.kt` — tool launcher interface
- `core/src/main/kotlin/com/pashkd/krender/engine/scene/SceneFileService.kt` — file IO abstraction
- `core/src/main/kotlin/com/pashkd/krender/engine/api/EngineRuntime.kt` — `EngineContext`, `EngineBackend`
- `engine/tools/src/main/kotlin/.../tools/ToolsModule.kt` — scene name → Scene routing
- `engine/tools/src/main/kotlin/.../tools/assetbrowser/AssetToolRegistry.kt` — `AssetTool` interface, registry
- `engine/tools/src/main/kotlin/.../tools/assetbrowser/AssetBrowserScene.kt` — tool registration, asset tools
- `engine/tools/src/main/kotlin/.../tools/modelviewer/ModelViewerScene.kt` — reference tool scene pattern
- `engine/tools/src/main/kotlin/.../ui/editor/ToolUi.kt` — `UiPanel`, `UiSystem`
- `desktop-lwjgl3-macos/src/.../Lwjgl3EditorToolLauncher.kt` — JVM process-based tool launching
- `assets/hdr/default/environment.json` — existing HDR environment manifest (schema `krender.hdr-environment` v2)

## Existing HDR / Environment-Related Classes

| Class | Location | Purpose |
|---|---|---|
| `HdrEnvironmentManifest` | `core/.../assets/hdr/` | Serializable manifest model (schema v2) |
| `HdrEnvironmentSource` | same | Source variants with active variant selection |
| `HdrEnvironmentSourceVariant` | same | Per-variant path, format, projection, resolution |
| `HdrSkyboxConfig` | same | Skybox cubemap cross config |
| `HdrIrradianceConfig` | same | Irradiance cubemap faces |
| `HdrRadianceConfig` | same | Radiance mip chain faces |
| `HdrBrdfLutConfig` | same | Shared BRDF LUT reference |
| `HdrEnvironmentDefaults` | same | Runtime defaults (exposure, tone mapping, etc.) |
| `HdrEnvironmentManifestLoader` | same | Load + validate manifest from `Path` |
| `HdrEnvironmentManifestCodec` | same | JSON encode/decode via kotlinx.serialization |
| `HdrEnvironmentAssets` | same | Preset resolution, path helpers |
| `GltfRendererSettings` | `core/.../api/Render.kt` | Backend-neutral PBR renderer config |
| `ApplyEnvironment` | same | Backend-neutral environment render command |

## Existing Tool Registration Pattern

1. **Scene routing**: `ToolsModule.createScene()` maps scene name strings to `Scene` subclasses.
2. **Asset Browser tool routing**: `AssetToolRegistry` holds `AssetTool` implementations. Each tool declares `supportedCategories` and opens assets via `EditorToolLauncher`.
3. **EditorToolLauncher**: Interface in `core/.../scene/`, implemented by `Lwjgl3EditorToolLauncher` in desktop launchers. Launches tools as separate JVM processes with system properties.
4. **Panel pattern**: `UiPanel` fun interface with `draw()`. Panels are added to `UiSystem` which calls `draw()` each frame.

## Existing Asset Browser Recognition Pattern

- `AssetTypeDetector.detect()` uses file extension and path prefix to classify assets.
- `AssetImporterRegistry` resolves importers by extension; registry fallback uses `AssetTypeDetector`.
- Current categories: Model, Texture, Skybox, Material, Terrain, Scene2D, UI, Scene, Other.
- No `Environment` category or `.environment.json` / `.exr` / `.hdr` recognition exists yet.

## Validation

Command:

```bash
./gradlew :core:compileKotlin :engine:tools:compileKotlin --no-daemon -q
```

Result:

```text
SUCCESS (exit code 0)
```

## Notes

- The new Environment domain model should live in `core/.../engine/assets/environment/` alongside the existing `hdr/` package.
- The new Environment Editor tool scene should follow the ModelViewerScene pattern: Scene subclass + State + UiSystem + panels.
- `EditorToolLauncher` needs a `launchEnvironmentEditor()` method.
- `ToolsModule` needs an `"environment-editor"` scene routing entry.
- `AssetCategory` and `AssetType` enums need new entries.
- `AssetTypeDetector` needs `.environment.json`, `.exr`, `.hdr` recognition.
- `AssetImporterRegistry` needs an `EnvironmentImporter`.
- Existing `HdrEnvironmentManifest` uses `kotlinx.serialization` — new manifest should follow same pattern.
- `SceneFileService` provides the file IO abstraction for reading/writing manifests.

## Limitations / Follow-ups

- Branch name has a typo (`envinroment` vs `environment`); proceeding as-is since user confirmed checkout.
- No `report/` directory existed; created it in this step.
