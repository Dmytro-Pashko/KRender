# UI System — Agent Deep Dive

> Supplements `AGENTS.md` §10. KRender has two separate UI stacks: the ImGui **editor** UI and the
> Scene2D **runtime** UI, plus a shared `.krui` document model.

## 1. Editor UI (ImGui) — `engine.ui.editor`

Core contract: `core/.../engine/ui/editor/Ui.kt`. Backend: `GdxImGuiService`
(`engine/backend-gdx/.../engine/backend/gdx/GdxImGuiService.kt`).

- **`UiContext`/`UiService`**: `beginFrame(delta)`, `endFrame()`, `render()`, `resize()`,
  `captureState: UiCaptureState` (mouse/keyboard capture), `drawTexturePreview(handle, w, h)`.
- **`NoOpUiService`**: used on Android (no ImGui).
- **Frame boundary is owned by `GameLoop`**, not panels: `ui.beginFrame` runs **before** input
  sampling (so `wantCaptureMouse`/`wantCaptureKeyboard` are current that frame) and `ui.endFrame`
  runs after scene systems draw. **Never call `beginFrame`/`endFrame` from a panel or scene.**
- **`UiPanel`** (fun interface): `draw()`. **`UiSystem`** is a `System` that holds panels and calls
  `draw()` on each during `update`. Tools build it in `createUiSystem(...)` and `addPanel(...)`.
- **Input capture** flows into `InputSnapshot.uiCapturesMouse/Keyboard`
  (`GdxInputService.setUiCaptureProvider`), so systems can ignore input the UI consumed
  (`snapshot.isCapturedByUI()`).
- **Layout is data-driven**: `ImGuiLayoutConfig` + `ImGuiLayoutConfigLoader` (loads from an asset
  path with a fallback config) + `ImGuiLayoutRuntimeTracker` (tracks live window placement);
  `ImGuiWindowEventLogger` logs panel window events. Each tool ships a `*UiLayoutDefaults`.
- **Texture previews**: `AssetService.texturePreviewHandle(pathOrId)` → opaque
  `TexturePreviewHandle` → `UiContext.drawTexturePreview(...)`. Never pass raw GL ids.
- **Shared panel**: `LogsPanel` renders `engine.logs` inside every tool.

### Editor panel rules
- Panels receive plain state objects + operation handlers; they must not touch `Gdx.*`, the
  renderer, or asset loading directly.
- Put behavior in systems/operations; keep ImGui calls inside panels.
- Wrap panels with error logging as existing tools do (`addPanel` helpers in tool scenes).

## 2. Runtime UI (Scene2D) — `engine.ui.runtime`

Core: `RuntimeUiService` (`core/.../engine/ui/runtime/RuntimeUiService.kt`) wrapping a
`RuntimeUiBackend`. Backend: `GdxRuntimeUiBackend`
(`engine/backend-gdx/.../engine/backend/gdx/ui/runtime/`), with app-provided runtime UI actor factories and
`GdxUiSceneBuilder` building Scene2D from `.krui`.

- Driven by the loop separately from the editor UI: `runtimeUi.update(delta)` then
  `runtimeUi.render()`.
- `RuntimeUiBindingContract`/`RuntimeUiBindingException` define how UI binds to game state/actions.
- This is **in-game** UI (HUD/menus), not editor tooling — keep the two stacks separate.

## 3. Shared `.krui` model — `engine.ui.scene`

- `UiSceneDocument` (model), `UiSceneSerializer` (load/save), `UiSceneValidation` +
  `engine/ui/scene/validation/*` (rule-based validators: node id uniqueness, shape, style/texture/
  binding references, placeholder syntax, metadata, type compatibility).
- **Shared by both runtime UI and the UI Composer editor.** Changes here affect both — keep them
  backward-compatible and run `UiSceneSerializerTest` + `UiSceneFocusedValidatorsTest` +
  `UiSceneAssetIntegrationTest`.
- The UI Composer additionally uses a backend Scene2D preview (`GdxUiScenePreview`) drawn via the
  `Scene.overlayRender` hook — see `docs/agents/tools/ui-composer.md`.

## Quick decision guide

| You want to… | Use |
|---|---|
| Add an editor panel to a tool | `UiPanel` + `UiSystem.addPanel` in the tool's `createUiSystem` |
| Show an in-game HUD/menu | `RuntimeUiService` (Scene2D) |
| Change the `.krui` document schema | `engine.ui.scene` model + serializer + validators (affects runtime + composer) |
| Preview a texture in a panel | `AssetService.texturePreviewHandle` → `drawTexturePreview` |
