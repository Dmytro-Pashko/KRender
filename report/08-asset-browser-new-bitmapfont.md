# Step 08 — Create bitmap font assets from asset browser

## Summary
Asset Browser can now create a new empty Bitmap Font asset (`.kfont.json`) and open it in BitmapFontEditor.

## Files changed
- **Created** `engine/tools/.../assetbrowser/creation/BitmapFontAssetCreation.kt` — `createBitmapFontAsset()` creates `.kfont.json` with default generation config and launches BitmapFontEditor.
- **Modified** `engine/tools/.../assetbrowser/Scene2DSkinSelection.kt` — added `CreatableAssetKind.BitmapFont`, default base name, default params display.
- **Modified** `engine/tools/.../assetbrowser/AssetBrowserScene.kt` — added BitmapFont creation dispatch in `SceneOperationsHandler.create()`.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Default target directory: `ui/fonts/`.
- Default generation config: 24px, English+Symbols+Ukrainian Cyrillic, 512x512 page, padding=2, spacing=1.
- After creation, BitmapFontEditor is launched automatically with the new `.kfont.json` path.
- The created `.kfont.json` has empty `sourceFont` — user must configure it in the generation panel.
