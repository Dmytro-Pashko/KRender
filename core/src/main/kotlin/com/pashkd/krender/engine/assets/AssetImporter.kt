package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.Logger
import java.io.File

/**
 * Backend-neutral asset importer that decides which [AssetType]/[AssetCategory]
 * a file maps to and provides per-importer metadata defaults.
 */
interface AssetImporter {
    val id: String
    val displayName: String
    val supportedExtensions: Set<String>
    val outputType: AssetType
    val outputCategory: AssetCategory

    /** Returns whether this importer can handle [path]. */
    fun canImport(path: String): Boolean

    /** Reads importer-specific metadata from [file] (best-effort, never throws). */
    fun readMetadata(file: File): Map<String, String> = emptyMap()
}

/**
 * Registry of [AssetImporter]s. Resolution falls back to [AssetTypeDetector]
 * when no importer claims a path.
 */
class AssetImporterRegistry(
    private val logger: Logger,
) {
    private val importers = mutableListOf<AssetImporter>()

    /** Registers an importer. Last-registered wins on conflict. */
    fun register(importer: AssetImporter) {
        importers.removeAll { it.id == importer.id }
        importers += importer
        logger.info(TAG) { "Registered importer '${importer.id}' (${importer.displayName})" }
    }

    /** Returns all registered importers. */
    fun all(): List<AssetImporter> = importers.toList()

    /** Returns the first importer that claims [path], or null. */
    fun resolve(path: String): AssetImporter? =
        importers.firstOrNull { it.canImport(path) }

    /** Returns importers whose output category matches [category]. */
    fun byCategory(category: AssetCategory): List<AssetImporter> =
        importers.filter { it.outputCategory == category }

    companion object {
        private const val TAG = "AssetImporterRegistry"

        /**
         * Returns a registry preloaded with the engine's built-in importers.
         */
        fun withDefaults(logger: Logger): AssetImporterRegistry =
            AssetImporterRegistry(logger).apply {
                register(GltfModelImporter())
                register(ObjModelImporter())
                register(GdxModelImporter())
                register(TextureImporter())
                register(SkyboxImporter())
                register(TerrainImporter())
                register(MaterialImporter())
                register(ShaderImporter())
            }
    }
}

private fun normalizedExtension(path: String): String =
    path.substringAfterLast('.', "").lowercase()

private fun normalizedLower(path: String): String =
    path.replace('\\', '/').lowercase()

/**
 * glTF / GLB binary models.
 */
class GltfModelImporter : AssetImporter {
    override val id = "gltf"
    override val displayName = "glTF Model"
    override val supportedExtensions = setOf("gltf", "glb")
    override val outputType = AssetType.GltfModel
    override val outputCategory = AssetCategory.Model

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}

/**
 * Wavefront .obj models.
 */
class ObjModelImporter : AssetImporter {
    override val id = "obj"
    override val displayName = "Wavefront OBJ"
    override val supportedExtensions = setOf("obj")
    override val outputType = AssetType.ObjModel
    override val outputCategory = AssetCategory.Model

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}

/**
 * libGDX g3db / g3dj models.
 */
class GdxModelImporter : AssetImporter {
    override val id = "gdx-model"
    override val displayName = "libGDX Model"
    override val supportedExtensions = setOf("g3db", "g3dj")
    override val outputType = AssetType.GdxModel
    override val outputCategory = AssetCategory.Model

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}

/**
 * 2D textures (png/jpg/webp).
 */
class TextureImporter : AssetImporter {
    override val id = "texture"
    override val displayName = "Texture"
    override val supportedExtensions = setOf("png", "jpg", "jpeg", "webp")
    override val outputType = AssetType.Texture
    override val outputCategory = AssetCategory.Texture

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}

/**
 * Scene skybox descriptors.
 */
class SkyboxImporter : AssetImporter {
    override val id = "skybox"
    override val displayName = "Skybox"
    override val supportedExtensions = setOf("krskybox")
    override val outputType = AssetType.Skybox
    override val outputCategory = AssetCategory.Skybox

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}

/**
 * Terrain JSON descriptors located under `terrains/`.
 */
class TerrainImporter : AssetImporter {
    override val id = "terrain"
    override val displayName = "Terrain"
    override val supportedExtensions = setOf("json")
    override val outputType = AssetType.Terrain
    override val outputCategory = AssetCategory.Terrain

    override fun canImport(path: String): Boolean {
        if (normalizedExtension(path) !in supportedExtensions) return false
        return normalizedLower(path).contains("terrains/")
    }
}

/**
 * Material JSON descriptors located under `materials/`.
 */
class MaterialImporter : AssetImporter {
    override val id = "material"
    override val displayName = "Material"
    override val supportedExtensions = setOf("json")
    override val outputType = AssetType.Material
    override val outputCategory = AssetCategory.Material

    override fun canImport(path: String): Boolean {
        if (normalizedExtension(path) !in supportedExtensions) return false
        return normalizedLower(path).contains("materials/")
    }
}

/**
 * GLSL shader source files.
 */
class ShaderImporter : AssetImporter {
    override val id = "shader"
    override val displayName = "Shader"
    override val supportedExtensions = setOf("vert", "frag", "glsl")
    override val outputType = AssetType.Shader
    override val outputCategory = AssetCategory.Shader

    override fun canImport(path: String): Boolean = normalizedExtension(path) in supportedExtensions
}
