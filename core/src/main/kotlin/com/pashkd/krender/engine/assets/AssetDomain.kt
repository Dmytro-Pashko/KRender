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
    Material("Material", 2),
    Terrain("Terrain", 3),
    Shader("Shader", 4),
    Scene("Scene", 5),
    Audio("Audio", 6),
    Script("Script", 7),
    Unknown("Unknown", 8),
}

/**
 * Concrete asset kind detected from paths and metadata.
 */
enum class AssetType {
    GltfModel,
    ObjModel,
    GdxModel,
    Texture,
    Terrain,
    Scene,
    Material,
    Shader,
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
