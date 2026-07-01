- removed/changed Skybox category/type references
  - Removed `AssetCategory.Skybox` from `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetDomain.kt`.
  - Removed `AssetType.Skybox` from `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetDomain.kt`.
  - Added Environment-family browser types:
    - `EnvironmentSkybox`
    - `EnvironmentCubemap`
    - `EnvironmentGeneratedMap`
    - `BrdfLut`
  - Remapped legacy `.krskybox` importer output in `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetImporter.kt` to:
    - `AssetType.EnvironmentSkybox`
    - `AssetCategory.Environment`
  - Remapped `.krskybox` detection in `core/src/main/kotlin/com/pashkd/krender/engine/assets/AssetTypeDetector.kt` to the Environment category.
  - Removed standalone Skybox UI surfacing in `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/assetbrowser/AssetBrowserUiHelpers.kt`:
    - removed `[Sky]`
    - removed `AssetCategory.Skybox` from `SupportedBrowserCategories`
  - Updated `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/sceneeditor/SceneAssetPanel.kt` so the embedded scene-skybox picker now filters for `AssetType.EnvironmentSkybox` inside `AssetCategory.Environment`.

- remaining skybox runtime references, if any
  - Runtime/scene support for `.krskybox` remains in:
    - `core/src/main/kotlin/com/pashkd/krender/engine/scene/SkyboxAssetService.kt`
    - `core/src/main/kotlin/com/pashkd/krender/engine/scene/SkyboxAssetSerializer.kt`
    - `engine/scene-player/...`
    - `core/src/main/kotlin/com/pashkd/krender/engine/render3d/RuntimeEnvironmentSystem.kt`
    - `engine/backend-gdx/.../GdxSkyboxRenderer.kt`
  - Environment-domain code also still uses skybox terminology for generated resources and settings, which is expected.

- why remaining references are not Asset Browser legacy code
  - They describe runtime skybox serialization, scene playback, renderer behavior, or Environment asset generated-resource structure.
  - This step only removed the standalone Skybox browser taxonomy and remapped editor-facing asset indexing to Environment.

- compilation command
  - `./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

- compilation result
  - Passed.
