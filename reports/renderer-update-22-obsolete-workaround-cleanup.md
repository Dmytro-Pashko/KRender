# Step 22 — Obsolete workaround cleanup

## Goal

Remove stale preview-era naming and leftover renderer-path identifiers so the glTF/PBR path reads like the primary renderer flow instead of a temporary preview workaround.

## Files changed

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerPanels.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerSystems.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxRenderer3D.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfRenderer.kt`

## Architecture notes

- Renamed Model Viewer renderer mode/state from generic `Pbr`/`pbr*` naming to `GltfPbr`/`gltf*` where the state now clearly belongs to the main glTF renderer path.
- Renamed backend cache/helper identifiers from `Pbr*` to `Gltf*` to reduce confusion between shading model terminology and the actual renderer backend.
- Kept runtime fallback behavior unchanged; this step only removes stale naming/workaround residue and makes the canonical path clearer.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`
- Result: Passed
- Command: `./gradlew.bat --no-daemon --console=plain :engine:backend-gdx:ktlintMainSourceSetCheck :engine:tools:ktlintMainSourceSetCheck`
- Result: Passed

## Commit

- Hash: `0185eb5b`
- Message: `cleanup: remove obsolete pbr and skybox workarounds`
