package com.pashkd.krender.engine.terrain

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Supported terrain brush operations.
 */
enum class TerrainBrushMode {
    Raise,
    Lower,
    Flatten,
    Smooth,
    PaintLayer,
}

/**
 * Mutable editor brush settings shared by terrain tools.
 */
data class TerrainBrush(
    var radius: Float = 3f,
    var strength: Float = 4f,
    var falloff: Float = 1f,
    var mode: TerrainBrushMode = TerrainBrushMode.Raise,
    var targetLayerId: Int? = null,
)

/**
 * One brush application request in terrain-local coordinates.
 */
data class TerrainBrushStroke(
    val localX: Float,
    val localZ: Float,
    val radius: Float,
    val strength: Float,
    val falloff: Float,
    val mode: TerrainBrushMode,
    val deltaSeconds: Float,
    val flattenHeight: Float? = null,
    val targetLayerId: Int? = null,
)

/**
 * Applies brush strokes to [TerrainData].
 */
object TerrainBrushApplier {
    /**
     * Applies [stroke] to [data] and returns true if height or layer data changed.
     */
    fun apply(
        data: TerrainData,
        stroke: TerrainBrushStroke,
        patchBuilder: TerrainEditPatchBuilder? = null,
    ): Boolean {
        if (!data.containsLocal(stroke.localX, stroke.localZ)) return false

        val minX = sampleMin(stroke.localX, stroke.radius, data.minLocalX, data.vertexSpacing)
            .coerceIn(0, data.width - 1)
        val maxX = sampleMax(stroke.localX, stroke.radius, data.minLocalX, data.vertexSpacing)
            .coerceIn(0, data.width - 1)
        val minY = sampleMin(stroke.localZ, stroke.radius, data.minLocalZ, data.vertexSpacing)
            .coerceIn(0, data.height - 1)
        val maxY = sampleMax(stroke.localZ, stroke.radius, data.minLocalZ, data.vertexSpacing)
            .coerceIn(0, data.height - 1)

        val smoothHeights = if (stroke.mode == TerrainBrushMode.Smooth) {
            captureSmoothHeightSnapshot(data, minX, maxX, minY, maxY)
        } else {
            null
        }
        var changed = false

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val localX = data.localXAt(x)
                val localZ = data.localZAt(y)
                val distance = distance(localX, localZ, stroke.localX, stroke.localZ)
                if (distance > stroke.radius) continue

                val effect = brushEffect(distance, stroke.radius, stroke.falloff)
                if (effect <= 0f) continue

                when (stroke.mode) {
                    TerrainBrushMode.PaintLayer -> {
                        val layerId = stroke.targetLayerId ?: continue
                        val oldWeight = data.getLayerWeight(layerId, x, y)
                        val newWeight = (oldWeight + stroke.strength * effect * stroke.deltaSeconds)
                            .coerceIn(0f, 1f)
                        if (oldWeight != newWeight) {
                            data.setLayerWeight(layerId, x, y, newWeight)
                            patchBuilder?.recordLayerWeightChange(layerId, data, x, y, oldWeight, newWeight)
                            changed = true
                        }
                    }

                    else -> {
                        val oldHeight = data.getHeight(x, y)
                        val newHeight = when (stroke.mode) {
                            TerrainBrushMode.Raise -> oldHeight + stroke.strength * effect * stroke.deltaSeconds
                            TerrainBrushMode.Lower -> oldHeight - stroke.strength * effect * stroke.deltaSeconds
                            TerrainBrushMode.Flatten -> {
                                val target = stroke.flattenHeight ?: oldHeight
                                lerp(oldHeight, target, (stroke.strength * effect * stroke.deltaSeconds).coerceIn(0f, 1f))
                            }

                            TerrainBrushMode.Smooth -> {
                                val average = sampleAverageHeight(data, x, y, smoothHeights)
                                lerp(oldHeight, average, (stroke.strength * effect * stroke.deltaSeconds).coerceIn(0f, 1f))
                            }

                            TerrainBrushMode.PaintLayer -> oldHeight
                        }

                        if (oldHeight != newHeight) {
                            data.setHeight(x, y, newHeight)
                            patchBuilder?.recordHeightChange(data, x, y, oldHeight, newHeight)
                            changed = true
                        }
                    }
                }
            }
        }

        return changed
    }

    private fun sampleAverageHeight(
        data: TerrainData,
        x: Int,
        y: Int,
        smoothHeights: SmoothHeightSnapshot?,
    ): Float {
        var total = 0f
        var samples = 0
        for (offsetY in -1..1) {
            for (offsetX in -1..1) {
                val sampleX = (x + offsetX).coerceIn(0, data.width - 1)
                val sampleY = (y + offsetY).coerceIn(0, data.height - 1)
                total += smoothHeights?.get(sampleX, sampleY) ?: data.getHeight(sampleX, sampleY)
                samples += 1
            }
        }
        return if (samples == 0) data.getHeight(x, y) else total / samples
    }

    private fun captureSmoothHeightSnapshot(
        data: TerrainData,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): SmoothHeightSnapshot {
        val snapshotMinX = (minX - 1).coerceIn(0, data.width - 1)
        val snapshotMaxX = (maxX + 1).coerceIn(0, data.width - 1)
        val snapshotMinY = (minY - 1).coerceIn(0, data.height - 1)
        val snapshotMaxY = (maxY + 1).coerceIn(0, data.height - 1)
        val snapshotWidth = snapshotMaxX - snapshotMinX + 1
        val values = FloatArray(snapshotWidth * (snapshotMaxY - snapshotMinY + 1))

        var index = 0
        for (sampleY in snapshotMinY..snapshotMaxY) {
            for (sampleX in snapshotMinX..snapshotMaxX) {
                values[index] = data.getHeight(sampleX, sampleY)
                index += 1
            }
        }
        return SmoothHeightSnapshot(snapshotMinX, snapshotMinY, snapshotWidth, values)
    }

    private data class SmoothHeightSnapshot(
        val minX: Int,
        val minY: Int,
        val width: Int,
        val values: FloatArray,
    ) {
        fun get(x: Int, y: Int): Float = values[(y - minY) * width + (x - minX)]
    }

    private fun brushEffect(distance: Float, radius: Float, falloff: Float): Float {
        if (radius <= 0f) return 0f
        val linear = 1f - (distance / radius).coerceIn(0f, 1f)
        return linear.pow(falloff.coerceAtLeast(1f))
    }

    private fun sampleMin(position: Float, radius: Float, min: Float, spacing: Float): Int =
        floor(((position - radius) - min) / spacing).toInt()

    private fun sampleMax(position: Float, radius: Float, min: Float, spacing: Float): Int =
        floor(((position + radius) - min) / spacing).toInt()

    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x0 - x1
        val dy = y0 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
