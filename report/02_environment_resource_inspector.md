# Phase 2 — Environment Resource Inspector MVP

## What was changed

- Replaced the old read-only Environment `Tools` overview with a new `Resource Inspector` panel.
- Added Environment Editor resource-inspection state for:
  - selected resource mode
  - selected face
  - selected radiance mip
  - hovered region/face
  - selected region/face
  - dedicated 2D preview state
- Added a resolver/controller/model flow that:
  - resolves skybox faces directly from manifest face entries
  - infers irradiance cubemap faces from manifest path patterns and sibling-file naming
  - resolves radiance faces per selected mip
  - resolves BRDF LUT preview data
  - queues previewable textures through `AssetService`
  - reads texture metadata backend-neutrally for size/format reporting
- Added an interactive resource canvas with face selection, hover highlighting, zoom/pan, checkerboard/grid/bounds toggles, and selected-resource details.
- Added `Imported Skybox Source` mode with an explicit empty-state fallback when no previewable imported source is available yet.

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorLayout.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorUiFactory.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceInspectorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourcePreviewModel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceFaceResolver.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourcePreviewController.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceInspectorPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceDiagnostics.kt`

## Architecture decisions

- Kept path resolution and face inference out of ImGui code by introducing resolver/controller/model classes.
- Reused the shared texture preview foundation from Phase 1 instead of pulling logic from `textureatlaseditor`.
- Kept resource-inspection state fully separate from the existing PBR preview camera state.
- Stayed backend-neutral in the tool logic by using `AssetService`, `TexturePreviewHandle`, `EnvironmentPathResolver`, and `TextureMetadataReader`.

## Risks / limitations

- `Imported Skybox Source` is still an MVP empty-state path for non-previewable HDR/EXR sources and does not yet support atlas/cross/row region overlays.
- The current radiance resolver follows existing manifest/runtime naming expectations but does not yet expose mismatch warnings beyond missing-file diagnostics.
- This phase adds an interactive main resource canvas, but the isolated selected-region viewport is intentionally deferred to Phase 3.

## Compilation command used

`./gradlew.bat :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

Add a separate `Selected Resource Preview` panel with its own independent viewport and selected-item diagnostics.
