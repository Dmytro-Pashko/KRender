package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import java.util.ArrayDeque

/**
 * One undo/redo snapshot for UiComposer document editing.
 *
 * The document is the actual undoable state. selectedNodeId is stored only as
 * editor navigation context so undo/redo can restore a useful selection.
 */
data class UiComposerHistoryEntry(
    val document: UiSceneDocument,
    val selectedNodeId: String?,
    val description: String,
)

/**
 * Snapshot-based document history for UiComposer.
 *
 * This intentionally stores whole UiSceneDocument snapshots instead of command
 * deltas. `.krui` documents are small and immutable/copy-based, so snapshots are
 * simpler, safer, and easier to maintain while the editor is evolving.
 */
class UiComposerHistory(
    private val capacity: Int = 100,
) {
    private val undoStack = ArrayDeque<UiComposerHistoryEntry>()
    private val redoStack = ArrayDeque<UiComposerHistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun recordBeforeChange(entry: UiComposerHistoryEntry) {
        undoStack.addLast(entry)
        while (undoStack.size > capacity) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: UiComposerHistoryEntry): UiComposerHistoryEntry? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: UiComposerHistoryEntry): UiComposerHistoryEntry? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        return next
    }
}
