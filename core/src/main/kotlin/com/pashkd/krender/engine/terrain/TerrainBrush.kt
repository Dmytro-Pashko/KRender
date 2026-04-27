package com.pashkd.krender.engine.terrain

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Supported terrain brush operations.
 *
 * Height-oriented modes modify the terrain elevation, while [PaintLayer] edits
 * the blend weight of a selected terrain material layer.
 */
enum class TerrainBrushMode {
    /** Raises terrain height inside the brush area. */
    Raise,

    /** Lowers terrain height inside the brush area. */
    Lower,

    /** Moves terrain height toward a fixed target level. */
    Flatten,

    /** Moves terrain height toward the average of neighboring samples. */
    Smooth,

    /** Adds or removes weight from a selected terrain layer. */
    PaintLayer,
}

/**
 * Direction used when editing a terrain layer's blend weight.
 */
enum class TerrainLayerPaintMode {
    /** Increases the selected layer's weight. */
    Add,

    /** Decreases the selected layer's weight. */
    Erase,
}

/**
 * Mutable editor brush settings shared by terrain tools.
 *
 * This is usually the long-lived editor state: UI controls update this object,
 * and tools convert it into one or more immutable [TerrainBrushStroke] values.
 */
data class TerrainBrush(
    /** Brush radius in terrain-local units. */
    var radius: Float = 3f,

    /** Base intensity of the brush effect per second. */
    var strength: Float = 4f,

    /** Edge softness exponent used by [TerrainBrushApplier.brushEffect]. */
    var falloff: Float = 1f,

    /** Currently selected brush operation. */
    var mode: TerrainBrushMode = TerrainBrushMode.Raise,

    /** Layer to paint when [mode] is [TerrainBrushMode.PaintLayer]. */
    var targetLayerId: Int? = null,
)

/**
 * One brush application request in terrain-local coordinates.
 *
 * Unlike [TerrainBrush], this object captures all values needed to execute a
 * single application step. It is immutable so one stroke is applied with a
 * stable set of parameters even if editor state changes mid-frame.
 */
data class TerrainBrushStroke(
    /** Brush center X coordinate in terrain-local space. */
    val localX: Float,

    /** Brush center Z coordinate in terrain-local space. */
    val localZ: Float,

    /** Brush radius in terrain-local units. */
    val radius: Float,

    /** Base intensity used by all brush calculations. */
    val strength: Float,

    /** Falloff exponent: 1 is linear, larger values sharpen the edge. */
    val falloff: Float,

    /** Operation to apply for this stroke. */
    val mode: TerrainBrushMode,

    /** Time slice represented by this stroke, used for frame-rate-independent editing. */
    val deltaSeconds: Float,

    /** Target level used only by [TerrainBrushMode.Flatten]. */
    val flattenHeight: Float? = null,

    /** Target layer used only by [TerrainBrushMode.PaintLayer]. */
    val targetLayerId: Int? = null,

    /** +1 to add weight, -1 to erase weight when painting layers. */
    val layerWeightDeltaSign: Float = 1f,
)

/**
 * Applies brush strokes to [TerrainData].
 *
 * The algorithm works in four phases:
 * 1. Compute a rectangular sample range that encloses the brush.
 * 2. Capture any temporary data needed by the selected mode.
 * 3. Iterate vertices inside the range and compute brush influence.
 * 4. Apply the mode-specific edit and optionally record it in a patch builder.
 */
