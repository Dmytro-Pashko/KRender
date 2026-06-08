package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode

/**
 * Replaces one node in a `.krui` document tree by id.
 *
 * This helper belongs to editor selected-node property editing. It uses immutable
 * copies so Inspector edits update the in-memory document without mutating shared
 * model instances. It intentionally does not add, delete, duplicate, reorder,
 * drag/drop, edit child structure, implement canvas selection, edit Skins,
 * introduce asset-id references, or serialize Scene2D actors.
 */
fun UiSceneDocument.updateNode(
    nodeId: String,
    transform: (UiSceneNode) -> UiSceneNode,
): UiSceneDocument {
    val updatedRoot = root.updateDescendant(nodeId, transform)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Replaces this node or one descendant by id using immutable copies.
 *
 * This helper belongs to editor selected-node property editing and keeps the
 * Phase 5 scope to scalar property replacement only. It intentionally does not
 * add/delete/reorder children, drag/drop nodes, edit Skins, resolve Asset Browser
 * assets, create asset ids, or serialize Scene2D actors.
 */
fun UiSceneNode.updateDescendant(
    nodeId: String,
    transform: (UiSceneNode) -> UiSceneNode,
): UiSceneNode {
    if (id == nodeId) return transform(this)
    var changed = false
    val updatedChildren = children.map { child ->
        val updated = child.updateDescendant(nodeId, transform)
        if (updated !== child) changed = true
        updated
    }
    return if (changed) copy(children = updatedChildren) else this
}

/**
 * Returns whether a `.krui` document contains [id].
 *
 * This helper belongs to editor selection/editing validation. It does not mutate
 * the tree, add/delete/reorder nodes, edit child structure, edit Skins, or touch
 * runtime UI state.
 */
fun UiSceneDocument.containsNodeId(id: String): Boolean =
    findUiSceneNodeById(root, id) != null
