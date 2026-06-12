package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.DefaultSceneFileService
import com.pashkd.krender.engine.scene.SceneFileService
import kotlinx.serialization.json.*

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
class TerrainPersistence(
    private val logger: Logger? = null,
    private val files: SceneFileService = DefaultSceneFileService,
) : TerrainRuntimePersistence {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
            explicitNulls = true
        }

    /**
     * Writes [data] as a versioned terrain file.
     */
    fun save(
        data: TerrainData,
        filePath: String,
        name: String = "terrain",
    ) {
        logger?.info(TAG) { "Saving terrain '$name' to '$filePath' (${data.describeTerrain()})" }
        val encoded = encode(data, name)
        files.ensureDirectories(filePath)
        files.writeText(filePath, encoded)
        logger?.info(TAG) { "Saved terrain '$name' to '$filePath' (${encoded.length} chars)" }
    }

    /**
     * Reads a versioned terrain file and returns editable terrain data.
     */
    fun load(filePath: String): TerrainData {
        logger?.info(TAG) { "Loading terrain data from '$filePath'" }
        val data = decode(files.readText(filePath))
        logger?.info(TAG) { "Loaded terrain data from '$filePath' (${data.describeTerrain()})" }
        return data
    }

    /**
     * Reads a complete terrain file descriptor, including file metadata.
     */
    override fun loadDescriptor(filePath: String): TerrainFileDescriptor {
        logger?.info(TAG) { "Loading terrain descriptor from '$filePath'" }
        val jsonText = files.readText(filePath)
        logger?.debug(TAG) { "Read terrain descriptor from '$filePath' (${jsonText.length} chars)" }
        val descriptor = decodeDescriptor(jsonText)
        logger?.info(TAG) { "Loaded terrain descriptor '${descriptor.name}' from '$filePath' (${descriptor.terrain.describeTerrain()})" }
        return descriptor
    }

    /**
     * Returns true when a local terrain file exists at [filePath].
     */
    fun exists(filePath: String): Boolean = files.exists(filePath)

    /**
     * Returns true when the terrain file can be read from local storage or packaged assets.
     */
    override fun existsReadable(filePath: String): Boolean = files.exists(filePath)

    /**
     * Describes where a terrain path will be read from for diagnostics.
     */
    override fun readableSource(filePath: String): String = files.describeReadableSource(filePath)

    /**
     * Encodes terrain data to JSON. Exposed for serializer-level tests.
     */
    fun encode(
        data: TerrainData,
        name: String = "terrain",
    ): String =
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
        logger?.debug(TAG) {
            "Encoding terrain descriptor '${descriptor.name}' format=${descriptor.formatVersion} (${descriptor.terrain.describeTerrain()})"
        }
        validate(descriptor)
        val encoded = json.encodeToString(JsonObject.serializer(), descriptor.toJsonObject())
        logger?.debug(TAG) { "Encoded terrain descriptor '${descriptor.name}' (${encoded.length} chars)" }
        return encoded
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
        logger?.debug(TAG) { "Decoding terrain descriptor (${jsonText.length} chars)" }
        val root =
            json.parseToJsonElement(jsonText) as? JsonObject
                ?: throw IllegalArgumentException("Terrain descriptor root must be a JSON object")
        val descriptor =
            TerrainFileDescriptor(
                formatVersion = root.intOrDefault("formatVersion", TerrainFileFormat.CurrentVersion),
                name = root.stringOrDefault("name", "terrain"),
                terrain = readTerrainData(root.requiredObject("terrain")),
            )
        validate(descriptor)
        logger?.debug(TAG) {
            "Decoded terrain descriptor '${descriptor.name}' format=${descriptor.formatVersion} (${descriptor.terrain.describeTerrain()})"
        }
        return descriptor
    }

    private fun readTerrainData(node: JsonObject): TerrainDataDescriptor =
        TerrainDataDescriptor(
            width = node.requiredInt("width"),
            height = node.requiredInt("height"),
            vertexSpacing = node.requiredFloat("vertexSpacing"),
            heights = node.requiredFloatArray("heights"),
            layers = readLayers(node["layers"]),
        )

    private fun readLayers(node: JsonElement?): List<TerrainLayerDescriptor> {
        val layers = node as? JsonArray ?: return emptyList()
        return layers.mapIndexed { index, layerNode ->
            val layer =
                layerNode as? JsonObject
                    ?: throw IllegalArgumentException("Terrain layer at index $index must be a JSON object")
            require(layer["texturePath"] == null) {
                "Terrain layer '${layer.stringOrDefault("name", "unknown")}' cannot contain texturePath"
            }
            TerrainLayerDescriptor(
                id = layer.requiredInt("id"),
                name = layer.requiredString("name"),
                materialId = layer.stringOrNull("materialId"),
                color = layer.colorOrDefault("color"),
                visible = layer.booleanOrDefault("visible", true),
                tiling = layer.floatOrDefault("tiling", 1f).coerceIn(0.1f, 128f),
                weights = layer.floatArrayOrNull("weights"),
            )
        }
    }

    private fun validate(descriptor: TerrainFileDescriptor) {
        logger?.debug(TAG) {
            "Validating terrain descriptor '${descriptor.name}' format=${descriptor.formatVersion} (${descriptor.terrain.describeTerrain()})"
        }
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
        logger?.debug(TAG) { "Validated terrain descriptor '${descriptor.name}'" }
    }

    private fun TerrainFileDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("formatVersion", JsonPrimitive(formatVersion))
            put("name", JsonPrimitive(name))
            put("terrain", terrain.toJsonObject())
        }

    private fun TerrainDataDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("width", JsonPrimitive(width))
            put("height", JsonPrimitive(height))
            put("vertexSpacing", JsonPrimitive(vertexSpacing))
            put(
                "heights",
                buildJsonArray {
                    heights.forEach { height -> add(JsonPrimitive(height)) }
                },
            )
            put(
                "layers",
                buildJsonArray {
                    layers.forEach { layer -> add(layer.toJsonObject()) }
                },
            )
        }

    private fun TerrainLayerDescriptor.toJsonObject(): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put("materialId", materialId?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "color",
                buildJsonObject {
                    val clamped = color.clamped()
                    put("r", JsonPrimitive(clamped.r))
                    put("g", JsonPrimitive(clamped.g))
                    put("b", JsonPrimitive(clamped.b))
                    put("a", JsonPrimitive(clamped.a))
                },
            )
            put("visible", JsonPrimitive(visible))
            put("tiling", JsonPrimitive(tiling.coerceIn(0.1f, 128f)))
            weights?.let { weightArray ->
                put(
                    "weights",
                    buildJsonArray {
                        weightArray.forEach { weight -> add(JsonPrimitive(weight)) }
                    },
                )
            }
        }

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name] as? JsonObject
            ?: throw IllegalArgumentException("Terrain file is missing required field '$name'")

    private fun JsonObject.requiredString(name: String): String =
        (this[name] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Terrain file is missing required field '$name'")

    private fun JsonObject.requiredInt(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Terrain file field '$name' must be an integer")

    private fun JsonObject.requiredFloat(name: String): Float =
        (this[name] as? JsonPrimitive)?.floatOrNull
            ?: throw IllegalArgumentException("Terrain file field '$name' must be a number")

    private fun JsonObject.requiredFloatArray(name: String): FloatArray =
        floatArrayOrNull(name)
            ?: throw IllegalArgumentException("Terrain file is missing required field '$name'")

    private fun JsonObject.floatArrayOrNull(name: String): FloatArray? {
        val values = this[name] as? JsonArray ?: return null
        return FloatArray(values.size) { index ->
            (values[index] as? JsonPrimitive)?.floatOrNull
                ?: throw IllegalArgumentException("Terrain file field '$name[$index]' must be a number")
        }
    }

    private fun JsonObject.colorOrDefault(name: String): TerrainLayerColorDescriptor {
        val color = this[name] as? JsonObject ?: return TerrainLayerColorDescriptor()
        return TerrainLayerColorDescriptor(
            r = color.floatOrDefault("r", 1f),
            g = color.floatOrDefault("g", 1f),
            b = color.floatOrDefault("b", 1f),
            a = color.floatOrDefault("a", 1f),
        ).clamped()
    }

    private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.content

    private fun JsonObject.stringOrDefault(
        name: String,
        defaultValue: String,
    ): String = stringOrNull(name) ?: defaultValue

    private fun JsonObject.intOrDefault(
        name: String,
        defaultValue: Int,
    ): Int = (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: defaultValue

    private fun JsonObject.floatOrDefault(
        name: String,
        defaultValue: Float,
    ): Float = (this[name] as? JsonPrimitive)?.floatOrNull ?: defaultValue

    private fun JsonObject.booleanOrDefault(
        name: String,
        defaultValue: Boolean,
    ): Boolean = (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: defaultValue

    companion object {
        private const val TAG = "TerrainPersistence"
    }
}

private fun TerrainLayerColorDescriptor.clamped(): TerrainLayerColorDescriptor =
    TerrainLayerColorDescriptor(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )

private fun TerrainData.describeTerrain(): String =
    "size=${width}x$height spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} [${
        allLayers().joinToString { layer ->
            "${layer.id}:${layer.name}"
        }
    }]"

private fun TerrainDataDescriptor.describeTerrain(): String =
    "size=${width}x$height spacing=${"%.2f".format(vertexSpacing)} layers=${layers.size} [${
        layers.joinToString { layer ->
            "${layer.id}:${layer.name}"
        }
    }]"
