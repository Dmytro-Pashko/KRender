# Scene Editor — Agent Context

Read `AGENTS.md` first. The Scene Editor opens an existing `.krscene` chosen in Asset Browser. `ToolsModule` requires `krender.scene.path`; there is no empty-document route.

## Document and runtime

- `SceneEditorScene` owns the editor world; `SceneEditorDocument` owns a separate world for authored entities. The editor camera and terrain preview are never serialized.
- `.krscene` schema v2 stores one optional Terrain under `settings.terrain`, including asset path, visibility, preview mode, baked resolution and legacy transform values. Scene Player and Woolboy create a runtime Terrain entity from these settings. The Terrain file selects its material library; the scene's existing material library path is only a fallback for older Terrain files.
- Texture preview baking uses `engine.terrainTextureSamplerFactory` through `SceneEditorDocumentTerrainSyncSystem`. Without that sampler, the bake falls back to material colors instead of sampling albedo textures.
- `SceneSerializer.decode` migrates a v1 terrain entity. A legacy file with multiple Terrain entities is rejected for manual migration.
- The authored active camera remains in scene settings. A host scene may override it after loading.
- `SceneComponentRegistry` in core defines serializable component types, factories, codecs, validation and backend-neutral field descriptions. `SceneComponentFieldRenderer` maps field kinds to ImGui controls; custom editors may be registered for complex types. Components contain data, while systems implement behavior.

## Panels

`Scene Editor Control` offers Save, Reload, Play and UI layout controls. Reload prompts for Save, Discard or Cancel when there are unsaved changes. `Scene Hierarchy` adds Empty, Model, Camera, Directional Light and Point Light entities, and offers Duplicate and Delete. `Scene Inspector` edits scene settings and diagnostics, with one optional Terrain, Color/Texture preview and preset bake resolutions. `Entity Properties` edits the selected entity and addable components. `Scene Viewport` and `LogsPanel` remain.

The old embedded Assets panel is removed. `SceneEditorAssetCatalog` performs an initial background scan of the shared `AssetRegistryService`; compact asset selectors in the panels consume its options. Preserve the scan/apply main-thread boundary.

## Safe change rules

- Keep the editor and document worlds distinct. Only authored entities are serialized. The generated Terrain preview is tagged `EditorOnlyComponent` and excluded from picking and persistence.
- Extend `SceneComponentRegistry` for a new serializable component, then add a specialized field editor only when the generic typed controls are insufficient. Update runtime systems, dependency collection and validation when the component introduces a new asset or behavior.
- Save through `engine.sceneFiles`. Retain unknown component descriptors when saving an edited entity, and never silently discard legacy Terrain data.
- Preserve the core/backend boundary and system order in `SceneEditorScene.show()`.
