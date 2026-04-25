package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.TextureAsset
import kotlin.math.ceil
import kotlin.math.floor

data class TerrainLayer(
    val id: Int,
    var name: String,
    val texture: AssetRef<TextureAsset>? = null,
    val materialId: String? = null,
)

data class TerrainLayerDescriptor(
    val id: Int,
    val name: String,
    val texturePath: String? = null,
    val materialId: String? = null,
    val weights: FloatArray? = null,
)

data class TerrainDataDescriptor(
    val width: Int,
    val height: Int,
    val vertexSpacing: Float,
    val heights: FloatArray,
    val layers: List<TerrainLayerDescriptor> = emptyList(),
)

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

    val worldWidth: Float
        get() = (width - 1) * vertexSpacing

    val worldHeight: Float
        get() = (height - 1) * vertexSpacing

    val minLocalX: Float
        get() = -worldWidth * 0.5f

    val minLocalZ: Float
        get() = -worldHeight * 0.5f

    fun heightValues(): FloatArray = heights.copyOf()

    fun getHeight(x: Int, y: Int): Float = heights[indexOf(x, y)]

    fun setHeight(x: Int, y: Int, value: Float) {
        heights[indexOf(x, y)] = value
    }

    fun localXAt(x: Int): Float = minLocalX + x * vertexSpacing

    fun localZAt(y: Int): Float = minLocalZ + y * vertexSpacing

    fun containsLocal(localX: Float, localZ: Float): Boolean =
        localX in minLocalX..(minLocalX + worldWidth) &&
            localZ in minLocalZ..(minLocalZ + worldHeight)

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

    fun addLayer(
        name: String,
        texture: AssetRef<TextureAsset>? = null,
        materialId: String? = null,
    ): TerrainLayer {
        val layer = TerrainLayer(nextLayerId++, name, texture, materialId)
        layers += layer
        layerWeights[layer.id] = FloatArray(width * height)
        return layer
    }

    fun removeLayer(layerId: Int): Boolean {
        val removed = layers.removeIf { it.id == layerId }
        if (removed) {
            layerWeights.remove(layerId)
        }
        return removed
    }

    fun allLayers(): List<TerrainLayer> = layers.toList()

    fun setLayerWeight(layerId: Int, x: Int, y: Int, weight: Float) {
        val weights = layerWeights.getOrPut(layerId) { FloatArray(width * height) }
        weights[indexOf(x, y)] = weight.coerceIn(0f, 1f)
    }

    fun getLayerWeight(layerId: Int, x: Int, y: Int): Float =
        layerWeights[layerId]?.get(indexOf(x, y)) ?: 0f

    fun getLayerWeightMap(layerId: Int): FloatArray? = layerWeights[layerId]?.copyOf()

    fun indexOf(x: Int, y: Int): Int {
        require(x in 0 until width) { "Terrain x index out of range: $x" }
        require(y in 0 until height) { "Terrain y index out of range: $y" }
        return y * width + x
    }

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
                    texturePath = layer.texture?.path,
                    materialId = layer.materialId,
                    weights = layerWeights[layer.id]?.copyOf(),
                )
            },
        )

    companion object {
        fun fromDescriptor(descriptor: TerrainDataDescriptor): TerrainData {
            val terrain = TerrainData(
                width = descriptor.width,
                height = descriptor.height,
                vertexSpacing = descriptor.vertexSpacing,
                heights = descriptor.heights,
            )
            descriptor.layers.forEach { layer ->
                val restored = TerrainLayer(
                    id = layer.id,
                    name = layer.name,
                    texture = layer.texturePath?.let(AssetRef.Companion::texture),
                    materialId = layer.materialId,
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
