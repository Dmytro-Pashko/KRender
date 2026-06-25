package com.pashkd.krender.engine.tools.common

import java.util.ArrayDeque

/**
 * One snapshot-based undo/redo entry for editor tools.
 *
 * [snapshot] is the full immutable state to restore, while [context] stores any
 * extra editor-only selection/cursor information that should travel with it.
 */
data class SnapshotEditorHistoryEntry<TSnapshot, TContext>(
    val snapshot: TSnapshot,
    val context: TContext,
    val description: String,
)

/**
 * Generic snapshot-based editor history.
 *
 * This stores full immutable snapshots instead of command deltas. It is useful
 * for editor documents that are already copy-based and small enough that whole
 * snapshots are simpler and safer than patch logic.
 */
class SnapshotEditorHistory<TSnapshot, TContext>(
    private val capacity: Int = 100,
) {
    private val undoStack = ArrayDeque<SnapshotEditorHistoryEntry<TSnapshot, TContext>>()
    private val redoStack = ArrayDeque<SnapshotEditorHistoryEntry<TSnapshot, TContext>>()

    init {
        require(capacity > 0) { "Snapshot editor history capacity must be positive." }
    }

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun recordBeforeChange(entry: SnapshotEditorHistoryEntry<TSnapshot, TContext>) {
        undoStack.addLast(entry)
        while (undoStack.size > capacity) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(current: SnapshotEditorHistoryEntry<TSnapshot, TContext>): SnapshotEditorHistoryEntry<TSnapshot, TContext>? {
        if (undoStack.isEmpty()) return null
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: SnapshotEditorHistoryEntry<TSnapshot, TContext>): SnapshotEditorHistoryEntry<TSnapshot, TContext>? {
        if (redoStack.isEmpty()) return null
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        return next
    }
}
