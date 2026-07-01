# Step 05 — Asset Browser Environment Support

## Goal

Make Environment assets visible and openable in Asset Browser.

## Changes

- `AssetTypeDetector` already recognized `.environment.json` → `Environment` and `.exr`/`.hdr` → `HdrSource`
- `AssetCategory.Environment` and `AssetType.Environment`/`HdrSource` already existed in `AssetDomain.kt`
- Added `EnvironmentEditorAssetTool` in `AssetBrowserScene.kt` — opens `.environment.json` in Environment Editor
- Registered `EnvironmentEditorAssetTool` in `AssetBrowserScene.show()`
- Added `[Env]` icon and `Environment` to `SupportedBrowserCategories` in `AssetBrowserUiHelpers.kt`
- Added `launchEnvironmentEditor` to all 3 desktop `Lwjgl3EditorToolLauncher` implementations
- Added `environmentPath` parameter to `ToolsModule.createScene()` with `"environment-editor"` route
- Added `configuredEnvironmentPath()` to all 3 desktop `DesktopMain` classes
- Created minimal `EnvironmentEditorScene` placeholder (shell to be expanded in Step 6)

## Recognized extensions

| Extension | AssetType | AssetCategory |
|---|---|---|
| `.environment.json` | `Environment` | `Environment` |
| `.exr` | `HdrSource` | `Environment` |
| `.hdr` | `HdrSource` | `Environment` |

## Category behavior

- Environment assets appear in the `Environment` category in Asset Browser
- `.exr`/`.hdr` files are recognized as `HdrSource` under `Environment` category (visible but no dedicated editor tool yet)

## Context menu actions

- `.environment.json`: "Open in Environment Editor" (via `EnvironmentEditorAssetTool`)
- `.exr`/`.hdr`: no specific tool registered yet (visible-only for now; "Create Environment from Source" deferred)

## Files touched

- `engine/tools/.../assetbrowser/AssetBrowserScene.kt` — tool registration
- `engine/tools/.../assetbrowser/AssetBrowserUiHelpers.kt` — icon + category set
- `engine/tools/.../environmenteditor/EnvironmentEditorScene.kt` — new (shell)
- `engine/tools/.../ToolsModule.kt` — scene routing
- `desktop-lwjgl3-macos/.../Lwjgl3EditorToolLauncher.kt` — launch method
- `desktop-lwjgl3-win/.../Lwjgl3EditorToolLauncher.kt` — launch method
- `desktop-lwjgl3-linux/.../Lwjgl3EditorToolLauncher.kt` — launch method
- `desktop-lwjgl3-macos/.../DesktopMain.kt` — env path config
- `desktop-lwjgl3-win/.../DesktopMain.kt` — env path config
- `desktop-lwjgl3-linux/.../DesktopMain.kt` — env path config

## Validation

Command:

```bash
./gradlew :core:compileKotlin :engine:tools:compileKotlin :desktop-lwjgl3-macos:compileKotlin :desktop-lwjgl3-win:compileKotlin :desktop-lwjgl3-linux:compileKotlin --no-daemon -q
```

Result:

```text
SUCCESS
```

## Limitations / Follow-ups

- No "Create Environment from Source" action for `.exr`/`.hdr` yet (placeholder deferred).
- No environment asset grouping (manifest children not collapsed under parent).
- `HdrSource` files don't have a dedicated editor tool.
