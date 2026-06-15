package com.pashkd.krender.engine.terrain

/**
 * One heightfield sample mutation captured by a terrain edit patch.
 *
 * [oldHeight] is the value needed for undo, while [newHeight] is the value
 * reapplied during redo.
 */
data class HeightSampleChange(
    val x: Int,
    val y: Int,
    val oldHeight: Float,
    val newHeight: Float,
)

/**
 * One paint-layer weight mutation captured by a terrain edit patch.
 *
 * The layer id is stored explicitly because multiple terrain layers can share
 * the same sample coordinates while holding different weights.
 */
data class LayerWeightChange(
    val layerId: Int,
    val x: Int,
    val y: Int,
    val oldWeight: Float,
    val newWeight: Float,
)

/**
 * Compact terrain edit record for a single authoring action.
 *
 * Patch-based history keeps [TerrainData] as the source of truth while storing
 * only changed samples. Full terrain snapshots would scale with terrain size
 * per brush action, which is too expensive for interactive painting.
 *
 * A patch may contain both height edits and layer-weight edits, but it stores
 * only the samples that actually changed between the start and end of the edit.
 */
data class TerrainEditPatch(
    /** Human-readable label shown by history/undo UI. */
    val label: String,
    /** Height changes captured for this authoring action. */
    val heightChanges: List<HeightSampleChange> = emptyList(),
    /** Layer weight changes captured for this authoring action. */
    val layerWeightChanges: List<LayerWeightChange> = emptyList(),
) {
    /** Returns `true` when the patch contains no effective terrain mutations. */
    fun isEmpty(): Boolean = heightChanges.isEmpty() && layerWeightChanges.isEmpty()
}

/**
 * Read-only summary used by editor UI history previews.
 */
data class TerrainEditPatchInfo(
    /** User-visible action label, such as "Raise terrain". */
    val label: String,
    /** Number of height samples touched by the patch. */
    val heightChanges: Int,
    /** Number of layer-weight samples touched by the patch. */
    val layerChanges: Int,
)

/**
 * Accumulates all sample writes made during one brush drag.
 *
 * A brush may touch the same sample many times while the pointer moves. The
 * builder keeps the first old value and replaces only the new value, so one
 * drag becomes one compact undo step without copying full height or weight maps.
 *
 * Internally the builder indexes changes by sample location so repeated writes
 * to the same vertex coalesce into one final patch entry.
 */
class TerrainEditPatchBuilder(
    private val label: String,
) {
    private val heightChanges = linkedMapOf<Int, MutableHeightSampleChange>()
    private val layerWeightChanges = linkedMapOf<Long, MutableLayerWeightChange>()

    /**
     * Records one height mutation for the current in-progress patch.
     *
     * If the same sample is edited multiple times, the first old value is kept
     * and only the latest new value is updated.
     */
    fun recordHeightChange(
        data: TerrainData,
        x: Int,
        y: Int,
        oldHeight: Float,
        newHeight: Float,
    ) {
        val key = data.indexOf(x, y)
        val existing = heightChanges[key]
        if (existing == null) {
            // First touch of this sample in the current drag: capture the full
            // before/after pair so undo can restore the original value.
            heightChanges[key] = MutableHeightSampleChange(x, y, oldHeight, newHeight)
        } else {
            // Subsequent touches during the same drag keep the original old value
            // but replace the final value that redo should restore.
            existing.newHeight = newHeight
        }
    }

    /**
     * Records one terrain-layer weight mutation for the current in-progress patch.
     *
     * Layer id is part of the key because different layers at the same terrain
     * coordinate must produce distinct history entries.
     */
    fun recordLayerWeightChange(
        layerId: Int,
        data: TerrainData,
        x: Int,
        y: Int,
        oldWeight: Float,
        newWeight: Float,
    ) {
        val key = layerWeightKey(layerId, data.indexOf(x, y))
        val existing = layerWeightChanges[key]
        if (existing == null) {
            layerWeightChanges[key] = MutableLayerWeightChange(layerId, x, y, oldWeight, newWeight)
        } else {
            existing.newWeight = newWeight
        }
    }

    /**
     * Materializes the compact immutable patch for history storage.
     *
     * Any entries whose value returned to its original state are filtered out so
     * no-op drags do not waste undo space.
     */
    fun build(): TerrainEditPatch =
        TerrainEditPatch(
            label = label,
            heightChanges =
                heightChanges.values
                    // If a sample was modified and then returned to its original
                    // value during the same drag, the edit is not worth storing.
                    .filter { it.oldHeight != it.newHeight }
                    .map { HeightSampleChange(it.x, it.y, it.oldHeight, it.newHeight) },
            layerWeightChanges =
                layerWeightChanges.values
                    .filter { it.oldWeight != it.newWeight }
                    .map { LayerWeightChange(it.layerId, it.x, it.y, it.oldWeight, it.newWeight) },
        )

    /** Mutable staging record used while coalescing repeated height writes. */
    private data class MutableHeightSampleChange(
        val x: Int,
        val y: Int,
        val oldHeight: Float,
        var newHeight: Float,
    )

    /** Mutable staging record used while coalescing repeated layer-weight writes. */
    private data class MutableLayerWeightChange(
        val layerId: Int,
        val x: Int,
        val y: Int,
        val oldWeight: Float,
        var newWeight: Float,
    )

    /** Packs layer id and sample index into one map key for per-layer deduplication. */
    private fun layerWeightKey(
        layerId: Int,
        sampleIndex: Int,
    ): Long = (layerId.toLong() shl 32) or (sampleIndex.toLong() and 0xffffffffL)
}

