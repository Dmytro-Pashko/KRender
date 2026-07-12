# Phase 5 — PBR Preview Camera Controls

## What was changed

- Added explicit preview-camera controls to the existing Environment `Preview` panel.
- Added a `Camera Distance` slider wired to the live orbit-camera state.
- Exposed `Yaw` and `Pitch` sliders as safe additional controls.
- Kept `Auto Rotate` and `Reset Camera` behavior in place.
- Camera changes apply directly to the existing preview camera system without reloading the Environment or restarting the renderer.

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/EnvironmentPreviewPanel.kt`

## Architecture decisions

- Reused the existing transient `EnvironmentPreviewState` and `EnvironmentPreviewCameraSystem` instead of introducing a new preview-control layer.
- Kept preview-camera controls separate from all 2D resource-preview viewport state introduced in earlier phases.

## Risks / limitations

- `FOV` and explicit focus-model controls are still not exposed in the UI.
- Camera limits currently follow the existing orbit camera implementation and config defaults.

## Compilation command used

`./gradlew.bat :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

Prepare backend-neutral IBL generation design/config artifacts without adding fake generation UI.
