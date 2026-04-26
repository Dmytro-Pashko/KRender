package com.pashkd.krender.engine.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter

/**
 * Versioned terrain asset file wrapper.
 */
data class TerrainFileDescriptor(
    val formatVersion: Int = TerrainFileFormat.CurrentVersion,
    val name: String = "terrain",
    val terrain: TerrainDataDescriptor,
)

/**
 * Terrain asset format constants.
 */
object TerrainFileFormat {
    const val CurrentVersion = 1
    const val Extension = "json"
}

/**
 * Saves and loads terrain data assets without runtime renderer state.
 */
class TerrainPersistence {
    private val json = Json().apply {
        setOutputType(JsonWriter.OutputType.json)
        setSerializer(TerrainFileDescriptor::class.java, TerrainFileDescriptorSerializer)
        setSerializer(TerrainDataDescriptor::class.java, TerrainDataDescriptorSerializer)
        setSerializer(TerrainLayerDescriptor::class.java, TerrainLayerDescriptorSerializer)
        setSerializer(TerrainLayerColorDescriptor::class.java, TerrainLayerColorDescriptorSerializer)
    }

    /**
     * Writes [data] as a versioned terrain file.
     */
    fun save(data: TerrainData, filePath: String, name: String = "terrain") {
        val file = Gdx.files.local(filePath)
        file.parent()?.mkdirs()
        file.writeString(encode(data, name), false, "UTF-8")
    }

    /**
     * Reads a versioned terrain file and returns editable terrain data.
     */
    fun load(filePath: String): TerrainData = decode(Gdx.files.local(filePath).readString("UTF-8"))

    /**
     * Reads a complete terrain file descriptor, including file metadata.
     */
    fun loadDescriptor(filePath: String): TerrainFileDescriptor =
        decodeDescriptor(Gdx.files.local(filePath).readString("UTF-8"))

    /**
     * Returns true when a local terrain file exists at [filePath].
     */
    fun exists(filePath: String): Boolean = Gdx.files.local(filePath).exists()

    /**
     * Encodes terrain data to JSON. Exposed for serializer-level tests.
     */
    fun encode(data: TerrainData, name: String = "terrain"): String =
        encode(
            TerrainFileDescriptor(
                formatVersion = TerrainFileFormat.CurrentVersion,
                name = name,
                terrain = data.toDescriptor(),
            ),
        )

    /**
     * Encodes a terrain file descriptor to JSON. Exposed for malformed descriptor tests.
     */
    fun encode(descriptor: TerrainFileDescriptor): String {
        validate(descriptor)
        return json.prettyPrint(descriptor)
    }

    /**
     * Extracts the editable terrain file name without the terrain extension.
     */
    fun fileNameFromPath(filePath: String): String {
        val leaf = filePath.substringAfterLast('/').substringAfterLast('\\')
        return leaf.removeSuffix(".${TerrainFileFormat.Extension}")
    }

    /**
     * Decodes terrain JSON into editable terrain data.
     */
    fun decode(jsonText: String): TerrainData {
        val descriptor = decodeDescriptor(jsonText)
        return TerrainData.fromDescriptor(descriptor.terrain)
    }

    /**
     * Decodes terrain JSON into a validated file descriptor.
     */
    fun decodeDescriptor(jsonText: String): TerrainFileDescriptor {
        val descriptor = json.fromJson(TerrainFileDescriptor::class.java, jsonText)
        validate(descriptor)
        return descriptor
    }

    private fun validate(descriptor: TerrainFileDescriptor) {
        require(descriptor.formatVersion == TerrainFileFormat.CurrentVersion) {
            "Unsupported terrain format version: ${descriptor.formatVersion}"
        }

        val terrain = descriptor.terrain
        require(terrain.width >= 2) { "Terrain width must be >= 2" }
        require(terrain.height >= 2) { "Terrain height must be >= 2" }
        require(terrain.vertexSpacing > 0f) { "Terrain vertexSpacing must be > 0" }
        require(terrain.heights.size == terrain.width * terrain.height) {
            "Terrain heights size ${terrain.heights.size} does not match width * height ${terrain.width * terrain.height}"
        }
        require(terrain.layers.size <= TerrainLayerLimits.MaxLayers) {
            "Terrain layer count cannot exceed ${TerrainLayerLimits.MaxLayers}"
        }

        terrain.layers.forEach { layer ->
            require(layer.tiling > 0f) { "Terrain layer '${layer.name}' tiling must be > 0" }
            require(layer.color.r in 0f..1f && layer.color.g in 0f..1f && layer.color.b in 0f..1f && layer.color.a in 0f..1f) {
                "Terrain layer '${layer.name}' color channels must be in 0..1"
            }
            val weights = layer.weights
            require(weights == null || weights.size == terrain.width * terrain.height) {
                "Terrain layer '${layer.name}' weights size ${weights?.size} does not match width * height ${terrain.width * terrain.height}"
            }
        }
    }
}

