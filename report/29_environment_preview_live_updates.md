## Step

Made the environment preview explicitly live-update from the current editor state.

## Changes

- Added `EnvironmentPreviewLiveUpdateSystem`.
- Added `EnvironmentPreviewController.liveStatusMessage(...)`.
- Wired the live-update system into `EnvironmentEditorScene` before the UI system.

## Live-update behavior

- The preview renderer settings are rebuilt from `EnvironmentEditorState.environment` each render.
- The preview status message is refreshed from the current editor state each update.
- No event bus was added.
- No reopen is required for the preview to react to:
  - `exposure`
  - `rotationDegrees`
  - `skyboxVisible`
  - `skyboxIntensity`
  - `diffuseIntensity`
  - `specularIntensity`
  - `backgroundMode`

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
