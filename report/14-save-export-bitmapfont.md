# Step 14 — Save generated bitmap font assets

## Summary
BitmapFontEditor can now save/export generated bitmap font assets (.fnt + .png + .kfont.json).

## Files changed
- **Created** `engine/tools/.../bitmapfonteditor/workflow/SaveBitmapFontWorkflow.kt` — saves .fnt, .png, .kfont.json.
- **Modified** `engine/tools/.../bitmapfonteditor/BitmapFontEditorController.kt` — added `save()` method.
- **Modified** `engine/tools/.../bitmapfonteditor/panels/BitmapFontToolbarPanel.kt` — added Save button and dirty indicator.

## Compilation
```
./gradlew :engine:tools:compileKotlin
```
Result: **SUCCESS** (exit code 0).

## Notes
- .fnt page path is relative to the .fnt file (just filename).
- .kfont.json is updated with final output paths.
- Source font path remains project-relative in metadata.
- Dirty state is cleared after successful save.
- Overwrites existing files on save.
