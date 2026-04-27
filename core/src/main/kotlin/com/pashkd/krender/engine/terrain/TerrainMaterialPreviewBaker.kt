package com.pashkd.krender.engine.terrain

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
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
 *
 * The baker walks a regular preview image grid, maps each output pixel back into
 * terrain-local coordinates, samples every visible terrain layer at that point,
 * and blends the resulting colors according to [TerrainLayerBlendMode].
 *
 * Texture files referenced by terrain materials are cached as [Pixmap] instances
 * so repeated preview bakes do not need to reload image data from disk.
 */
class TerrainMaterialPreviewBaker(
    private val materialLibrary: TerrainMaterialLibrary,
    private val logger: Logger? = null,
) {
    private val texturePixmapCache = linkedMapOf<String, Pixmap>()

    /**
     * Bakes a full-color terrain preview into a newly allocated pixmap.
     *
     * The output image spans the full terrain bounds. Each pixel samples the
     * terrain at the corresponding normalized position and blends visible layers
     * using the supplied [blendMode].
     */
    fun bakePixmap(
        terrain: TerrainData,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
    ): Pixmap {
        require(resolution > 0) { "Preview resolution must be > 0" }
        val output = Pixmap(resolution, resolution, Pixmap.Format.RGBA8888)
        try {
            // Map integer pixel coordinates into normalized 0..1 UV space so the
            // outermost pixels land exactly on the terrain bounds.
            val denominator = (resolution - 1).coerceAtLeast(1).toFloat()
            for (y in 0 until resolution) {
                val v = y / denominator
                val localZ = terrain.minLocalZ + v * terrain.worldHeight
                for (x in 0 until resolution) {
                    val u = x / denominator
                    val localX = terrain.minLocalX + u * terrain.worldWidth
                    val color = blendPreviewColor(terrain, u, v, localX, localZ, blendMode)
                    output.drawPixel(x, y, toIntRgba8888(color))
                }
            }
            return output
        } catch (error: Exception) {
            output.dispose()
            throw error
        }
    }

    /**
     * Bakes a grayscale mask showing the sampled weight of one terrain layer.
     *
     * White means full weight, black means zero weight. When no layer is
     * selected, the preview falls back to a solid black image.
     */
    fun bakeSelectedLayerMaskPixmap(
        terrain: TerrainData,
        selectedLayerId: Int?,
        resolution: Int,
    ): Pixmap {
        require(resolution > 0) { "Preview resolution must be > 0" }
        val output = Pixmap(resolution, resolution, Pixmap.Format.RGBA8888)
        val denominator = (resolution - 1).coerceAtLeast(1).toFloat()
        if (selectedLayerId == null) {
            output.setColor(0f, 0f, 0f, 1f)
            output.fill()
            return output
        }

        try {
            // The selected layer mask reuses the same terrain-space mapping as
            // the full preview, but writes weight into RGB for a neutral mask.
            for (y in 0 until resolution) {
                val v = y / denominator
                val localZ = terrain.minLocalZ + v * terrain.worldHeight
                for (x in 0 until resolution) {
                    val u = x / denominator
                    val localX = terrain.minLocalX + u * terrain.worldWidth
                    val weight = terrain.sampleLayerWeight(selectedLayerId, localX, localZ).coerceIn(0f, 1f)
                    output.drawPixel(
                        x,
                        y,
                        toIntRgba8888(TerrainLayerColorDescriptor(weight, weight, weight, 1f)),
                    )
                }
            }
            return output
        } catch (error: Exception) {
            output.dispose()
            throw error
        }
    }

    /**
     * Bakes the editor preview and writes it as a PNG under LibGDX local storage.
     *
     * This is a convenience wrapper around [bakePixmap] and [writePng] that also
     * guarantees the temporary pixmap is disposed after writing.
     */
    fun bakePng(
        terrain: TerrainData,
        resolution: Int,
        blendMode: TerrainLayerBlendMode,
        filePath: String,
    ): String {
        val normalizedPath = normalizePngPath(filePath)
        val pixmap = bakePixmap(terrain, resolution, blendMode)
        return try {
            writePng(pixmap, normalizedPath)
        } finally {
            pixmap.dispose()
        }
    }

    /**
     * Writes an existing preview pixmap as a PNG under LibGDX local storage.
     *
     * The path is normalized to ensure a `.png` extension and parent folders are
     * created before the image is written.
     */
    fun writePng(
        pixmap: Pixmap,
        filePath: String,
    ): String {
        val normalizedPath = normalizePngPath(filePath)
        val file = Gdx.files.local(normalizedPath)
        file.parent()?.mkdirs()
        PixmapIO.writePNG(file, pixmap)
        logger?.info(TAG) { "Saved terrain material preview PNG to '$normalizedPath'" }
        return normalizedPath
    }

    /**
     * Releases every cached material pixmap and empties the texture cache.
     */
    fun clearTextureCache() {
        texturePixmapCache.values.forEach(Pixmap::dispose)
        texturePixmapCache.clear()
    }

    /**
     * Disposes all baker-owned resources.
     */
    fun dispose() {
        clearTextureCache()
    }

    /**
     * Returns lightweight statistics for the current material texture cache.
     *
     * Memory usage is approximate and assumes 4 bytes per pixel in RGBA8888 form.
     */
    fun cacheStats(): TerrainPreviewTextureCacheStats =
        TerrainPreviewTextureCacheStats(
            textureCount = texturePixmapCache.size,
            approximateMemoryBytes = texturePixmapCache.values.sumOf { pixmap ->
                pixmap.width.toLong() * pixmap.height.toLong() * 4L
            },
        )

    /**
     * Samples every visible layer at one terrain location and blends the result
     * into a single preview color.
     */
    private fun blendPreviewColor(
        terrain: TerrainData,
        u: Float,
        v: Float,
        localX: Float,
        localZ: Float,
        blendMode: TerrainLayerBlendMode,
    ): TerrainLayerColorDescriptor {
        // Preview layers are gathered as color/weight pairs first, then blended
        // by the same logical mode selected in the editor.
        val samples = terrain.allLayers()
            .filter { it.visible }
            .map { layer ->
                TerrainMaterialPreviewLayerSample(
                    color = sampleLayerTextureColor(layer, u, v),
                    weight = terrain.sampleLayerWeight(layer.id, localX, localZ),
                    visible = true,
                )
            }
        return TerrainMaterialPreviewColorBlender.blend(samples, blendMode, BASE_FALLBACK_COLOR)
    }

    /**
     * Resolves the preview color for one terrain layer at normalized coordinates.
     *
     * If the material or its texture cannot be used, the code falls back to the
     * layer or material fallback color instead of failing the whole bake.
     */
    private fun sampleLayerTextureColor(
        layer: TerrainLayer,
        u: Float,
        v: Float,
    ): TerrainLayerColorDescriptor {
        val material = materialLibrary.find(layer.materialId) ?: return layer.color
        val source = loadMaterialPixmap(material) ?: return fallbackColor(material)
        // Tiling is applied in UV space, and fract() wraps the coordinates so the
        // material repeats seamlessly across the terrain.
        val tiledU = fract(u * layer.tiling)
        val tiledV = fract(v * layer.tiling)
        return samplePixmapNearest(source, tiledU, tiledV)
    }

    /**
     * Loads and caches the pixmap for a terrain material's albedo texture.
     *
     * Returns `null` when the material has no texture path or loading fails.
     */
    private fun loadMaterialPixmap(
        material: TerrainMaterialDescriptor,
    ): Pixmap? {
        val path = material.albedoTexture.trim()
        if (path.isBlank()) return null
        texturePixmapCache[path]?.let { return it }

        val pixmap = try {
            Pixmap(Gdx.files.internal(path))
        } catch (error: Exception) {
            logger?.warn(TAG, error) { "Failed to load terrain preview texture '$path': ${error.message}" }
            null
        }
        if (pixmap != null) {
            texturePixmapCache[path] = pixmap
        }
        return pixmap
    }

    /**
     * Samples a pixmap using nearest-neighbor lookup in normalized UV space.
     */
    private fun samplePixmapNearest(
        pixmap: Pixmap,
        u: Float,
        v: Float,
    ): TerrainLayerColorDescriptor {
        // UVs are clamped to protect against tiny floating-point overshoot, then
        // converted into integer texel coordinates.
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

    /** Returns the baked preview fallback color defined by the material itself. */
    private fun fallbackColor(material: TerrainMaterialDescriptor): TerrainLayerColorDescriptor =
        material.fallbackColor

    /** Ensures preview export paths are non-empty and end with `.png`. */
    private fun normalizePngPath(filePath: String): String {
        val trimmed = filePath.trim().takeIf(String::isNotEmpty) ?: DEFAULT_PREVIEW_EXPORT_PATH
        return if (trimmed.endsWith(".png", ignoreCase = true)) trimmed else "$trimmed.png"
    }

    /** Packs floating-point RGBA channels into LibGDX RGBA8888 integer format. */
    private fun toIntRgba8888(color: TerrainLayerColorDescriptor): Int {
        val r = (color.r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val a = (color.a.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (r shl 24) or (g shl 16) or (b shl 8) or a
    }

    /** Returns the fractional part of a value, used for repeating tiled UVs. */
    private fun fract(value: Float): Float = value - floor(value)

    private companion object {
        private const val TAG = "TerrainMaterialPreviewBaker"
        private const val DEFAULT_PREVIEW_EXPORT_PATH = "terrains/terrain_preview.png"
        private val BASE_FALLBACK_COLOR = TerrainLayerColorDescriptor(0.38f, 0.48f, 0.30f, 1f)
    }
}

/**
 * Summary of the preview baker's in-memory source texture cache.
 */
data class TerrainPreviewTextureCacheStats(
    /** Number of distinct texture paths currently cached as pixmaps. */
    val textureCount: Int,

    /** Approximate total cache size in bytes assuming RGBA8888 storage. */
    val approximateMemoryBytes: Long,
)

/**
 * One color/weight pair passed into preview color blending.
 */
internal data class TerrainMaterialPreviewLayerSample(
    /** Sampled layer color after texture lookup or fallback resolution. */
    val color: TerrainLayerColorDescriptor,

    /** Layer influence at the current terrain sample position. */
    val weight: Float,

    /** Whether the layer should participate in preview blending. */
    val visible: Boolean = true,
)

/**
 * CPU-side preview color blender used by [TerrainMaterialPreviewBaker].
 *
 * The blender mirrors the editor's terrain preview blend modes, but operates on
 * simple per-layer color samples instead of GPU shading data.
 */
internal object TerrainMaterialPreviewColorBlender {
    /**
     * Blends the provided layer samples according to the selected blend mode.
     */
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

    /**
     * Computes a normalized weighted average of all contributing layer colors.
     */
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
            // Clamp weights for safety and ignore layers with no contribution.
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

    /**
     * Applies layers in order using each weight as a simple alpha toward the next color.
     */
    private fun orderedAlphaColor(
        samples: List<TerrainMaterialPreviewLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var result = baseFallbackColor
        samples.forEach { sample ->
            // Each layer progressively pulls the result toward its own color.
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight <= 0f) return@forEach
            result = lerp(result, sample.color, weight)
        }
        return result
    }

    /**
     * Chooses the color of the single layer with the highest sampled weight.
     */
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

    /** Linearly interpolates between two colors by factor [t]. */
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
