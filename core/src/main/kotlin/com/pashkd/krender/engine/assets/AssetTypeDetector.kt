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
        val environmentDetection = detectEnvironmentAsset(lowerPath, extension)
        return when {
            environmentDetection != null -> environmentDetection
            extension == "glb" || extension == "gltf" -> AssetTypeDetection(AssetType.GltfModel, AssetCategory.Model)
            extension == "obj" -> AssetTypeDetection(AssetType.ObjModel, AssetCategory.Model)
            extension == "g3db" || extension == "g3dj" -> AssetTypeDetection(AssetType.GdxModel, AssetCategory.Model)
            extension in textureExtensions -> AssetTypeDetection(AssetType.Texture, AssetCategory.Texture)
            extension == "atlas" -> AssetTypeDetection(AssetType.Atlas, AssetCategory.Scene2D)
            extension == "fnt" || extension == "ttf" || extension == "otf" -> AssetTypeDetection(AssetType.Font, AssetCategory.Scene2D)
            lowerPath.endsWith(".kfont.json") -> AssetTypeDetection(AssetType.Font, AssetCategory.Scene2D)
            lowerPath.startsWith("terrains/") && extension == "json" ->
                AssetTypeDetection(
                    AssetType.Terrain,
                    AssetCategory.Terrain,
                )

            lowerPath.startsWith("ui/skins/") && extension == "json" ->
                AssetTypeDetection(
                    AssetType.Scene2DSkin,
                    AssetCategory.Scene2D,
                )

            extension == "krui" -> AssetTypeDetection(AssetType.UiScene, AssetCategory.UI)
            extension == "krscene" -> AssetTypeDetection(AssetType.Scene, AssetCategory.Scene)
            lowerPath.startsWith("scenes/") && extension == "json" ->
                AssetTypeDetection(
                    AssetType.Scene,
                    AssetCategory.Scene,
                )

            lowerPath.startsWith("materials/") && extension == "json" ->
                AssetTypeDetection(
                    AssetType.Material,
                    AssetCategory.Material,
                )

            else -> AssetTypeDetection(AssetType.Unknown, AssetCategory.Other)
        }
    }

    private val textureExtensions = setOf("png", "bmp", "jpg", "jpeg", "ktx", "webp")

    private fun detectEnvironmentAsset(
        lowerPath: String,
        extension: String,
    ): AssetTypeDetection? =
        when {
            lowerPath.endsWith(".environment.json") ->
                AssetTypeDetection(AssetType.Environment, AssetCategory.Environment)

            extension == "exr" || extension == "hdr" ->
                AssetTypeDetection(AssetType.HdrSource, AssetCategory.Environment)

            extension == "krskybox" ->
                AssetTypeDetection(AssetType.EnvironmentSkybox, AssetCategory.Environment)

            extension in textureExtensions && isEnvironmentTexturePath(lowerPath) ->
                AssetTypeDetection(environmentTextureType(lowerPath), AssetCategory.Environment)

            else -> null
        }

    private fun isEnvironmentTexturePath(lowerPath: String): Boolean =
        isBrdfLutPath(lowerPath) ||
            lowerPath.startsWith("environments/") ||
            lowerPath.contains("/environments/") ||
            lowerPath.startsWith("skyboxes/") ||
            lowerPath.contains("/skyboxes/") ||
            lowerPath.contains("generated/skybox") ||
            lowerPath.contains("generated/irradiance") ||
            lowerPath.contains("generated/radiance") ||
            lowerPath.contains("cubemap")

    private fun environmentTextureType(lowerPath: String): AssetType =
        when {
            isBrdfLutPath(lowerPath) -> AssetType.BrdfLut
            lowerPath.startsWith("skyboxes/") || lowerPath.contains("/skyboxes/") -> AssetType.EnvironmentSkybox
            lowerPath.contains("generated/skybox") -> AssetType.EnvironmentSkybox
            lowerPath.contains("generated/irradiance") -> AssetType.EnvironmentCubemap
            lowerPath.contains("generated/radiance") -> AssetType.EnvironmentCubemap
            lowerPath.contains("cubemap") -> AssetType.EnvironmentCubemap
            hasCubemapFaceName(lowerPath) -> AssetType.EnvironmentCubemap
            else -> AssetType.EnvironmentGeneratedMap
        }

    private fun isBrdfLutPath(lowerPath: String): Boolean = lowerPath.contains("brdf_lut")

    private fun hasCubemapFaceName(lowerPath: String): Boolean {
        val stem = lowerPath.substringAfterLast('/').substringBeforeLast('.')
        if (stem in cubemapFaceNames) return true
        return cubemapFaceSuffixes.any { suffix -> stem.endsWith(suffix) }
    }

    private val cubemapFaceNames = setOf("px", "nx", "py", "ny", "pz", "nz")
    private val cubemapFaceSuffixes = cubemapFaceNames.flatMap { face -> listOf("_$face", "-$face") }.toSet()
}

/**
 * Result of asset type detection.
 */
data class AssetTypeDetection(
    val type: AssetType,
    val category: AssetCategory,
)

internal fun normalizePath(path: String): String = path.replace('\\', '/').trim().trimStart('/')
