# Phase 4 — Skybox Atlas Importer / Splitter

## What was changed

- Added a `Skybox Import` workflow inside `Resource Inspector` when `Imported Skybox Source` mode is active.
- Added import state for:
  - source texture path
  - layout preset
  - output directory
  - selected face
  - per-face regions
  - per-face rotate/flip metadata
- Added layout presets and default region generation for:
  - `CubeCross4x3`
  - `HorizontalRow6x1`
  - `VerticalColumn1x6`
  - `Custom`
- Added numeric region editing for selected face `x / y / width / height`.
- Added rotate and flip controls to the import model and applied them during export.
- Updated `Imported Skybox Source` mode so the main `Resource Inspector` canvas now shows the full source texture with overlaid face regions instead of an empty state when a previewable source texture path is configured.
- Updated `Selected Resource Preview` so selected imported-source faces are cropped from the source atlas using UV/region metadata.
- Added a real split/export service that crops face images, writes separate PNG files, and updates `environment.skybox.faces`.
- Manifest updates now write separate runtime face files under the configured output directory and set:
  - `layout = "SixFaces"`
  - `format = "PNG"`

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourcePreviewModel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceFaceResolver.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourcePreviewController.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceInspectorPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/SkyboxAtlasImportModel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/SkyboxAtlasLayoutResolver.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/SkyboxAtlasImportService.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/SkyboxAtlasImportController.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorUiFactory.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`

## Architecture decisions

- Kept crop/write/image-transform logic entirely outside ImGui code in `SkyboxAtlasImportService`.
- Kept layout inference separate in `SkyboxAtlasLayoutResolver` so future importer workflows can reuse it.
- Reused the existing Resource Inspector + Selected Resource Preview panels instead of creating a parallel import-only preview stack.
- Preserved the runtime separate-face-file decision by exporting only six individual face files and updating the Environment manifest accordingly.

## Risks / limitations

- The importer currently targets previewable 2D texture sources (`png/jpg/jpeg/webp`) and does not split HDR/EXR sources directly.
- Region editing is numeric-only in this phase; drag/resize handles are not implemented yet.
- Output naming is fixed to `<outputDirectory>/<face>.png` for now.
- Rotation/flip transforms are implemented during export, but there is no advanced visual gizmo for transform editing yet.

## Compilation command used

`./gradlew.bat :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

Add the requested PBR preview camera control improvements, starting with explicit camera distance control in the preview panel.
