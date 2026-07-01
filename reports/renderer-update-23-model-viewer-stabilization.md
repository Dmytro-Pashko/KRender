# Step 23 — Model Viewer stabilization

## Goal

Verify the post-PBR Model Viewer layout/controls are coherent, keep only useful live controls, and remove leftover misleading “preview” wording from the primary renderer flow.

## Files changed

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerPanels.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerState.kt`

## Architecture notes

- Kept the four-section Viewport Control layout intact:
  - Camera Control
  - Display Mode / Display Options
  - Renderer Selector
  - Renderer Options
- Confirmed the glTF / PBR renderer remains the default mode through `DEFAULT_MODEL_VIEWER_RENDERER_MODE`.
- Kept renderer-specific controls isolated to the selected renderer panel.
- Removed leftover “preview” wording from user-facing Model Viewer strings so the UI matches the new main-renderer direction.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :engine:tools:compileKotlin`
- Result: Passed

## Commit

- Hash: `5d86a392`
- Message: `model-viewer: finalize renderer ui stabilization`
