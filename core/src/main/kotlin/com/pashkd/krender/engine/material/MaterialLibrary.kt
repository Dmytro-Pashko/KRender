package com.pashkd.krender.engine.material

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor

data class TerrainMaterialDescriptor(
    val id: String,
    val name: String,
    val albedoTexture: String,
    val fallbackColor: TerrainLayerColorDescriptor = TerrainLayerColorDescriptor(),
    val defaultTiling: Float = 1f,
)

data class TerrainMaterialLibraryDescriptor(
    val formatVersion: Int = 1,
    val materials: List<TerrainMaterialDescriptor> = emptyList(),
)

class TerrainMaterialLibrary(
    private val logger: Logger? = null,
) {
    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setSerializer(TerrainMaterialLibraryDescriptor::class.java, TerrainMaterialLibraryDescriptorSerializer)
        setSerializer(TerrainMaterialDescriptor::class.java, TerrainMaterialDescriptorSerializer)
        setSerializer(TerrainLayerColorDescriptor::class.java, TerrainLayerColorDescriptorSerializer)
    }
    private var materials: List<TerrainMaterialDescriptor> = fallbackMaterials()

    fun load(assetPath: String = "materials/terrain_materials.json") {
        materials = try {
            loadFromJson(Gdx.files.internal(assetPath).readString("UTF-8"), assetPath)
        } catch (error: Exception) {
            logger?.warn(TAG, error) {
                "Failed to load terrain material library '$assetPath': ${error.message}. Using fallback materials."
            }
            fallbackMaterials()
        }
    }

    fun all(): List<TerrainMaterialDescriptor> = materials

    fun find(id: String?): TerrainMaterialDescriptor? {
        val normalized = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return materials.firstOrNull { it.id == normalized }
    }

    fun firstOrNull(): TerrainMaterialDescriptor? = materials.firstOrNull()

    internal fun loadFromJson(jsonText: String, source: String = "<memory>"): List<TerrainMaterialDescriptor> {
        val descriptor = json.fromJson(TerrainMaterialLibraryDescriptor::class.java, jsonText)
        require(descriptor.formatVersion == FORMAT_VERSION) {
            "Unsupported terrain material format version: ${descriptor.formatVersion}"
        }
        val loaded = validateMaterials(descriptor.materials, source)
        require(loaded.isNotEmpty()) { "Terrain material library '$source' does not contain valid materials" }
        materials = loaded
        return loaded
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
            val albedoTexture = descriptor.albedoTexture.trim()
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

        fun fallbackMaterials(): List<TerrainMaterialDescriptor> =
            listOf(
                TerrainMaterialDescriptor(
                    id = "terrain/sand",
                    name = "Sand",
                    albedoTexture = "textures/t_sand_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.78f, 0.63f, 0.35f, 1f),
                    defaultTiling = 8f,
                ),
                TerrainMaterialDescriptor(
                    id = "terrain/dirt",
                    name = "Dirt",
                    albedoTexture = "textures/t_dirt_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.38f, 0.25f, 0.15f, 1f),
                    defaultTiling = 8f,
                ),
                TerrainMaterialDescriptor(
                    id = "terrain/ground_grass",
                    name = "Ground + Grass",
                    albedoTexture = "textures/t_ground_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.24f, 0.42f, 0.18f, 1f),
                    defaultTiling = 8f,
                ),
                TerrainMaterialDescriptor(
                    id = "terrain/grass",
                    name = "Grass",
                    albedoTexture = "textures/t_grass_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.18f, 0.55f, 0.14f, 1f),
                    defaultTiling = 8f,
                ),
                TerrainMaterialDescriptor(
                    id = "terrain/gravel",
                    name = "Gravel",
                    albedoTexture = "textures/t_gravel_02_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.45f, 0.43f, 0.39f, 1f),
                    defaultTiling = 8f,
                ),
                TerrainMaterialDescriptor(
                    id = "terrain/stone",
                    name = "Stone",
                    albedoTexture = "textures/t_stone_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.42f, 0.42f, 0.40f, 1f),
                    defaultTiling = 8f,
                ),
            )
    }
}

private object TerrainMaterialLibraryDescriptorSerializer : Json.Serializer<TerrainMaterialLibraryDescriptor> {
    override fun write(json: Json, descriptor: TerrainMaterialLibraryDescriptor, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("formatVersion", descriptor.formatVersion)
        json.writeArrayStart("materials")
        descriptor.materials.forEach { material ->
            json.writeValue(material, TerrainMaterialDescriptor::class.java)
        }
        json.writeArrayEnd()
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainMaterialLibraryDescriptor =
        TerrainMaterialLibraryDescriptor(
            formatVersion = jsonData.getInt("formatVersion", 1),
            materials = jsonData.get("materials")?.map { materialData ->
                json.readValue(TerrainMaterialDescriptor::class.java, materialData)
            } ?: emptyList(),
        )
}

private object TerrainMaterialDescriptorSerializer : Json.Serializer<TerrainMaterialDescriptor> {
    override fun write(json: Json, descriptor: TerrainMaterialDescriptor, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("id", descriptor.id)
        json.writeValue("name", descriptor.name)
        json.writeValue("albedoTexture", descriptor.albedoTexture)
        json.writeValue("fallbackColor", descriptor.fallbackColor.clamped(), TerrainLayerColorDescriptor::class.java)
        json.writeValue("defaultTiling", descriptor.defaultTiling)
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainMaterialDescriptor =
        TerrainMaterialDescriptor(
            id = jsonData.getString("id", ""),
            name = jsonData.getString("name", ""),
            albedoTexture = jsonData.getString("albedoTexture", ""),
            fallbackColor = (
                jsonData.get("fallbackColor")?.let {
                    json.readValue(TerrainLayerColorDescriptor::class.java, it)
                } ?: TerrainLayerColorDescriptor()
                ).clamped(),
            defaultTiling = jsonData.getFloat("defaultTiling", 1f),
        )
}

private object TerrainLayerColorDescriptorSerializer : Json.Serializer<TerrainLayerColorDescriptor> {
    override fun write(json: Json, descriptor: TerrainLayerColorDescriptor, knownType: Class<*>?) {
        val color = descriptor.clamped()
        json.writeObjectStart()
        json.writeValue("r", color.r)
        json.writeValue("g", color.g)
        json.writeValue("b", color.b)
        json.writeValue("a", color.a)
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainLayerColorDescriptor =
        TerrainLayerColorDescriptor(
            r = jsonData.getFloat("r", 1f),
            g = jsonData.getFloat("g", 1f),
            b = jsonData.getFloat("b", 1f),
            a = jsonData.getFloat("a", 1f),
        ).clamped()
}

private fun TerrainLayerColorDescriptor.clamped(): TerrainLayerColorDescriptor =
    TerrainLayerColorDescriptor(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )
