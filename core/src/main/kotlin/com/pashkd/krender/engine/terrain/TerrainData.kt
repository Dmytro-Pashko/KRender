package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.TextureAsset
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Terrain layer authoring limits.
 */
object TerrainLayerLimits {
    const val MaxLayers = 8
}

/**
 * Serializable editor/debug color used by terrain layers.
 */
data class TerrainLayerColorDescriptor(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f,
)

/**
 * Describes one editable terrain surface layer.
 *
 * Layers keep authoring metadata separate from the generated mesh so future
 * material blending and texture painting can reuse the same terrain data.
 */
data class TerrainLayer(
    val id: Int,
    var name: String,
    val texture: AssetRef<TextureAsset>? = null,
    var materialId: String? = null,
    var color: TerrainLayerColorDescriptor = TerrainLayerColorDescriptor(),
    var visible: Boolean = true,
    var tiling: Float = 1f,
)

/**
 * Serializable layer snapshot used by terrain save/load code.
 */
data class TerrainLayerDescriptor(
    val id: Int,
    val name: String,
    val materialId: String? = null,
    val color: TerrainLayerColorDescriptor = TerrainLayerColorDescriptor(),
    val visible: Boolean = true,
    val tiling: Float = 1f,
    val weights: FloatArray? = null,
)

/**
 * Serializable terrain snapshot containing only data, not runtime render state.
 */
data class TerrainDataDescriptor(
    val width: Int,
    val height: Int,
    val vertexSpacing: Float,
    val heights: FloatArray,
    val layers: List<TerrainLayerDescriptor> = emptyList(),
)

/**
 * Editable heightfield and layer data for a terrain instance.
 *
 * This class is the source of truth for terrain authoring. Mesh generation,
 * rendering, picking, and brush tools should read or modify this model instead
 * of writing directly into render buffers.
 */
