package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType

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
 * current scope to selected-node scalar property replacement only. It intentionally does not
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

/**
 * Adds [child] under the node identified by [parentId].
 *
 * This helper belongs to editor structure editing for hierarchy/inspector-driven
 * `.krui` changes. It updates the shared document model immutably so the caller
 * can validate, rebuild preview, mark dirty, and save explicitly. It does not
 * implement canvas drag/drop, canvas selection, layout solving, Skin editing,
 * Asset Browser drag/drop, asset-id references, Scene2D actor serialization, or
 * automatic saving.
 */
fun UiSceneDocument.addChildNode(
    parentId: String,
    child: UiSceneNode,
): UiSceneDocument {
    if (containsNodeId(child.id)) return this
    val updatedRoot = root.addChildToDescendant(parentId, child)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Deletes the non-root node identified by [nodeId].
 *
 * This helper belongs to editor structure editing and intentionally ignores root
 * deletion so an in-memory `.krui` document always retains a document root. It
 * does not delete through canvas interaction, solve Scene2D layout, save the
 * document automatically, edit Skins, resolve assets, or serialize actors.
 */
fun UiSceneDocument.deleteNode(nodeId: String): UiSceneDocument {
    if (root.id == nodeId) return this
    val updatedRoot = root.deleteDescendant(nodeId)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Duplicates the non-root node identified by [nodeId] next to the original.
 *
 * This helper belongs to editor structure editing. The duplicated node receives
 * [newId], and descendant ids are made unique so saving the resulting `.krui`
 * does not introduce duplicate ids. It does not duplicate the root, perform
 * canvas drag/drop, resolve layout, save automatically, edit Skins, create
 * asset-id references, or serialize Scene2D actor instances.
 */
fun UiSceneDocument.duplicateNode(
    nodeId: String,
    newId: String,
): UiSceneDocument {
    if (root.id == nodeId || containsNodeId(newId)) return this
    val source = findUiSceneNodeById(root, nodeId) ?: return this
    val usedIds = collectNodeIds().toMutableSet()
    val duplicate = source.copyWithUniqueIds(newId, usedIds)
    val updatedRoot = root.insertSiblingAfter(nodeId, duplicate)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Moves the non-root node identified by [nodeId] one slot earlier among siblings.
 *
 * This helper belongs to editor structure editing. It only changes sibling order
 * inside the shared `.krui` tree and leaves invalid root or first-sibling moves
 * unchanged. It does not implement canvas drag/drop, layout solving, automatic
 * saving, Skin editing, Asset Browser drag/drop, asset-id references, or actor
 * serialization.
 */
fun UiSceneDocument.moveNodeUp(nodeId: String): UiSceneDocument {
    if (root.id == nodeId) return this
    val updatedRoot = root.moveDescendant(nodeId, direction = -1)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Moves the non-root node identified by [nodeId] one slot later among siblings.
 *
 * This helper belongs to editor structure editing. It only changes sibling order
 * inside the shared `.krui` tree and leaves invalid root or last-sibling moves
 * unchanged. It does not implement canvas drag/drop, layout solving, automatic
 * saving, Skin editing, Asset Browser drag/drop, asset-id references, or actor
 * serialization.
 */
fun UiSceneDocument.moveNodeDown(nodeId: String): UiSceneDocument {
    if (root.id == nodeId) return this
    val updatedRoot = root.moveDescendant(nodeId, direction = 1)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Wraps the non-root node identified by [nodeId] in [wrapper].
 *
 * This helper belongs to editor structure editing. It replaces the selected
 * node with a new wrapper whose only child is the original selected node, leaving
 * parent order stable and requiring the caller to validate, rebuild, mark dirty,
 * and save explicitly. It does not wrap the root, implement canvas wrapping or
 * drag/drop, solve layout, edit Skins, pick assets, create asset ids, or serialize
 * Scene2D actors.
 */
fun UiSceneDocument.wrapNode(
    nodeId: String,
    wrapper: UiSceneNode,
): UiSceneDocument {
    if (root.id == nodeId || containsNodeId(wrapper.id)) return this
    val updatedRoot = root.wrapDescendant(nodeId, wrapper)
    return if (updatedRoot === root) this else copy(root = updatedRoot)
}

/**
 * Finds the parent node for [nodeId] in a `.krui` document.
 *
 * This helper belongs to editor structure editing and inspector context. It
 * supports hierarchy-driven add/delete/move decisions only and does not expose a
 * mutable tree handle, perform canvas selection, resolve layout, save documents,
 * edit Skins, pick assets, create asset ids, or serialize actors.
 */
fun UiSceneDocument.parentOf(nodeId: String): UiSceneNode? =
    root.findParentOf(nodeId)

/**
 * Creates an unused node id from [base] for editor-created `.krui` nodes.
 *
 * This helper belongs to editor structure editing and creation UX. It sanitizes
 * ids for stable ImGui labels and JSON readability, then appends `_1`, `_2`,
 * and so on until the id is unused. It does not reserve ids globally, inspect
 * assets, edit Skin names, save automatically, solve layout, or serialize actors.
 */
fun UiSceneDocument.uniqueNodeId(base: String): String {
    val sanitized = base.trim()
        .replace(Regex("\\s+"), "_")
        .map { char ->
            when {
                char.isLetterOrDigit() || char == '_' || char == '-' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "node" }
    if (!containsNodeId(sanitized)) return sanitized

    var suffix = 1
    while (true) {
        val candidate = "${sanitized}_$suffix"
        if (!containsNodeId(candidate)) return candidate
        suffix += 1
    }
}

/**
 * Creates a sensible editor-side default node for [type] and [id].
 *
 * This factory belongs to editor creation UX for hierarchy/inspector-driven
 * structure editing. The defaults are intentionally generic and do not hardcode
 * Woolboy assets, edit Skin data, pick assets from the Asset Browser, create
 * asset-id references, solve layout, save automatically, or serialize full
 * Scene2D actors.
 */
fun createDefaultUiSceneNode(
    type: UiSceneNodeType,
    id: String,
): UiSceneNode =
    when (type) {
        UiSceneNodeType.Stack -> UiSceneNode(id = id, type = type)
        UiSceneNodeType.Table -> UiSceneNode(id = id, type = type, spacing = 8f)
        UiSceneNodeType.Container -> UiSceneNode(id = id, type = type, align = UiSceneAlign.Center)
        UiSceneNodeType.Label -> UiSceneNode(id = id, type = type, text = "Label")
        UiSceneNodeType.TextButton -> UiSceneNode(id = id, type = type, text = "Button", action = "action.todo")
        UiSceneNodeType.ProgressBar -> UiSceneNode(id = id, type = type, min = 0f, max = 1f, step = 0.01f, value = 0.5f)
        UiSceneNodeType.Image -> UiSceneNode(id = id, type = type, texture = null)
        UiSceneNodeType.Space -> UiSceneNode(id = id, type = type, width = 16f, height = 16f)
    }

private fun UiSceneNode.addChildToDescendant(
    parentId: String,
    child: UiSceneNode,
): UiSceneNode {
    if (id == parentId) return copy(children = children + child)
    var changed = false
    val updatedChildren = children.map { existing ->
        // Recurse through children so structure edits work for any hierarchy depth.
        val updated = existing.addChildToDescendant(parentId, child)
        if (updated !== existing) changed = true
        updated
    }
    return if (changed) copy(children = updatedChildren) else this
}

private fun UiSceneNode.deleteDescendant(nodeId: String): UiSceneNode {
    if (children.none { it.id == nodeId }) {
        var changed = false
        val updatedChildren = children.map { child ->
            // Keep walking until a direct parent can remove the target child.
            val updated = child.deleteDescendant(nodeId)
            if (updated !== child) changed = true
            updated
        }
        return if (changed) copy(children = updatedChildren) else this
    }
    return copy(children = children.filterNot { it.id == nodeId })
}

private fun UiSceneNode.insertSiblingAfter(
    nodeId: String,
    sibling: UiSceneNode,
): UiSceneNode {
    val index = children.indexOfFirst { it.id == nodeId }
    if (index >= 0) {
        val updatedChildren = children.toMutableList()
        updatedChildren.add(index + 1, sibling)
        return copy(children = updatedChildren)
    }

    var changed = false
    val updatedChildren = children.map { child ->
        // Descend until the target's direct parent is found.
        val updated = child.insertSiblingAfter(nodeId, sibling)
        if (updated !== child) changed = true
        updated
    }
    return if (changed) copy(children = updatedChildren) else this
}

private fun UiSceneNode.moveDescendant(
    nodeId: String,
    direction: Int,
): UiSceneNode {
    val index = children.indexOfFirst { it.id == nodeId }
    if (index >= 0) {
        val nextIndex = index + direction
        if (nextIndex !in children.indices) return this
        val updatedChildren = children.toMutableList()
        val node = updatedChildren.removeAt(index)
        updatedChildren.add(nextIndex, node)
        return copy(children = updatedChildren)
    }

    var changed = false
    val updatedChildren = children.map { child ->
        // Moving is local to one sibling list, but the target may be deeply nested.
        val updated = child.moveDescendant(nodeId, direction)
        if (updated !== child) changed = true
        updated
    }
    return if (changed) copy(children = updatedChildren) else this
}

private fun UiSceneNode.wrapDescendant(
    nodeId: String,
    wrapper: UiSceneNode,
): UiSceneNode {
    val index = children.indexOfFirst { it.id == nodeId }
    if (index >= 0) {
        val selected = children[index]
        val updatedChildren = children.toMutableList()
        updatedChildren[index] = wrapper.copy(children = listOf(selected))
        return copy(children = updatedChildren)
    }

    var changed = false
    val updatedChildren = children.map { child ->
        // Wrapping preserves the selected subtree exactly inside the new wrapper.
        val updated = child.wrapDescendant(nodeId, wrapper)
        if (updated !== child) changed = true
        updated
    }
    return if (changed) copy(children = updatedChildren) else this
}

private fun UiSceneNode.findParentOf(nodeId: String): UiSceneNode? {
    if (children.any { it.id == nodeId }) return this
    children.forEach { child ->
        // Parent lookup is used for delete selection and inspector context.
        val parent = child.findParentOf(nodeId)
        if (parent != null) return parent
    }
    return null
}

private fun UiSceneDocument.collectNodeIds(): Set<String> {
    val ids = mutableSetOf<String>()
    root.collectNodeIdsInto(ids)
    return ids
}

private fun UiSceneNode.collectNodeIdsInto(ids: MutableSet<String>) {
    ids += id
    children.forEach { child ->
        // Duplicate operations need all current ids before cloning a subtree.
        child.collectNodeIdsInto(ids)
    }
}

private fun UiSceneNode.copyWithUniqueIds(
    newRootId: String,
    usedIds: MutableSet<String>,
): UiSceneNode {
    val assignedRootId = newRootId.ifBlank { "node" }
    usedIds += assignedRootId
    val copiedChildren = children.map { child ->
        // Descendant ids are uniqued to avoid validation errors after subtree duplication.
        val childId = uniqueIdFromUsed(child.id, usedIds)
        child.copyWithUniqueIds(childId, usedIds)
    }
    return copy(id = assignedRootId, children = copiedChildren)
}

private fun uniqueIdFromUsed(
    base: String,
    usedIds: Set<String>,
): String {
    val sanitized = base.trim()
        .replace(Regex("\\s+"), "_")
        .map { char -> if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_' }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "node" }
    if (sanitized !in usedIds) return sanitized

    var suffix = 1
    while (true) {
        val candidate = "${sanitized}_$suffix"
        if (candidate !in usedIds) return candidate
        suffix += 1
    }
}
