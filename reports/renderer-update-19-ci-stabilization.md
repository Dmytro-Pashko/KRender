# Step 19 — CI Stabilization

## Goal

Fix the failing PR #20 quality checks first by reproducing the failures locally and resolving the actionable `detekt`, `ktlint`, and formatting issues without running the full test suite.

## Files changed

- `core/src/main/kotlin/com/pashkd/krender/engine/assets/hdr/HdrEnvironmentManifest.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxGltfPbrPreviewRenderer.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxHdrEnvironmentResolver.kt`
- `engine/backend-gdx/src/main/kotlin/com/pashkd/krender/engine/backend/gdx/GdxImGuiService.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerPanels.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerSystems.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/ui/SkinEditorToolbarPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/ui/TextureAtlasEditorInspectorPanel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/ui/TextureAtlasEditorPreviewCanvasPanel.kt`
- `engine/tools/src/test/kotlin/com/pashkd/krender/engine/tools/assetbrowser/AssetBrowserSystemTest.kt`
- `engine/tools/src/test/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerSystemTest.kt`
- `engine/tools/src/test/kotlin/com/pashkd/krender/engine/tools/modelviewer/ModelViewerTextureChannelResolverTest.kt`

## Architecture notes

- Split large HDR manifest encode/decode paths into focused helpers so manifest v2 support stays readable and `detekt`-clean.
- Reduced `GdxGltfPbrPreviewRenderer` environment setup complexity without changing its runtime behavior.
- Split large Model Viewer and Skin Editor UI draw methods into smaller sections to remove `LongMethod`/complexity debt introduced during the renderer transition.
- Resolved backend formatting/style issues and kept the user-owned `assets/ui/model_viewer_layout.json` change untouched.

## Validation

- Command: `./gradlew.bat --no-daemon --console=plain :core:ktlintMainSourceSetCheck :engine:backend-gdx:ktlintMainSourceSetCheck :engine:tools:ktlintMainSourceSetCheck`
- Result: Passed

- Command: `./gradlew.bat --no-daemon --console=plain detekt`
- Result: Passed

- Command: `./gradlew.bat --no-daemon --console=plain :core:compileKotlin :engine:backend-gdx:compileKotlin :engine:tools:compileKotlin`
- Result: Passed

## Commit

- Hash: `<pending>`
- Message: `build: fix renderer update CI failures`
