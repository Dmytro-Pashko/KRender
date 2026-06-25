package com.pashkd.krender.engine.tools.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotEditorHistoryTest {
    @Test
    fun `empty history cannot undo or redo`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
        assertNull(history.undo(entry("current", "ctx", "Current")))
        assertNull(history.redo(entry("current", "ctx", "Current")))
    }

    @Test
    fun `record before change enables undo`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)

        history.recordBeforeChange(entry("before", "nodeA", "Edited"))

        assertTrue(history.canUndo)
        assertEquals(1, history.undoCount)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo pushes current into redo`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)
        history.recordBeforeChange(entry("before", "nodeA", "Edited"))

        val undone = history.undo(entry("current", "nodeB", "Current"))

        assertEquals(entry("before", "nodeA", "Edited"), undone)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
        assertEquals(0, history.undoCount)
        assertEquals(1, history.redoCount)
    }

    @Test
    fun `redo pushes current back into undo`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)
        history.recordBeforeChange(entry("before", "nodeA", "Edited"))
        history.undo(entry("current", "nodeB", "Current"))

        val redone = history.redo(entry("restored", "nodeC", "Current"))

        assertEquals(entry("current", "nodeB", "Current"), redone)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(1, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `recording a new change clears redo`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)
        history.recordBeforeChange(entry("before", "nodeA", "Edited"))
        history.undo(entry("current", "nodeB", "Current"))

        history.recordBeforeChange(entry("newBefore", "nodeC", "New"))

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.redoCount)
        assertNull(history.redo(entry("afterNew", "nodeD", "Current")))
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `capacity trims oldest undo entries`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 2)
        history.recordBeforeChange(entry("one", null, "One"))
        history.recordBeforeChange(entry("two", null, "Two"))
        history.recordBeforeChange(entry("three", null, "Three"))

        assertEquals(entry("three", null, "Three"), history.undo(entry("current", null, "Current")))
        assertEquals(entry("two", null, "Two"), history.undo(entry("afterThree", null, "Current")))
        assertNull(history.undo(entry("afterTwo", null, "Current")))
    }

    @Test
    fun `clear clears both stacks`() {
        val history = SnapshotEditorHistory<String, String?>(capacity = 3)
        history.recordBeforeChange(entry("before", null, "Edited"))
        history.undo(entry("current", null, "Current"))

        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `invalid capacity throws`() {
        assertFailsWith<IllegalArgumentException> {
            SnapshotEditorHistory<String, String?>(capacity = 0)
        }
    }

    private fun entry(
        snapshot: String,
        context: String?,
        description: String,
    ) = SnapshotEditorHistoryEntry(snapshot, context, description)
}
