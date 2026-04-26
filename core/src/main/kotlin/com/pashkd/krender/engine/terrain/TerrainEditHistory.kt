package com.pashkd.krender.engine.terrain

/**
 * One heightfield sample mutation captured by a terrain edit patch.
 */
data class HeightSampleChange(
    val x: Int,
    val y: Int,
    val oldHeight: Float,
    val newHeight: Float,
)

/**
 * One paint-layer weight mutation captured by a terrain edit patch.
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
 */
data class TerrainEditPatch(
    val label: String,
    val heightChanges: List<HeightSampleChange> = emptyList(),
    val layerWeightChanges: List<LayerWeightChange> = emptyList(),
) {
    fun isEmpty(): Boolean = heightChanges.isEmpty() && layerWeightChanges.isEmpty()
}

/**
 * Read-only summary used by editor UI history previews.
 */
data class TerrainEditPatchInfo(
    val label: String,
    val heightChanges: Int,
    val layerChanges: Int,
)

/**
 * Accumulates all sample writes made during one brush drag.
 *
 * A brush may touch the same sample many times while the pointer moves. The
 * builder keeps the first old value and replaces only the new value, so one
 * drag becomes one compact undo step without copying full height or weight maps.
 */
class TerrainEditPatchBuilder(
    private val label: String,
) {
    private val heightChanges = linkedMapOf<Int, MutableHeightSampleChange>()
    private val layerWeightChanges = linkedMapOf<Long, MutableLayerWeightChange>()

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
            heightChanges[key] = MutableHeightSampleChange(x, y, oldHeight, newHeight)
        } else {
            existing.newHeight = newHeight
        }
    }

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

    fun build(): TerrainEditPatch =
        TerrainEditPatch(
            label = label,
            heightChanges = heightChanges.values
                .filter { it.oldHeight != it.newHeight }
                .map { HeightSampleChange(it.x, it.y, it.oldHeight, it.newHeight) },
            layerWeightChanges = layerWeightChanges.values
                .filter { it.oldWeight != it.newWeight }
                .map { LayerWeightChange(it.layerId, it.x, it.y, it.oldWeight, it.newWeight) },
        )

    private data class MutableHeightSampleChange(
        val x: Int,
        val y: Int,
        val oldHeight: Float,
        var newHeight: Float,
    )

    private data class MutableLayerWeightChange(
        val layerId: Int,
        val x: Int,
        val y: Int,
        val oldWeight: Float,
        var newWeight: Float,
    )

    private fun layerWeightKey(layerId: Int, sampleIndex: Int): Long =
        (layerId.toLong() shl 32) or (sampleIndex.toLong() and 0xffffffffL)
}

/**
 * Undo/redo stack for terrain edits.
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

    fun push(patch: TerrainEditPatch): Boolean {
        if (patch.isEmpty()) return false
        val entry = HistoryEntry(
            patch = patch,
            beforeRevision = currentRevision,
            afterRevision = nextRevision(),
        )
        undoStack.addLast(entry)
        if (undoStack.size > maxUndoSteps) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        currentRevision = entry.afterRevision
        return true
    }

    fun undo(data: TerrainData): Boolean {
        val entry = undoStack.removeLastOrNull() ?: return false
        applyOldValues(data, entry.patch)
        currentRevision = entry.beforeRevision
        redoStack.addLast(entry)
        return true
    }

    fun redo(data: TerrainData): Boolean {
        val entry = redoStack.removeLastOrNull() ?: return false
        applyNewValues(data, entry.patch)
        currentRevision = entry.afterRevision
        undoStack.addLast(entry)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        currentRevision = nextRevision()
    }

    fun clearAndMarkClean() {
        undoStack.clear()
        redoStack.clear()
        currentRevision = nextRevision()
        cleanRevision = currentRevision
    }

    fun markClean() {
        cleanRevision = currentRevision
    }

    fun hasUnsavedChanges(): Boolean = currentRevision != cleanRevision

    fun currentRevision(): Long = currentRevision

    fun cleanRevision(): Long = cleanRevision

    fun peekUndoLabel(): String? = undoStack.lastOrNull()?.patch?.label

    fun peekRedoLabel(): String? = redoStack.lastOrNull()?.patch?.label

    fun undoCount(): Int = undoStack.size

    fun redoCount(): Int = redoStack.size

    fun estimatedMemoryBytes(): Long =
        (undoStack.asSequence() + redoStack.asSequence()).sumOf { it.patch.estimatedMemoryBytes() }

    fun getUndoPreview(limit: Int = 10): List<TerrainEditPatchInfo> =
        undoStack.reversed().take(limit.coerceAtLeast(0)).map { it.patch.toInfo() }

    fun getRedoPreview(limit: Int = 10): List<TerrainEditPatchInfo> =
        redoStack.reversed().take(limit.coerceAtLeast(0)).map { it.patch.toInfo() }

    fun jumpToUndoIndex(index: Int, data: TerrainData) {
        require(index >= 0) { "Undo index must be >= 0" }
        repeat(minOf(index + 1, undoStack.size)) {
            undo(data)
        }
    }

    private fun applyOldValues(data: TerrainData, patch: TerrainEditPatch) {
        patch.heightChanges.forEach { change ->
            data.setHeight(change.x, change.y, change.oldHeight)
        }
        patch.layerWeightChanges.forEach { change ->
            data.setLayerWeight(change.layerId, change.x, change.y, change.oldWeight)
        }
    }

    private fun applyNewValues(data: TerrainData, patch: TerrainEditPatch) {
        patch.heightChanges.forEach { change ->
            data.setHeight(change.x, change.y, change.newHeight)
        }
        patch.layerWeightChanges.forEach { change ->
            data.setLayerWeight(change.layerId, change.x, change.y, change.newWeight)
        }
    }

    private fun TerrainEditPatch.toInfo(): TerrainEditPatchInfo =
        TerrainEditPatchInfo(
            label = label,
            heightChanges = heightChanges.size,
            layerChanges = layerWeightChanges.size,
        )

    private fun TerrainEditPatch.estimatedMemoryBytes(): Long =
        heightChanges.size * HEIGHT_CHANGE_BYTES + layerWeightChanges.size * LAYER_WEIGHT_CHANGE_BYTES

    private fun nextRevision(): Long = nextRevision++

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
