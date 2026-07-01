# Step 20 — Renderer Cleanup

## Goal

Remove preview-era naming from the main glTF renderer path so the code reflects the current architecture: glTF/PBR is the primary renderer direction and Legacy remains an explicit fallback mode.

## Files changed

- `core/src/main/kotlin/com/pashkd/krender/engine/api/Render.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfRenderer.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxRenderer3D.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerSystems.kt`

## Architecture notes

- Renamed the backend-neutral draw payload from `PbrPreviewView` to `GltfRendererSettings`.
- Renamed the renderer-side implementation from `GdxGltfPbrPreviewRenderer` to `GdxGltfRenderer`.
- Updated `DrawModel` to carry `gltfRenderer` settings instead of a preview-only field name.
- Adjusted user-facing warnings/logging so they describe the glTF renderer path instead of a temporary preview mode.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`
- Result: Passed

- Command: `./gradlew.bat --no-daemon --console=plain detekt`
- Result: Passed

## Commit

- Hash: `<pending>`
- Message: `renderer: clean preview naming and legacy fallback paths`
