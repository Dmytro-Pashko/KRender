# Phase 3 — Selected Region Preview Panel

## What was changed

- Added a new `Selected Resource Preview` panel to Environment Editor.
- Added a second, independent texture-preview viewport state dedicated to the selected resource preview.
- Added a selected-resource preview controller for separate fit/reset/zoom/pan behavior.
- The panel now shows the currently selected face/resource from `Resource Inspector` in its own viewport instead of embedding selected-item rendering into the main inspector canvas.
- Added selected-resource detail output for:
  - resource kind
  - face
  - radiance mip level
  - roughness
  - source path
  - resolved path
  - region rect or full-face state
  - UV range
  - texture size
  - selected-item warnings
- Added selected-preview warning aggregation for:
  - missing file
  - non-square cubemap face
  - face size mismatch
  - region outside bounds when source-region data exists

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceInspectorState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourcePreviewModel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentResourceDiagnostics.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentSelectedResourcePreviewController.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentSelectedResourcePreviewPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorLayout.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorUiFactory.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentEditorScene.kt`

## Architecture decisions

- Kept selected-preview camera state separate from the main resource-inspector camera state.
- Reused the same resource model and selection state from the inspector rather than duplicating manifest resolution.
- Added optional source-region metadata to the shared Environment resource item model so Phase 4 atlas/cross/row import can feed cropped previews into this panel without redesigning the data flow.

## Risks / limitations

- Current selected-preview cropping is full-face only because imported atlas/cross/row source regions are not added until Phase 4.
- Warnings such as region-outside-bounds are wired for future source-region workflows, but most current runtime face resources naturally show full-face previews.
- No live desktop UI/manual GL verification was run in this phase.

## Compilation command used

`./gradlew.bat :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

Add the skybox atlas importer/splitter workflow and feed atlas face regions into the main inspector plus selected preview panel.
