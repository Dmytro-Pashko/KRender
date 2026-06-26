# Step 06 — Open bitmap fonts from asset browser

## Summary
Asset Browser can now open .fnt files with BitmapFontEditor.

## Files changed
- **Modified** `engine/tools/.../assetbrowser/AssetBrowserScene.kt` — added `BitmapFontEditorAssetTool` and registered it.
- **Modified** `core/.../assets/AssetCapabilities.kt` — changed `canOpenWith` from `false` to `true` for `AssetType.Font`.

## Compilation
```
./gradlew :core:compileKotlin :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- `BitmapFontEditorAssetTool` only matches `.fnt` files (`AssetType.Font` + extension check).
- `.ttf`/`.otf` files are also `AssetType.Font` but are not matched by this tool (they have no `.fnt` to open).
- Existing Texture Atlas Editor import behavior is preserved — Texture Atlas Editor doesn't register as an `AssetTool` for `.fnt` files.
- `canOpenWith = true` for all Font assets enables the context menu "Open With" option.
