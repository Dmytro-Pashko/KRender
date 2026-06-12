package com.pashkd.krender.engine.assets

/**
 * Stable unique asset identifier stored in sidecar metadata.
 */
@JvmInline
value class AssetId(val value: String)

/**
 * High-level asset grouping used for browser filters and sorting.
 */
enum class AssetCategory(
    val displayName: String,
    val sortOrder: Int,
) {
    Model("Model", 0),
    Texture("Texture", 1),
    Skybox("Skybox", 2),
    Material("Material", 3),
    Terrain("Terrain", 4),

    /**
     * UI document category used by asset indexing and browser filters for KRender-native `.krui` UiScene files.
     *
     * This category is metadata-only for now: it makes runtime UI documents visible to tools and prepares
     * routing to UiComposerScene, but it does not provide preview rendering, hierarchy editing, Skin editing,
     * drag/drop authoring, or asset-id references.
     */
    UI("UI", 5),
    Scene("Scene", 6),
    Other("Other", 7),
}

/**
 * Concrete asset kind detected from paths and metadata.
 */
enum class AssetType {
    GltfModel,
    ObjModel,
    GdxModel,
    Texture,
    Skybox,
    Terrain,

    /**
     * KRender-native Scene2D UI scene document stored as `.krui`.
     *
     * This type belongs to asset metadata and tool routing: runtime UI already consumes the shared
     * `engine.ui.scene` model, while Asset Browser now indexes these files and can open a temporary
     * UiComposerScene placeholder. Full UI Composer editing, preview rendering, Skin editing, drag/drop,
     * and asset-id references are intentionally deferred.
     */
    UiScene,

    /**
     * LibGDX Scene2D Skin JSON descriptor used by `.krui` UI scenes and future Skin tooling.
     *
     * This asset type is metadata-only for now. Asset Browser can index and inspect Skin JSON files,
     * but Skin editing, preview rendering, and asset-id based texture migration are intentionally out of scope.
     */
    Scene2DSkin,
    Scene,
    Material,
    Unknown,
}

/**
 * Backend-neutral snapshot describing one discovered engine asset.
 */
data class AssetDescriptor(
    val id: AssetId,
    val name: String,
    val path: String,
    val category: AssetCategory,
    val type: AssetType,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)
