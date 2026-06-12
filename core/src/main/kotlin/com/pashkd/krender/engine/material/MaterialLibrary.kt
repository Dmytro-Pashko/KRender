package com.pashkd.krender.engine.material

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.DefaultSceneFileService
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import kotlinx.serialization.json.*

data class TerrainMaterialDescriptor(
    val id: String,
    val name: String,
    val albedoTexture: String,
    val fallbackColor: TerrainLayerColorDescriptor,
    val defaultTiling: Float,
)

data class TerrainMaterialLibraryDescriptor(
    val formatVersion: Int,
    val materials: List<TerrainMaterialDescriptor>,
)

class TerrainMaterialLibrary(
    private val logger: Logger? = null,
    private val files: SceneFileService = DefaultSceneFileService,
) {
    private var materials: List<TerrainMaterialDescriptor> = emptyList()

    fun load(assetPath: String) {
        val normalizedPath = assetPath.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Terrain material library path must not be blank." }
        materials = try {
            loadFromJson(files.readText(normalizedPath), normalizedPath)
        } catch (error: Exception) {
            logger?.error(TAG, error) {
                "Failed to load terrain material library '$normalizedPath': ${error.message}"
            }
            throw IllegalStateException(
                "Terrain material library '$normalizedPath' could not be loaded: ${error.message}",
                error
            )
        }
    }

    fun all(): List<TerrainMaterialDescriptor> = materials

    fun find(id: String?): TerrainMaterialDescriptor? {
        val normalized = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return materials.firstOrNull { it.id == normalized }
    }

    fun firstOrNull(): TerrainMaterialDescriptor? = materials.firstOrNull()

    internal fun loadFromJson(jsonText: String, source: String = "<memory>"): List<TerrainMaterialDescriptor> {
        val root = Json.parseToJsonElement(jsonText) as? JsonObject
            ?: throw IllegalArgumentException("Terrain material library root must be a JSON object")
        val descriptor = TerrainMaterialLibraryDescriptor(
            formatVersion = root.intOrDefault("formatVersion", -1),
            materials = readMaterials(root["materials"]),
        )
        require(descriptor.formatVersion == FORMAT_VERSION) {
            "Unsupported terrain material format version: ${descriptor.formatVersion}"
        }
        val loaded = validateMaterials(descriptor.materials, source)
        require(loaded.isNotEmpty()) { "Terrain material library '$source' does not contain valid materials" }
        materials = loaded
        return loaded
    }

    private fun readMaterials(node: JsonElement?): List<TerrainMaterialDescriptor> {
        val materials = node as? JsonArray
            ?: throw IllegalArgumentException("Terrain material library is missing required field 'materials'")
        return materials.mapIndexed { index, materialNode ->
            val material = materialNode as? JsonObject
                ?: throw IllegalArgumentException("Terrain material at index $index must be a JSON object")
            TerrainMaterialDescriptor(
                id = material.requiredString("id"),
                name = material.requiredString("name"),
                albedoTexture = material.requiredString("albedoTexture"),
                fallbackColor = material.requiredColor("fallbackColor"),
                defaultTiling = material.requiredFloat("defaultTiling"),
            )
        }
    }

    private fun validateMaterials(
        descriptors: List<TerrainMaterialDescriptor>,
        source: String,
    ): List<TerrainMaterialDescriptor> {
        val ids = linkedSetOf<String>()
        val valid = mutableListOf<TerrainMaterialDescriptor>()
        descriptors.forEach { descriptor ->
            val id = descriptor.id.trim()
            val name = descriptor.name.trim()
            val albedoTexture = descriptor.albedoTexture.trim().replace('\\', '/')
            when {
                id.isBlank() -> logger?.warn(TAG) { "Ignoring terrain material with blank id in '$source'" }
                name.isBlank() -> logger?.warn(TAG) { "Ignoring terrain material '$id' with blank name in '$source'" }
                albedoTexture.isBlank() -> logger?.warn(TAG) { "Ignoring terrain material '$id' with blank albedoTexture in '$source'" }
                descriptor.defaultTiling <= 0f -> logger?.warn(TAG) { "Ignoring terrain material '$id' with non-positive defaultTiling in '$source'" }
                !ids.add(id) -> logger?.warn(TAG) { "Ignoring duplicate terrain material id '$id' in '$source'" }
                else -> valid += descriptor.copy(
                    id = id,
                    name = name,
                    albedoTexture = albedoTexture,
                    fallbackColor = descriptor.fallbackColor.clamped(),
                )
            }
        }
        return valid
    }

    companion object {
        private const val TAG = "TerrainMaterialLibrary"
        private const val FORMAT_VERSION = 1

        fun fromMaterials(materials: List<TerrainMaterialDescriptor>): TerrainMaterialLibrary =
            TerrainMaterialLibrary().also { library ->
                library.materials = library.validateMaterials(materials, "<provided>")
            }
    }

    private fun JsonObject.requiredString(name: String): String =
        (this[name] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Terrain material library is missing required field '$name'")

    private fun JsonObject.requiredFloat(name: String): Float =
        (this[name] as? JsonPrimitive)?.floatOrNull
            ?: throw IllegalArgumentException("Terrain material library field '$name' must be a number")

    private fun JsonObject.requiredColor(name: String): TerrainLayerColorDescriptor {
        val color = this[name] as? JsonObject
            ?: throw IllegalArgumentException("Terrain material library is missing required field '$name'")
        return TerrainLayerColorDescriptor(
            r = color.floatOrDefault("r", 1f),
            g = color.floatOrDefault("g", 1f),
            b = color.floatOrDefault("b", 1f),
            a = color.floatOrDefault("a", 1f),
        ).clamped()
    }

    private fun JsonObject.floatOrDefault(name: String, defaultValue: Float): Float =
        (this[name] as? JsonPrimitive)?.floatOrNull ?: defaultValue

    private fun JsonObject.intOrDefault(name: String, defaultValue: Int): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: defaultValue
}

private fun TerrainLayerColorDescriptor.clamped(): TerrainLayerColorDescriptor =
    TerrainLayerColorDescriptor(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )
