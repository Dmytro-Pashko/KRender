package com.pashkd.krender.engine.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Editor-only CPU terrain material preview baker.
 *
 * This creates a simple diffuse texture for immediate paint feedback in the
 * Terrain Editor. It is not a runtime splat shader and it is not persisted.
 */
class TerrainMaterialPreviewBaker(
    private val materialLibrary: TerrainMaterialLibrary,
    private val logger: Logger? = null,
) {
    fun bakePixmap(
        terrain: TerrainData,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
    ): Pixmap {
        require(resolution > 0) { "Preview resolution must be > 0" }
        val output = Pixmap(resolution, resolution, Pixmap.Format.RGBA8888)
        val textureCache = mutableMapOf<String, Pixmap?>()
        try {
            val denominator = (resolution - 1).coerceAtLeast(1).toFloat()
            for (y in 0 until resolution) {
                val v = y / denominator
                val localZ = terrain.minLocalZ + v * terrain.worldHeight
                for (x in 0 until resolution) {
                    val u = x / denominator
                    val localX = terrain.minLocalX + u * terrain.worldWidth
                    val color = blendPreviewColor(terrain, u, v, localX, localZ, blendMode, textureCache)
                    output.drawPixel(x, y, toIntRgba8888(color))
                }
            }
            return output
        } catch (error: Exception) {
            output.dispose()
            throw error
        } finally {
            textureCache.values.filterNotNull().distinct().forEach(Pixmap::dispose)
        }
    }

    private fun blendPreviewColor(
        terrain: TerrainData,
        u: Float,
        v: Float,
        localX: Float,
        localZ: Float,
        blendMode: TerrainLayerBlendMode,
        textureCache: MutableMap<String, Pixmap?>,
    ): TerrainLayerColorDescriptor {
        val samples = terrain.allLayers()
            .filter { it.visible }
            .map { layer ->
                TerrainMaterialPreviewLayerSample(
                    color = sampleLayerTextureColor(layer, u, v, textureCache),
                    weight = terrain.sampleLayerWeight(layer.id, localX, localZ),
                    visible = true,
                )
            }
        return TerrainMaterialPreviewColorBlender.blend(samples, blendMode, BASE_FALLBACK_COLOR)
    }

    private fun sampleLayerTextureColor(
        layer: TerrainLayer,
        u: Float,
        v: Float,
        textureCache: MutableMap<String, Pixmap?>,
    ): TerrainLayerColorDescriptor {
        val material = materialLibrary.find(layer.materialId) ?: return layer.color
        val source = loadMaterialPixmap(material, textureCache) ?: return fallbackColor(material)
        val tiledU = fract(u * layer.tiling)
        val tiledV = fract(v * layer.tiling)
        return samplePixmapNearest(source, tiledU, tiledV)
    }

    private fun loadMaterialPixmap(
        material: TerrainMaterialDescriptor,
        textureCache: MutableMap<String, Pixmap?>,
    ): Pixmap? {
        val path = material.albedoTexture.trim()
        if (path.isBlank()) return null
        if (textureCache.containsKey(path)) return textureCache[path]

        val pixmap = try {
            Pixmap(Gdx.files.internal(path))
        } catch (error: Exception) {
            logger?.warn(TAG, error) { "Failed to load terrain preview texture '$path': ${error.message}" }
            null
        }
        textureCache[path] = pixmap
        return pixmap
    }

    private fun samplePixmapNearest(
        pixmap: Pixmap,
        u: Float,
        v: Float,
    ): TerrainLayerColorDescriptor {
        val x = (u.coerceIn(0f, 1f) * (pixmap.width - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, pixmap.width - 1)
        val y = (v.coerceIn(0f, 1f) * (pixmap.height - 1).coerceAtLeast(0)).roundToInt()
            .coerceIn(0, pixmap.height - 1)
        val rgba = pixmap.getPixel(x, y)
        return TerrainLayerColorDescriptor(
            r = ((rgba ushr 24) and 0xff) / 255f,
            g = ((rgba ushr 16) and 0xff) / 255f,
            b = ((rgba ushr 8) and 0xff) / 255f,
            a = (rgba and 0xff) / 255f,
        )
    }

    private fun fallbackColor(material: TerrainMaterialDescriptor): TerrainLayerColorDescriptor =
        material.fallbackColor

    private fun toIntRgba8888(color: TerrainLayerColorDescriptor): Int {
        val r = (color.r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val a = (color.a.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (r shl 24) or (g shl 16) or (b shl 8) or a
    }

    private fun fract(value: Float): Float = value - floor(value)

    private companion object {
        private const val TAG = "TerrainMaterialPreviewBaker"
        private val BASE_FALLBACK_COLOR = TerrainLayerColorDescriptor(0.38f, 0.48f, 0.30f, 1f)
    }
}

internal data class TerrainMaterialPreviewLayerSample(
    val color: TerrainLayerColorDescriptor,
    val weight: Float,
    val visible: Boolean = true,
)

internal object TerrainMaterialPreviewColorBlender {
    fun blend(
        samples: List<TerrainMaterialPreviewLayerSample>,
        blendMode: TerrainLayerBlendMode,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        val visibleSamples = samples.filter { it.visible }
        return when (blendMode) {
            TerrainLayerBlendMode.WeightedAverage -> weightedAverageColor(visibleSamples, baseFallbackColor)
            TerrainLayerBlendMode.OrderedAlpha -> orderedAlphaColor(visibleSamples, baseFallbackColor)
            TerrainLayerBlendMode.MaxWeight -> maxWeightColor(visibleSamples, baseFallbackColor)
        }
    }

    private fun weightedAverageColor(
        samples: List<TerrainMaterialPreviewLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var totalWeight = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight <= 0f) return@forEach
            r += sample.color.r * weight
            g += sample.color.g * weight
            b += sample.color.b * weight
            a += sample.color.a * weight
            totalWeight += weight
        }
        if (totalWeight <= 0f) return baseFallbackColor
        return TerrainLayerColorDescriptor(r / totalWeight, g / totalWeight, b / totalWeight, a / totalWeight)
    }

    private fun orderedAlphaColor(
        samples: List<TerrainMaterialPreviewLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var result = baseFallbackColor
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight <= 0f) return@forEach
            result = lerp(result, sample.color, weight)
        }
        return result
    }

    private fun maxWeightColor(
        samples: List<TerrainMaterialPreviewLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var bestSample: TerrainMaterialPreviewLayerSample? = null
        var bestWeight = 0f
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight > bestWeight) {
                bestWeight = weight
                bestSample = sample
            }
        }
        return bestSample?.color ?: baseFallbackColor
    }

    private fun lerp(
        from: TerrainLayerColorDescriptor,
        to: TerrainLayerColorDescriptor,
        t: Float,
    ): TerrainLayerColorDescriptor =
        TerrainLayerColorDescriptor(
            r = from.r + (to.r - from.r) * t,
            g = from.g + (to.g - from.g) * t,
            b = from.b + (to.b - from.b) * t,
            a = from.a + (to.a - from.a) * t,
        )
}
