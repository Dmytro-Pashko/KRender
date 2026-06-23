package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.tools.common.SnapshotEditorHistory
import com.pashkd.krender.engine.tools.common.SnapshotEditorHistoryEntry
import com.pashkd.krender.engine.ui.scene.UiSceneDocument

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
    private val delegate = SnapshotEditorHistory<UiSceneDocument, String?>(capacity)

    val canUndo: Boolean
        get() = delegate.canUndo

    val canRedo: Boolean
        get() = delegate.canRedo

    val undoCount: Int
        get() = delegate.undoCount

    val redoCount: Int
        get() = delegate.redoCount

    fun clear() = delegate.clear()

    fun recordBeforeChange(entry: UiComposerHistoryEntry) {
        delegate.recordBeforeChange(entry.toSnapshotEntry())
    }

    fun undo(current: UiComposerHistoryEntry): UiComposerHistoryEntry? = delegate.undo(current.toSnapshotEntry())?.toUiComposerEntry()

    fun redo(current: UiComposerHistoryEntry): UiComposerHistoryEntry? = delegate.redo(current.toSnapshotEntry())?.toUiComposerEntry()
}

private fun UiComposerHistoryEntry.toSnapshotEntry(): SnapshotEditorHistoryEntry<UiSceneDocument, String?> =
    SnapshotEditorHistoryEntry(
        snapshot = document,
        context = selectedNodeId,
        description = description,
    )

private fun SnapshotEditorHistoryEntry<UiSceneDocument, String?>.toUiComposerEntry(): UiComposerHistoryEntry =
    UiComposerHistoryEntry(
        document = snapshot,
        selectedNodeId = context,
        description = description,
    )
