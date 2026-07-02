## Step

Added the first real visual Environment Preview rendering path.

## Changes

- Added `EnvironmentPreviewCamera` for orbit-style preview camera defaults and reset behavior.
- Added `EnvironmentPreviewController` for preview model and ground-plane setup.
- Added `EnvironmentPreviewCameraSystem` to drive the scene camera from preview state.
- Added `EnvironmentPreviewRenderSystem` to:
  - render the built-in `model/tests/MetalRoughSpheres.glb` preview asset
  - render a neutral dynamic ground plane
  - route the spheres through the shared glTF/PBR renderer path
- Updated `EnvironmentEditorScene` to create:
  - a dedicated preview camera
  - preview lighting for the ground plane
  - the material-spheres preview model entity
  - the preview camera/render systems
- Replaced the old text-only `EnvironmentPreviewPanel` summary with:
  - preview mode label
  - `Show Skybox`
  - `Show Ground`
  - `Auto Rotate`
  - `Reset Camera`
  - explanation that the visual preview renders in the main tool scene background

## Renderer path used

Shared glTF/PBR renderer reuse, with the visual preview rendered in the tool scene background instead of an embedded render-to-texture panel.

## MVP note

This step intentionally uses the acceptable MVP path from the task:

- real visual preview scene
- ImGui controls in the Preview panel
- no new embedded framebuffer viewport yet

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