object TerrainBrushApplier {
    /**
     * Applies [stroke] to [data] and returns `true` if height or layer data changed.
     *
     * The brush center is expressed in terrain-local coordinates. Every candidate
     * vertex is converted back to local space so the code can measure its radial
     * distance from the stroke center and derive the final influence.
     */
    fun apply(
        data: TerrainData,
        stroke: TerrainBrushStroke,
        patchBuilder: TerrainEditPatchBuilder? = null,
    ): Boolean {
        // If the brush center is not over the terrain, there is nothing to edit.
        if (!data.containsLocal(stroke.localX, stroke.localZ)) return false

        // First reduce work to the rectangle that can possibly intersect the circular brush.
        // The later distance test trims this coarse region down to the actual brush shape.
        val minX = sampleMin(stroke.localX, stroke.radius, data.minLocalX, data.vertexSpacing)
            .coerceIn(0, data.width - 1)
        val maxX = sampleMax(stroke.localX, stroke.radius, data.minLocalX, data.vertexSpacing)
            .coerceIn(0, data.width - 1)
        val minY = sampleMin(stroke.localZ, stroke.radius, data.minLocalZ, data.vertexSpacing)
            .coerceIn(0, data.height - 1)
        val maxY = sampleMax(stroke.localZ, stroke.radius, data.minLocalZ, data.vertexSpacing)
            .coerceIn(0, data.height - 1)

        // Smoothing must read from a frozen copy of the source heights for the whole stroke.
        // Without this snapshot, earlier writes in the loop would bias later samples and create
        // directional artifacts.
        val smoothHeights = if (stroke.mode == TerrainBrushMode.Smooth) {
            captureSmoothHeightSnapshot(data, minX, maxX, minY, maxY)
        } else {
            null
        }

        // This flag lets callers skip downstream work such as mesh rebuilds when
        // the stroke ends up producing no actual value changes.
        var changed = false

        // Visit each vertex in the candidate rectangle, discard vertices outside the circle,
        // then compute a falloff-scaled effect that modulates the selected brush operation.
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val localX = data.localXAt(x)
                val localZ = data.localZAt(y)
                val distance = distance(localX, localZ, stroke.localX, stroke.localZ)
                if (distance > stroke.radius) continue

                val effect = brushEffect(distance, stroke.radius, stroke.falloff)
                if (effect <= 0f) continue

                // Height editing and layer painting have different target data, so the logic is
                // split here before computing a new value for the selected mode.
                when (stroke.mode) {
                    TerrainBrushMode.PaintLayer -> {
                        val layerId = stroke.targetLayerId ?: continue
                        // Layer painting integrates weight over time, then clamps to the valid
                        // [0, 1] range expected by terrain layer blending.
                        val oldWeight = data.getLayerWeight(layerId, x, y)
                        val newWeight = (oldWeight + stroke.strength * effect * stroke.deltaSeconds * stroke.layerWeightDeltaSign)
                            .coerceIn(0f, 1f)
                        if (oldWeight != newWeight) {
                            data.setLayerWeight(layerId, x, y, newWeight)
                            // Record only the delta so undo/redo systems can reconstruct the edit
                            // without storing the entire terrain again.
                            patchBuilder?.recordLayerWeightChange(layerId, data, x, y, oldWeight, newWeight)
                            changed = true
                        }
                    }

                    else -> {
                        val oldHeight = data.getHeight(x, y)
                        // Height modes either add/subtract directly or interpolate toward a
                        // target value so that strength behaves like a blend factor per stroke.
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
                            // Height patches are recorded per vertex for the same reason as layer
                            // patches: compact incremental undo/redo support.
                            patchBuilder?.recordHeightChange(data, x, y, oldHeight, newHeight)
                            changed = true
                        }
                    }
                }
            }
        }

        return changed
    }

    /**
     * Computes the smoothed height target for one vertex.
     *
     * The current implementation uses a clamped 3x3 box filter centered on the
     * vertex. At terrain edges, missing neighbors are replaced by the closest
     * valid sample through coordinate clamping.
     */
    private fun sampleAverageHeight(
        data: TerrainData,
        x: Int,
        y: Int,
        smoothHeights: SmoothHeightSnapshot?,
    ): Float {
        // Smooth mode uses a clamped 3x3 neighborhood around the current vertex. At borders,
        // out-of-range samples collapse to the nearest valid vertex instead of being skipped.
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

    /**
     * Captures a read-only copy of heights needed by one smoothing stroke.
     *
     * A one-vertex border is included around the edited area because each center
     * sample may read neighbors at offsets `-1..1` in both axes.
     */
    private fun captureSmoothHeightSnapshot(
        data: TerrainData,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): SmoothHeightSnapshot {
        // Expand the edited area by one vertex on each side so every vertex inside the stroke can
        // read its full 3x3 neighborhood from the same immutable snapshot.
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

    /**
     * Dense row-major storage for a temporary smoothing height snapshot.
     *
     * The snapshot stores only the rectangular region needed for one stroke,
     * which is cheaper than cloning the entire terrain height map.
     */
    private data class SmoothHeightSnapshot(
        /** Inclusive minimum X index covered by [values]. */
        val minX: Int,

        /** Inclusive minimum Y index covered by [values]. */
        val minY: Int,

        /** Number of columns in the snapshot grid. */
        val width: Int,

        /** Height values laid out in row-major order. */
        val values: FloatArray,
    ) {
        /** Reads a height from the snapshot using terrain sample coordinates. */
        fun get(x: Int, y: Int): Float = values[(y - minY) * width + (x - minX)]
    }

    /**
     * Computes the normalized influence of the brush at a specific distance.
     *
     * Returned values are in the `[0, 1]` range, where 1 means full influence at
     * the brush center and 0 means no effect at or beyond the radius.
     */
    private fun brushEffect(distance: Float, radius: Float, falloff: Float): Float {
        if (radius <= 0f) return 0f
        // Map the sample to a linear 0..1 influence, then raise it to a power. A larger falloff
        // keeps the center strong while making the edge drop off more sharply.
        val linear = 1f - (distance / radius).coerceIn(0f, 1f)
        return linear.pow(falloff.coerceAtLeast(1f))
    }

    /**
     * Converts the minimum brush extent from local space into a terrain sample index.
     */
    private fun sampleMin(position: Float, radius: Float, min: Float, spacing: Float): Int =
        floor(((position - radius) - min) / spacing).toInt()

    /**
     * Converts the maximum brush extent from local space into a terrain sample index.
     */
    private fun sampleMax(position: Float, radius: Float, min: Float, spacing: Float): Int =
        floor(((position + radius) - min) / spacing).toInt()

    /**
     * Returns the Euclidean distance between two points in the terrain plane.
     *
     * The brush works in X/Z space, so the helper names the second coordinate
     * generically as `y` even though callers pass terrain Z values.
     */
    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x0 - x1
        val dy = y0 - y1
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Linear interpolation between [a] and [b] by factor [t].
     */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
