package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.terrain.TerrainLayer
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSampler
import kotlin.math.roundToInt

/**
 * LibGDX-backed runtime terrain material sampler with a per-bake Pixmap cache.
 */
class GdxTerrainMaterialTextureSampler(
    private val logger: Logger? = null,
) : TerrainMaterialTextureSampler {
    private val pixmaps = linkedMapOf<String, Pixmap>()
    private val failedPaths = mutableSetOf<String>()
    private var unavailableFileAccessLogged = false

    override fun sample(
        layer: TerrainLayer,
        material: TerrainMaterialDescriptor,
        u: Float,
        v: Float,
    ): TerrainLayerColorDescriptor? {
        val pixmap = loadPixmap(material.albedoTexture) ?: return null
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

    override fun close() {
        pixmaps.values.forEach(Pixmap::dispose)
        pixmaps.clear()
        failedPaths.clear()
    }

    private fun loadPixmap(path: String): Pixmap? {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) return null
        pixmaps[normalizedPath]?.let { return it }
        if (normalizedPath in failedPaths) return null

        val files = Gdx.files
        if (files == null) {
            if (!unavailableFileAccessLogged) {
                unavailableFileAccessLogged = true
                logger?.warn(TAG) {
                    "Terrain material texture sampling is unavailable because Gdx.files is not initialized; falling back to material colors."
                }
            }
            return null
        }

        val pixmap = try {
            Pixmap(files.internal(normalizedPath))
        } catch (error: Exception) {
            failedPaths += normalizedPath
            logger?.warn(TAG, error) {
                "Failed to load terrain runtime texture '$normalizedPath': ${error.message}"
            }
            null
        }
        if (pixmap != null) {
            pixmaps[normalizedPath] = pixmap
        }
        return pixmap
    }

    private companion object {
        private const val TAG = "GdxTerrainMaterialSampler"
    }
}

