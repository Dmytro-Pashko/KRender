# Phase 7 — Final Environment Editor V2 Summary

## What was changed

- Completed the shared texture preview extraction and atlas-editor migration.
- Added the Environment `Resource Inspector` panel with mode-based inspection for skybox, irradiance, radiance, BRDF LUT, and imported source data.
- Added the separate `Selected Resource Preview` panel with its own viewport state and selected-item diagnostics.
- Added skybox source import/splitting with preset region layouts, numeric region editing, rotate/flip metadata, PNG face export, and manifest face updates.
- Added PBR preview camera distance, yaw, and pitch controls.
- Added backend-neutral IBL generation design contracts in `core`.
- Updated the directly relevant Environment Editor docs and phase reports.

## Main files touched

- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/common/texturepreview/*`
- `engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/environmenteditor/*`
- `core/src/main/kotlin/com/pashkd/krender/engine/assets/environment/EnvironmentIblGeneration.kt`
- `docs/agents/tools/environment-editor.md`
- `docs/tools.md`
- `report/01_shared_texture_preview.md`
- `report/02_environment_resource_inspector.md`
- `report/03_selected_region_preview_panel.md`
- `report/04_skybox_import_splitter.md`
- `report/05_pbr_preview_camera_controls.md`
- `report/06_environment_ibl_generation_design.md`

## Architecture decisions

- Kept all reusable preview math and overlays in `engine/tools/common/texturepreview`.
- Kept Environment Editor tool logic backend-neutral and avoided new casual GDX imports.
- Kept selected-resource preview logic separate from the main resource-inspector canvas.
- Preserved the runtime separate-face-file rule for skybox and future IBL outputs.
- Added only backend-neutral IBL generation contracts, without fake UI actions.

## Risks / limitations

- Imported skybox source splitting currently targets previewable 2D texture sources, not HDR/EXR sources.
- Import region editing is numeric-only and does not yet provide drag/resize handles.
- Output naming for split skybox faces is currently fixed to `<outputDirectory>/<face>.png`.
- Irradiance/radiance/BRDF LUT generation is still design-only and has no runtime/editor implementation yet.
- Manual GL/UI verification was not performed during this task; validation was compile-only per request.

## Compilation command used

`./gradlew.bat :core:compileKotlin :engine:tools:compileKotlin`

## Compilation result

Success.

## Next step

- Add drag/resize region handles for imported skybox source editing.
- Extend imported-source support to HDR/EXR workflows when a real preview/import path exists.
- Implement real irradiance/radiance/BRDF LUT generation behind the new core service contract.
- Add manual desktop verification and screenshots for the updated Environment Editor.

## Completed phases

- Phase 1 — Shared texture preview extraction
- Phase 2 — Environment resource inspector MVP
- Phase 3 — Selected region preview panel
- Phase 4 — Skybox atlas importer / splitter
- Phase 5 — PBR preview camera controls
- Phase 6 — Environment IBL generation design
- Phase 7 — Docs and final cleanup

## Commits

- `31586b4e` — `Environment Editor V2: extract shared texture preview layer`
- `e682512f` — `Environment Editor V2: add resource inspector MVP`
- `ea49ca79` — `Environment Editor V2: add selected region preview panel`
- `d69bd58e` — `Environment Editor V2: add skybox atlas importer`
- `7f56383c` — `Environment Editor V2: add PBR preview camera controls`
- `7357048b` — `Environment Editor V2: design IBL generation pipeline`
