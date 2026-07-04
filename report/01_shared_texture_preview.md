# Phase 1 — Shared Texture Preview Layer

## What was changed

- Added a reusable `tools/common/texturepreview` package for texture preview canvas geometry, viewport state, zoom/surface modes, focus math, region hit testing, overlays, cursor inspection, and formatting.
- Refactored the existing `tools/common/canvas` helpers to delegate to the new texture preview foundation so the bitmap font editor keeps working against the same shared implementation path.
- Migrated Texture Atlas Editor preview types and math to the shared layer through package-local aliases and wrapper functions, keeping atlas-specific UI and NinePatch/font preview behavior intact.
- Switched atlas region focus logic to the shared focus computation instead of duplicating viewport-centering math.
- Reused shared checkerboard, grid, generic region bounds, and label drawing from the atlas preview overlay layer where safe.

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/common/texturepreview/*`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/common/canvas/CanvasState.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/common/canvas/CanvasViewportLayout.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/common/canvas/CanvasOverlays.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/TextureAtlasEditorModel.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/TexturePreviewViewportMath.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/TextureAtlasEditorOperations.kt`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/textureatlaseditor/ui/TextureAtlasEditorPreviewOverlays.kt`

## Architecture decisions

- Kept the shared layer in `engine:tools` under `common` so Environment Editor and other tools can reuse it without depending on `textureatlaseditor`.
- Preserved atlas-editor package APIs with thin aliases/wrappers to limit churn and reduce risk in a large existing tool.
- Left atlas-specific concerns such as NinePatch handles, packed-atlas overlays, and font glyph overlays in the atlas tool layer, while moving generic texture viewport math and generic region drawing into shared code.
- Reused the existing backend-neutral preview model and did not introduce any new GDX imports.

## Risks / limitations

- The shared overlay layer currently covers the generic region workflow; atlas-specific overlays still live in the atlas editor and can be extracted later if more tools need them.
- `tools/common/canvas` now forwards to the new shared logic, which is low risk but means future changes should be made in `common/texturepreview` first.
- No manual runtime verification was performed in a live desktop OpenGL session during this phase.

## Compilation command used

`./gradlew.bat :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

Build Phase 2 by adding an Environment Resource Inspector panel and state/controller helpers on top of the shared texture preview layer.