/**
 * Undo/redo stack for terrain edits.
 *
 * The history stores compact [TerrainEditPatch] records rather than full terrain
 * snapshots. Each pushed patch advances the logical revision number so the
 * editor can answer questions like "are there unsaved changes?" efficiently.
 */
class TerrainEditHistory(
    private val maxUndoSteps: Int = 100,
) {
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private var nextRevision: Long = 1L
    private var currentRevision: Long = 0L
    private var cleanRevision: Long = 0L

    init {
        require(maxUndoSteps > 0) { "Terrain edit history must keep at least one undo step" }
    }

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    /**
     * Pushes a new patch onto the undo stack.
     *
     * Pushing clears redo history because a new edit creates a new timeline.
     * Returns `false` when the patch is empty and therefore not stored.
     */
    fun push(patch: TerrainEditPatch): Boolean {
        if (patch.isEmpty()) return false
        val entry =
            HistoryEntry(
                patch = patch,
                beforeRevision = currentRevision,
                afterRevision = nextRevision(),
            )
        undoStack.addLast(entry)
        if (undoStack.size > maxUndoSteps) {
            // Trim the oldest entry to keep memory bounded.
            undoStack.removeFirst()
        }
        redoStack.clear()
        currentRevision = entry.afterRevision
        return true
    }

    /**
     * Reverts the most recent patch and moves it onto the redo stack.
     */
    fun undo(data: TerrainData): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        applyOldValues(data, entry.patch)
        currentRevision = entry.beforeRevision
        redoStack.addLast(entry)
        return true
    }

    /**
     * Reapplies the most recently undone patch and moves it back to undo history.
     */
    fun redo(data: TerrainData): Boolean {
        val entry = redoStack.removeLastOrNull() ?: return false
        applyNewValues(data, entry.patch)
        currentRevision = entry.afterRevision
        undoStack.addLast(entry)
        return true
    }

    /**
     * Clears both undo and redo stacks and advances to a fresh dirty revision.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        currentRevision = nextRevision()
    }

    /**
     * Clears history and marks the resulting revision as clean.
     */
    fun clearAndMarkClean() {
        undoStack.clear()
        redoStack.clear()
        currentRevision = nextRevision()
        cleanRevision = currentRevision
    }

    /** Marks the current revision as matching the last saved state. */
    fun markClean() {
        cleanRevision = currentRevision
    }

    /** Returns whether the current logical revision differs from the clean revision. */
    fun hasUnsavedChanges(): Boolean = currentRevision != cleanRevision

    /** Returns the current logical revision id. */
    fun currentRevision(): Long = currentRevision

    /** Returns the revision id that represents the last clean/saved state. */
    fun cleanRevision(): Long = cleanRevision

    /** Returns the label of the next undo action, if any. */
    fun peekUndoLabel(): String? = undoStack.lastOrNull()?.patch?.label

    /** Returns the label of the next redo action, if any. */
    fun peekRedoLabel(): String? = redoStack.lastOrNull()?.patch?.label

    /** Returns how many undo entries are currently available. */
    fun undoCount(): Int = undoStack.size

    /** Returns how many redo entries are currently available. */
    fun redoCount(): Int = redoStack.size

    /**
     * Returns a heuristic estimate of memory used by stored patches.
     *
     * This is not an exact JVM allocation count; it simply scales by the number
     * of recorded sample changes and constant per-entry size estimates.
     */
    fun estimatedMemoryBytes(): Long = (undoStack.asSequence() + redoStack.asSequence()).sumOf { it.patch.estimatedMemoryBytes() }

    /** Returns the newest undo entries formatted for UI preview lists. */
    fun getUndoPreview(limit: Int = 10): List<TerrainEditPatchInfo> = undoStack.reversed().take(limit.coerceAtLeast(0)).map { it.patch.toInfo() }

    /** Returns the newest redo entries formatted for UI preview lists. */
    fun getRedoPreview(limit: Int = 10): List<TerrainEditPatchInfo> = redoStack.reversed().take(limit.coerceAtLeast(0)).map { it.patch.toInfo() }

    /**
     * Undoes entries until the requested visible undo index has been consumed.
     *
     * For example, `index = 0` undoes the most recent patch, `index = 1` undoes
     * the two most recent patches, and so on.
     */
    fun jumpToUndoIndex(
        index: Int,
        data: TerrainData,
    ) {
        require(index >= 0) { "Undo index must be >= 0" }
        repeat(minOf(index + 1, undoStack.size)) {
            undo(data)
        }
    }

    /** Applies the undo-side values stored in a patch. */
    private fun applyOldValues(
        data: TerrainData,
        patch: TerrainEditPatch,
    ) {
        patch.heightChanges.forEach { change ->
            data.setHeight(change.x, change.y, change.oldHeight)
        }
        patch.layerWeightChanges.forEach { change ->
            data.setLayerWeight(change.layerId, change.x, change.y, change.oldWeight)
        }
    }

    /** Applies the redo-side values stored in a patch. */
    private fun applyNewValues(
        data: TerrainData,
        patch: TerrainEditPatch,
    ) {
        patch.heightChanges.forEach { change ->
            data.setHeight(change.x, change.y, change.newHeight)
        }
        patch.layerWeightChanges.forEach { change ->
            data.setLayerWeight(change.layerId, change.x, change.y, change.newWeight)
        }
    }

    /** Converts a full patch into the lighter summary shown by history UI. */
    private fun TerrainEditPatch.toInfo(): TerrainEditPatchInfo =
        TerrainEditPatchInfo(
            label = label,
            heightChanges = heightChanges.size,
            layerChanges = layerWeightChanges.size,
        )

    /** Estimates the storage cost of this patch using fixed per-change constants. */
    private fun TerrainEditPatch.estimatedMemoryBytes(): Long = heightChanges.size * HEIGHT_CHANGE_BYTES + layerWeightChanges.size * LAYER_WEIGHT_CHANGE_BYTES

    /** Returns the next monotonically increasing revision id. */
    private fun nextRevision(): Long = nextRevision++

    /** One history node plus the revision interval it spans. */
    private data class HistoryEntry(
        val patch: TerrainEditPatch,
        val beforeRevision: Long,
        val afterRevision: Long,
    )

    private companion object {
        private const val HEIGHT_CHANGE_BYTES = 16L
        private const val LAYER_WEIGHT_CHANGE_BYTES = 24L
    }
}