class TerrainData(
    val width: Int,
    val height: Int,
    val vertexSpacing: Float,
    heights: FloatArray? = null,
) {
    private val heights = heights?.copyOf() ?: FloatArray(width * height)
    private val layers = mutableListOf<TerrainLayer>()
    private val layerWeights = linkedMapOf<Int, FloatArray>()
    private var nextLayerId: Int = 1

    init {
        require(width >= 2) { "Terrain width must be >= 2" }
        require(height >= 2) { "Terrain height must be >= 2" }
        require(vertexSpacing > 0f) { "Terrain spacing must be > 0" }
        require(this.heights.size == width * height) { "Height array size does not match terrain dimensions" }
    }

    /**
     * Terrain width in local world units, measured between the first and last vertices.
     */
    val worldWidth: Float
        get() = (width - 1) * vertexSpacing

    /**
     * Terrain depth in local world units, measured between the first and last vertices.
     */
    val worldHeight: Float
        get() = (height - 1) * vertexSpacing

    /**
     * Local X coordinate of the first height sample.
     */
    val minLocalX: Float
        get() = -worldWidth * 0.5f

    /**
     * Local Z coordinate of the first height sample.
     */
    val minLocalZ: Float
        get() = -worldHeight * 0.5f

    /**
     * Returns a defensive copy of all height samples in row-major order.
     */
    fun heightValues(): FloatArray = heights.copyOf()

    /**
     * Returns the height sample at grid coordinate [x], [y].
     */
    fun getHeight(x: Int, y: Int): Float = heights[indexOf(x, y)]

    /**
     * Replaces the height sample at grid coordinate [x], [y].
     */
    fun setHeight(x: Int, y: Int, value: Float) {
        heights[indexOf(x, y)] = value
    }

    /**
     * Converts a grid X index into centered local-space X.
     */
    fun localXAt(x: Int): Float = minLocalX + x * vertexSpacing

    /**
     * Converts a grid Y index into centered local-space Z.
     */
    fun localZAt(y: Int): Float = minLocalZ + y * vertexSpacing

    /**
     * Returns true when the local-space X/Z point lies inside the terrain bounds.
     */
    fun containsLocal(localX: Float, localZ: Float): Boolean =
        localX in minLocalX..(minLocalX + worldWidth) &&
            localZ in minLocalZ..(minLocalZ + worldHeight)

    /**
     * Bilinearly samples terrain height at a local-space X/Z point.
     */
    fun sampleHeight(localX: Float, localZ: Float): Float {
        if (!containsLocal(localX, localZ)) return 0f

        val gridX = ((localX - minLocalX) / vertexSpacing).coerceIn(0f, (width - 1).toFloat())
        val gridY = ((localZ - minLocalZ) / vertexSpacing).coerceIn(0f, (height - 1).toFloat())

        val x0 = floor(gridX).toInt().coerceIn(0, width - 1)
        val x1 = ceil(gridX).toInt().coerceIn(0, width - 1)
        val y0 = floor(gridY).toInt().coerceIn(0, height - 1)
        val y1 = ceil(gridY).toInt().coerceIn(0, height - 1)

        val tx = gridX - x0
        val ty = gridY - y0

        val h00 = getHeight(x0, y0)
        val h10 = getHeight(x1, y0)
        val h01 = getHeight(x0, y1)
        val h11 = getHeight(x1, y1)

        val top = lerp(h00, h10, tx)
        val bottom = lerp(h01, h11, tx)
        return lerp(top, bottom, ty)
    }

    /**
     * Adds a terrain layer and allocates a matching weight map.
     */
    fun addLayer(
        name: String,
        texture: AssetRef<TextureAsset>? = null,
        materialId: String? = null,
        color: TerrainLayerColorDescriptor = TerrainLayerColorDescriptor(),
        visible: Boolean = true,
        tiling: Float = 1f,
    ): TerrainLayer {
        require(layers.size < TerrainLayerLimits.MaxLayers) {
            "Terrain layer count cannot exceed ${TerrainLayerLimits.MaxLayers}"
        }
        val layerId = nextLayerId++
        val layer = TerrainLayer(
            id = layerId,
            name = sanitizeLayerName(name, layerId),
            texture = texture,
            materialId = materialId,
            color = color.clamped(),
            visible = visible,
            tiling = tiling.clampedTiling(),
        )
        layers += layer
        layerWeights[layer.id] = FloatArray(width * height)
        return layer
    }

    /**
     * Removes a terrain layer and its weight map.
     *
     * Returns true when a layer with [layerId] existed.
     */
    fun removeLayer(layerId: Int): Boolean {
        val removed = layers.removeIf { it.id == layerId }
        if (removed) {
            layerWeights.remove(layerId)
        }
        return removed
    }

    /**
     * Returns all layers in stable authoring order.
     */
    fun allLayers(): List<TerrainLayer> = layers.toList()

    fun updateLayerColor(layerId: Int, color: TerrainLayerColorDescriptor): Boolean {
        val layer = findLayer(layerId) ?: return false
        layer.color = color.clamped()
        return true
    }

    fun updateLayerVisibility(layerId: Int, visible: Boolean): Boolean {
        val layer = findLayer(layerId) ?: return false
        layer.visible = visible
        return true
    }

    fun updateLayerTiling(layerId: Int, tiling: Float): Boolean {
        val layer = findLayer(layerId) ?: return false
        layer.tiling = tiling.clampedTiling()
        return true
    }

    fun updateLayerMaterial(layerId: Int, materialId: String?): Boolean {
        val layer = findLayer(layerId) ?: return false
        layer.materialId = materialId?.trim()?.takeIf(String::isNotEmpty)
        return true
    }

    fun renameLayer(layerId: Int, name: String): Boolean {
        val layer = findLayer(layerId) ?: return false
        layer.name = sanitizeLayerName(name, layer.id)
        return true
    }

    fun moveLayerUp(layerId: Int): Boolean {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return false
        return moveLayer(layerId, index - 1)
    }

    fun moveLayerDown(layerId: Int): Boolean {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index < 0 || index >= layers.lastIndex) return false
        return moveLayer(layerId, index + 1)
    }

    fun moveLayer(layerId: Int, newIndex: Int): Boolean {
        val currentIndex = layers.indexOfFirst { it.id == layerId }
        if (currentIndex < 0 || newIndex !in layers.indices || currentIndex == newIndex) return false
        val layer = layers.removeAt(currentIndex)
        layers.add(newIndex, layer)
        return true
    }

    /**
     * Writes one layer weight sample, clamped to the normalized 0..1 range.
     */
    fun setLayerWeight(layerId: Int, x: Int, y: Int, weight: Float) {
        val weights = layerWeights.getOrPut(layerId) { FloatArray(width * height) }
        weights[indexOf(x, y)] = weight.coerceIn(0f, 1f)
    }

    /**
     * Returns one layer weight sample, or 0 when the layer has no weight map.
     */
    fun getLayerWeight(layerId: Int, x: Int, y: Int): Float =
        layerWeights[layerId]?.get(indexOf(x, y)) ?: 0f

    /**
     * Returns a defensive copy of the layer weight map, if it exists.
     */
    fun getLayerWeightMap(layerId: Int): FloatArray? = layerWeights[layerId]?.copyOf()

    private fun findLayer(layerId: Int): TerrainLayer? =
        layers.firstOrNull { it.id == layerId }

    /**
     * Converts grid coordinates into the row-major sample index.
     */
    fun indexOf(x: Int, y: Int): Int {
        require(x in 0 until width) { "Terrain x index out of range: $x" }
        require(y in 0 until height) { "Terrain y index out of range: $y" }
        return y * width + x
    }

    /**
     * Creates a data-only snapshot for future persistence.
     */
    fun toDescriptor(): TerrainDataDescriptor =
        TerrainDataDescriptor(
            width = width,
            height = height,
            vertexSpacing = vertexSpacing,
            heights = heights.copyOf(),
            layers = layers.map { layer ->
                TerrainLayerDescriptor(
                    id = layer.id,
                    name = layer.name,
                    materialId = layer.materialId,
                    color = layer.color.clamped(),
                    visible = layer.visible,
                    tiling = layer.tiling.clampedTiling(),
                    weights = layerWeights[layer.id]?.copyOf(),
                )
            },
        )

    companion object {
        /**
         * Restores terrain data from a descriptor produced by [toDescriptor].
         */
        fun fromDescriptor(descriptor: TerrainDataDescriptor): TerrainData {
            require(descriptor.layers.size <= TerrainLayerLimits.MaxLayers) {
                "Terrain layer count cannot exceed ${TerrainLayerLimits.MaxLayers}"
            }
            val terrain = TerrainData(
                width = descriptor.width,
                height = descriptor.height,
                vertexSpacing = descriptor.vertexSpacing,
                heights = descriptor.heights,
            )
            descriptor.layers.forEach { layer ->
                val restored = TerrainLayer(
                    id = layer.id,
                    name = sanitizeLayerName(layer.name, layer.id),
                    texture = null,
                    materialId = layer.materialId,
                    color = layer.color.clamped(),
                    visible = layer.visible,
                    tiling = layer.tiling.clampedTiling(),
                )
                terrain.layers += restored
                terrain.layerWeights[restored.id] =
                    layer.weights?.copyOf() ?: FloatArray(descriptor.width * descriptor.height)
                terrain.nextLayerId = maxOf(terrain.nextLayerId, restored.id + 1)
            }
            return terrain
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun sanitizeLayerName(name: String, id: Int): String =
    name.trim().takeIf(String::isNotEmpty) ?: "Layer $id"

private fun Float.clampedTiling(): Float = coerceIn(0.1f, 128f)

private fun TerrainLayerColorDescriptor.clamped(): TerrainLayerColorDescriptor =
    TerrainLayerColorDescriptor(
        r = r.coerceIn(0f, 1f),
        g = g.coerceIn(0f, 1f),
        b = b.coerceIn(0f, 1f),
        a = a.coerceIn(0f, 1f),
    )
