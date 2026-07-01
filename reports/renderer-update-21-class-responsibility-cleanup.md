# Step 21 — Class Responsibility Cleanup

## Goal

Reduce mixed responsibilities in the HDR/glTF renderer path without overengineering, so environment preset caching and GPU asset loading have cleaner boundaries.

## Files changed

- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfEnvironment.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfEnvironmentAssetLoader.kt`

## Architecture notes

- `GdxGltfEnvironment` now focuses on preset resolution/caching only.
- Added `GdxGltfEnvironmentAssetLoader` to own cubemap, radiance, and BRDF LUT loading from a resolved HDR environment descriptor.
- This keeps future environment-asset evolution easier, because manifest resolution and GPU resource loading are now separate seams.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :engine:backend-gdx:ktlintMainSourceSetCheck`
- Result: Passed

- Command: `./gradlew.bat --no-daemon --console=plain :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`
- Result: Passed

## Commit

- Hash: `af3ddef8`
- Message: `renderer: split gltf environment and renderer responsibilities`
