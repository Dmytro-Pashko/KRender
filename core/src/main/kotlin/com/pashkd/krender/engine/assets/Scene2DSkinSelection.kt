package com.pashkd.krender.engine.assets

const val DefaultUiSceneSkinPath = "ui/skins/craftacular-ui.json"

enum class CreatableAssetKind(
    val displayName: String,
    val type: AssetType,
    val category: AssetCategory,
    val targetDirectory: String,
    val extension: String,
) {
    UiScene("UI Scene", AssetType.UiScene, AssetCategory.UI, "ui/scenes", "krui"),
    Terrain("Terrain", AssetType.Terrain, AssetCategory.Terrain, "terrains", "json"),
    Scene("Scene", AssetType.Scene, AssetCategory.Scene, "scenes", "krscene"),
}

data class CreateAssetDraft(
    val kind: CreatableAssetKind = CreatableAssetKind.UiScene,
    val name: String = "",
    val uiSceneSkinPath: String = DefaultUiSceneSkinPath,
)

internal fun defaultCreateAssetDraft(assets: List<AssetDescriptor>): CreateAssetDraft =
    CreateAssetDraft(uiSceneSkinPath = defaultUiSceneSkinPath(assets))

internal fun discoveredScene2DSkinAssets(assets: List<AssetDescriptor>): List<AssetDescriptor> =
    assets
        .filter { asset -> asset.category == AssetCategory.UI && asset.type == AssetType.Scene2DSkin }
        .sortedBy { asset -> asset.path.lowercase() }

internal fun defaultUiSceneSkinPath(assets: List<AssetDescriptor>): String =
    discoveredScene2DSkinAssets(assets)
        .firstOrNull { asset -> asset.path.equals(DefaultUiSceneSkinPath, ignoreCase = true) }
        ?.path
        ?: discoveredScene2DSkinAssets(assets).firstOrNull()?.path
        ?: DefaultUiSceneSkinPath

internal fun normalizedUiSceneSkinPath(path: String): String =
    path.trim().replace('\\', '/').ifBlank { DefaultUiSceneSkinPath }

internal fun CreateAssetDraft.withSyncedDefaults(assets: List<AssetDescriptor>): CreateAssetDraft =
    if (kind == CreatableAssetKind.UiScene) {
        val skinAssets = discoveredScene2DSkinAssets(assets)
        val selectedPath = normalizedUiSceneSkinPath(uiSceneSkinPath)
        val nextSkin = when {
            skinAssets.isEmpty() -> DefaultUiSceneSkinPath
            skinAssets.any { asset -> asset.path == selectedPath } -> selectedPath
            else -> defaultUiSceneSkinPath(assets)
        }
        copy(uiSceneSkinPath = nextSkin)
    } else {
        this
    }

internal fun createAssetRelativePath(draft: CreateAssetDraft): String =
    normalizePath("${draft.kind.targetDirectory}/${createAssetBaseName(draft)}.${draft.kind.extension}")

internal fun createAssetBaseName(draft: CreateAssetDraft): String =
    sanitizedAssetName(draft.name, defaultAssetBaseName(draft.kind.type, draft.kind.category))

internal fun defaultAssetBaseName(type: AssetType, category: AssetCategory): String =
    when (type) {
        AssetType.UiScene -> "new_ui_scene"
        AssetType.Terrain -> "new_terrain"
        AssetType.Scene -> "new_scene"
        else -> error("Unsupported asset creation type=$type category=$category")
    }

internal fun sanitizedAssetName(name: String, fallback: String): String =
    name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { fallback }

internal fun createAssetDefaultParams(draft: CreateAssetDraft): List<String> =
    when (draft.kind) {
        CreatableAssetKind.UiScene -> listOf(
            "Skin: ${normalizedUiSceneSkinPath(draft.uiSceneSkinPath)}",
            "Root: Stack",
            "Schema: 1",
        )
        CreatableAssetKind.Terrain -> listOf(
            "Size: 64 x 64",
            "Vertex spacing: 1.0",
            "Layers: 0",
        )
        CreatableAssetKind.Scene -> listOf(
            "Schema: 1",
            "Entities: 0",
            "Settings: default",
        )
    }
