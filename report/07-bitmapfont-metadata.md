# Step 07 — Add bitmap font metadata asset

## Summary
Added `.kfont.json` as editor metadata for generated Bitmap Font assets. Supports load/save
with manual `kotlinx.serialization.json` API consistent with existing KRender conventions.

## Files changed
- **Created** `engine/tools/.../bitmapfonteditor/BitmapFontEditorMetadata.kt` — `BitmapFontEditorMetadata`, `BitmapFontGenerationMetadata`, `BitmapFontEditorMetadataCodec` (encode/decode/save/load).
- **Modified** `engine/tools/.../bitmapfonteditor/BitmapFontEditorState.kt` — added `metadata` and `metadataPath` fields.
- **Modified** `engine/tools/.../bitmapfonteditor/workflow/OpenBitmapFontWorkflow.kt` — added `.kfont.json` dispatch: loads metadata, then opens generated `.fnt` if available.
- **Modified** `engine/tools/.../assetbrowser/AssetBrowserScene.kt` — `BitmapFontEditorAssetTool.canOpen()` now also matches `.kfont.json` paths.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- Uses `KRenderJson.Pretty` for human-readable JSON output.
- Codec uses `buildJsonObject`/`parseToJsonElement` — no `@Serializable` compiler plugin required.
- Metadata stores project-relative source font, output paths, and generation config.
- If `.kfont.json` references an existing `.fnt`, it is opened and previewed automatically.
