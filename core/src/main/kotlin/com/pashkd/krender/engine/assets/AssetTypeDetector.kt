package com.pashkd.krender.engine.assets

/**
 * Detects asset type and category from normalized engine asset paths.
 */
object AssetTypeDetector {
    /**
     * Returns a best-effort type/category pair for [path].
     */
    fun detect(path: String): AssetTypeDetection {
        val normalized = normalizePath(path)
        val extension = normalized.substringAfterLast('.', "").lowercase()
        val lowerPath = normalized.lowercase()
        return when {
            extension == "glb" || extension == "gltf" -> AssetTypeDetection(AssetType.GltfModel, AssetCategory.Model)
            extension == "obj" -> AssetTypeDetection(AssetType.ObjModel, AssetCategory.Model)
            extension == "g3db" || extension == "g3dj" -> AssetTypeDetection(AssetType.GdxModel, AssetCategory.Model)
            extension in textureExtensions -> AssetTypeDetection(AssetType.Texture, AssetCategory.Texture)
            extension == "krskybox" -> AssetTypeDetection(AssetType.Skybox, AssetCategory.Skybox)
            lowerPath.startsWith("terrains/") && extension == "json" -> AssetTypeDetection(AssetType.Terrain, AssetCategory.Terrain)
            lowerPath.startsWith("ui/skins/") && extension == "json" -> AssetTypeDetection(AssetType.Scene2DSkin, AssetCategory.UI)
            extension == "krui" -> AssetTypeDetection(AssetType.UiScene, AssetCategory.UI)
            extension == "krscene" -> AssetTypeDetection(AssetType.Scene, AssetCategory.Scene)
            lowerPath.startsWith("scenes/") && extension == "json" -> AssetTypeDetection(AssetType.Scene, AssetCategory.Scene)
            lowerPath.startsWith("materials/") && extension == "json" -> AssetTypeDetection(AssetType.Material, AssetCategory.Material)
            else -> AssetTypeDetection(AssetType.Unknown, AssetCategory.Other)
        }
    }

    private val textureExtensions = setOf("png", "jpg", "jpeg", "webp")
}

/**
 * Result of asset type detection.
 */
data class AssetTypeDetection(
    val type: AssetType,
    val category: AssetCategory,
)

internal fun normalizePath(path: String): String =
    path.replace('\\', '/').trim().trimStart('/')