private object TerrainFileDescriptorSerializer : Json.Serializer<TerrainFileDescriptor> {
    override fun write(json: Json, descriptor: TerrainFileDescriptor, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("formatVersion", descriptor.formatVersion)
        json.writeValue("name", descriptor.name)
        json.writeValue("terrain", descriptor.terrain, TerrainDataDescriptor::class.java)
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainFileDescriptor =
        TerrainFileDescriptor(
            formatVersion = jsonData.getInt("formatVersion", TerrainFileFormat.CurrentVersion),
            name = jsonData.getString("name", "terrain"),
            terrain = json.readValue(TerrainDataDescriptor::class.java, required(jsonData, "terrain")),
        )
}

private object TerrainDataDescriptorSerializer : Json.Serializer<TerrainDataDescriptor> {
    override fun write(json: Json, descriptor: TerrainDataDescriptor, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("width", descriptor.width)
        json.writeValue("height", descriptor.height)
        json.writeValue("vertexSpacing", descriptor.vertexSpacing)
        json.writeValue("heights", descriptor.heights)
        json.writeArrayStart("layers")
        descriptor.layers.forEach { layer ->
            json.writeValue(layer, TerrainLayerDescriptor::class.java)
        }
        json.writeArrayEnd()
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainDataDescriptor =
        TerrainDataDescriptor(
            width = jsonData.getInt("width"),
            height = jsonData.getInt("height"),
            vertexSpacing = jsonData.getFloat("vertexSpacing"),
            heights = required(jsonData, "heights").asFloatArray(),
            layers = readLayers(json, jsonData.get("layers")),
        )

    private fun readLayers(json: Json, layersData: JsonValue?): List<TerrainLayerDescriptor> {
        if (layersData == null) return emptyList()
        return layersData.map { layerData ->
            json.readValue(TerrainLayerDescriptor::class.java, layerData)
        }
    }
}

private object TerrainLayerDescriptorSerializer : Json.Serializer<TerrainLayerDescriptor> {
    override fun write(json: Json, descriptor: TerrainLayerDescriptor, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("id", descriptor.id)
        json.writeValue("name", descriptor.name)
        json.writeValue("materialId", descriptor.materialId)
        json.writeValue("color", descriptor.color.clamped(), TerrainLayerColorDescriptor::class.java)
        json.writeValue("visible", descriptor.visible)
        json.writeValue("tiling", descriptor.tiling.coerceIn(0.1f, 128f))
        if (descriptor.weights != null) {
            json.writeValue("weights", descriptor.weights)
        }
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TerrainLayerDescriptor {
        require(jsonData.get("texturePath") == null) { "Terrain layer '${jsonData.getString("name", "unknown")}' cannot contain texturePath" }
        return TerrainLayerDescriptor(
            id = jsonData.getInt("id"),
            name = jsonData.getString("name"),
            materialId = jsonData.getString("materialId", null),
            color = (
                jsonData.get("color")?.let {
                    json.readValue(TerrainLayerColorDescriptor::class.java, it)
                } ?: TerrainLayerColorDescriptor()
                ).clamped(),
            visible = jsonData.getBoolean("visible", true),
            tiling = jsonData.getFloat("tiling", 1f).coerceIn(0.1f, 128f),
            weights = jsonData.get("weights")?.asFloatArray(),
        )
    }
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

private fun required(jsonData: JsonValue, name: String): JsonValue =
    jsonData.get(name) ?: throw IllegalArgumentException("Terrain file is missing required field '$name'")

private fun TerrainLayerColorDescriptor.clamped(): TerrainLayerColorDescriptor =
    TerrainLayerColorDescriptor(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )
