## Step

Added the initial Environment Preview state model.

## Changes

- Added `EnvironmentPreviewMode` with the single `MaterialSpheres` mode.
- Added `EnvironmentPreviewState` with:
  - `mode`
  - `showSkybox`
  - `showGround`
  - `autoRotate`
  - `cameraDistance`
  - `cameraYawDegrees`
  - `cameraPitchDegrees`
  - `previewStatusMessage`
- Wired a shared `previewState` instance into `EnvironmentEditorState`.

## Notes

- The state is owned by the editor state so both the preview panel and scene systems can read/write the same values.
- No future preview modes were added in this step.

## Compilation command

`./gradlew :engine:tools:compileKotlin --no-daemon`

## Result

Compilation succeeded for `:engine:tools:compileKotlin`.
