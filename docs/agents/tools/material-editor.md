# Material Editor — Agent Context

The Material Editor opens a JSON library under `assets/materials/` in its own desktop process. Asset Browser routes Material assets to `material-editor` with `krender.material.path`.

The UI edits the existing `formatVersion: 1` structure: each material has `id`, `name`, `albedoTexture`, `fallbackColor`, and `defaultTiling`. Textures remain asset paths within each material; there is no separate texture catalogue. The editor loads texture choices from the asset registry.

Saving validates IDs, fields, and texture paths. When an ID changes, the controller scans terrain assets and scene bindings, then updates terrain layer references only for files using this library. Ambiguous legacy bindings block the save. The library and affected terrain files are staged before replacement; failed replacement attempts restore earlier files.

Terrain files can store `materialLibraryPath`. Scene Player and Scene Editor use that path ahead of the scene setting. Older terrain files without it use the scene setting or the default library path.
