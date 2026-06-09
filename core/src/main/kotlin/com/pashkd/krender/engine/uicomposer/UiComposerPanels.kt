package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneScaling
import imgui.ImGui
import imgui.dsl
import java.nio.charset.StandardCharsets

/**
 * Toolbar panel for the UiComposer preview and `.krui` save/reload workflow.
 *
 * This panel belongs to editor preview UX. It exposes document reload, panel
 * layout persistence, `.krui` saving, toolbar-level reload confirmation, bounds
 * toggling, selected-node highlighting, and selection-only canvas interaction
 * toggles; it intentionally does not implement a modal framework,
 * create/delete nodes, reorder nodes, drag/drop canvas editing, resize handles,
 * multi-select, snapping, transform gizmos, edit Skins, add Asset Browser
 * picking, introduce asset-id references, edit JSON, or serialize full Scene2D
 * actors.
 */
class UiComposerToolbarPanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /** Draws save, reload confirmation, preview, and panel-layout toolbar controls. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Toolbar)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.beginDisabled(state.document == null)
        with(dsl) {
            button("Save .krui##ui_composer_save_document") {
                state.saveRequested = true
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        with(dsl) {
            button("Save Panel Layout##ui_composer_save_panel_layout") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Panel Layout##ui_composer_reset_panel_layout") {
                operations.restoreUiLayout()
            }
        }
        ImGui.sameLine()
        if (state.pendingReloadConfirmation) {
            with(dsl) {
                button("Confirm Reload##ui_composer_confirm_reload") {
                    state.reloadRequested = true
                }
            }
            ImGui.sameLine()
            with(dsl) {
                button("Cancel Reload##ui_composer_cancel_reload") {
                    state.pendingReloadConfirmation = false
                    state.statusMessage = "Reload canceled; unsaved changes kept."
                }
            }
        } else {
            with(dsl) {
                button("Reload##ui_composer_reload") {
                    if (state.dirty) {
                        state.pendingReloadConfirmation = true
                        state.statusMessage = "Unsaved changes. Click Confirm Reload to discard them."
                    } else {
                        state.reloadRequested = true
                    }
                }
            }
        }
        ImGui.sameLine()
        ImGui.checkbox("Show Bounds##ui_composer_show_bounds", state::showBounds)
        ImGui.sameLine()
        ImGui.checkbox("Highlight Selected##ui_composer_highlight_selected", state::highlightSelected)
        ImGui.sameLine()
        ImGui.checkbox("Canvas Selection##ui_composer_canvas_selection", state::canvasSelectionEnabled)
        ImGui.sameLine()
        ImGui.checkbox("Highlight Hovered##ui_composer_highlight_hovered", state::highlightHovered)
        ImGui.separator()
        ImGui.textUnformatted("Path: ${state.uiScenePath}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Hovered: ${state.hoveredNodeId ?: "<none>"}")
        ImGui.textUnformatted("Dirty: ${if (state.dirty) "yes" else "no"}")
        state.saveStatusMessage?.let { message -> ImGui.textUnformatted("Save: $message") }
        if (state.pendingReloadConfirmation) {
            ImGui.textUnformatted("Reload confirmation is toolbar-level MVP; no modal is shown.")
        } else if (state.dirty) {
            ImGui.textUnformatted("Reload requires confirmation before discarding unsaved .krui changes.")
        }
        ImGui.end()
    }
}

/**
 * Recursive hierarchy panel for UiComposer node selection.
 *
 * This panel belongs to editor UI. It selects existing `.krui` nodes by id for
 * inspection and best-effort preview highlighting, but intentionally omits
 * inline add/delete/reorder operations, drag/drop canvas editing, saving, Skin
 * editing, Asset Browser picking, asset-id references, layout solving, and actor
 * serialization.
 */
class UiComposerHierarchyPanel(
    private val state: UiComposerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /** Draws the decoded `.krui` document tree. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Hierarchy)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Hierarchy, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Hierarchy, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val document = state.document
        if (document == null) {
            ImGui.textUnformatted("No document loaded.")
        } else {
            drawNode(document.root, depth = 0, path = "root")
        }
        ImGui.end()
    }

    private fun drawNode(
        node: UiSceneNode,
        depth: Int,
        path: String,
    ) {
        // Indentation keeps the hierarchy readable without adding edit/reorder affordances.
        if (depth > 0) ImGui.indent(16f)
        val label = "${node.id} [${node.type}]##ui_composer_node_$path"
        if (ImGui.selectable(label, state.selectedNodeId == node.id)) {
            state.selectedNodeId = node.id
        }
        node.children.forEachIndexed { index, child ->
            drawNode(child, depth + 1, "$path.$index")
        }
        if (depth > 0) ImGui.unindent(16f)
    }
}

/**
 * Panel for hierarchy-driven `.krui` structure editing.
 *
 * This panel belongs to editor structure editing. It exposes add child, delete,
 * duplicate, sibling move, and simple wrapper commands for the selected hierarchy
 * node while keeping hierarchy display and scalar Inspector editing separate. It
 * intentionally does not implement canvas drag/drop, canvas resizing,
 * multi-select, snapping, transform gizmos, Skin editing, Asset Browser
 * pickers, asset-id references, add/delete through the Scene2D preview, generic
 * layout solving, automatic saving, or full Scene2D actor serialization.
 */
class UiComposerStructurePanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private var selectedAddType: UiSceneNodeType = UiSceneNodeType.Label

    /** Draws selected-node structure actions for the current `.krui` document. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Structure)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Structure, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Structure, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val document = state.document
        if (document == null) {
            ImGui.textUnformatted("No document loaded.")
            ImGui.end()
            return
        }

        val selectedNode = findUiSceneNodeById(document.root, state.selectedNodeId) ?: document.root
        val isRoot = selectedNode.id == document.root.id
        val canAddChild = selectedNode.type.isContainerLike()

        property("selected id", selectedNode.id)
        property("selected type", selectedNode.type.name)
        property("root", isRoot.toString())
        ImGui.textWrapped("Structure editing is hierarchy-based. Canvas interaction is selection-only.")
        ImGui.separator()

        drawAddChildControls(canAddChild)
        if (!canAddChild) {
            ImGui.textWrapped("Selected node type is leaf-like; add child is disabled.")
        }
        ImGui.separator()

        val hasSelectedNode = state.selectedNodeId != null || document.root.id == selectedNode.id
        ImGui.beginDisabled(!hasSelectedNode || isRoot)
        with(dsl) {
            button("Delete Selected##ui_composer_delete_selected") {
                operations.deleteSelectedNode()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Duplicate Selected##ui_composer_duplicate_selected") {
                operations.duplicateSelectedNode()
            }
        }
        ImGui.endDisabled()

        ImGui.beginDisabled(!hasSelectedNode || isRoot)
        with(dsl) {
            button("Move Up##ui_composer_move_up") {
                operations.moveSelectedNodeUp()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Move Down##ui_composer_move_down") {
                operations.moveSelectedNodeDown()
            }
        }
        ImGui.endDisabled()

        ImGui.separator()
        ImGui.beginDisabled(!hasSelectedNode || isRoot)
        with(dsl) {
            button("Wrap in Container##ui_composer_wrap_container") {
                operations.wrapSelectedNode(UiSceneNodeType.Container)
            }
        }
        with(dsl) {
            button("Wrap in Stack##ui_composer_wrap_stack") {
                operations.wrapSelectedNode(UiSceneNodeType.Stack)
            }
        }
        with(dsl) {
            button("Wrap in Table##ui_composer_wrap_table") {
                operations.wrapSelectedNode(UiSceneNodeType.Table)
            }
        }
        ImGui.endDisabled()
        ImGui.end()
    }

    private fun drawAddChildControls(canAddChild: Boolean) {
        // A compact combo keeps creation type selection local to the structure panel.
        if (ImGui.beginCombo("Child Type##ui_composer_add_child_type", selectedAddType.name)) {
            UiSceneNodeType.entries.forEach { type ->
                if (ImGui.selectable("${type.name}##ui_composer_add_child_type_${type.name}", type == selectedAddType)) {
                    selectedAddType = type
                }
            }
            ImGui.endCombo()
        }
        ImGui.beginDisabled(!canAddChild)
        with(dsl) {
            button("Add Child##ui_composer_add_child") {
                operations.addChildToSelected(selectedAddType)
            }
        }
        ImGui.endDisabled()
    }

    private fun property(
        name: String,
        value: String,
    ) {
        ImGui.textUnformatted("$name: $value")
    }
}

/**
 * Inspector panel for selected-node scalar property editing and structure context.
 *
 * This panel belongs to editor selected-node property editing. It edits scalar
 * fields on the selected `.krui` node and shows read-only parent/sibling context
 * for structure editing. It leaves document-level metadata and node type
 * read-only. It intentionally omits add/delete/duplicate/reorder controls,
 * child-list editing, drag/drop canvas editing, resize handles, multi-select,
 * Skin editing, Asset Browser picking, asset-id references, snapping, transform
 * gizmos, JSON text editing, preview payload persistence, layout solving, and
 * full Scene2D actor serialization.
 */
class UiComposerInspectorPanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val editBuffers = mutableMapOf<String, ByteArray>()

    /** Draws document details or selected-node scalar editing controls. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Inspector)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val document = state.document
        if (document == null) {
            ImGui.textUnformatted("No document loaded.")
            state.parseError?.let { error -> ImGui.textWrapped("Error: $error") }
            ImGui.end()
            return
        }

        val selectedNode = findUiSceneNodeById(document.root, state.selectedNodeId)
        if (selectedNode == null) {
            drawDocumentInfo(document)
        } else {
            drawNodeInfo(document, selectedNode)
        }
        ImGui.end()
    }

    private fun drawDocumentInfo(document: UiSceneDocument) {
        ImGui.textUnformatted("Document")
        ImGui.separator()
        property("Path", state.uiScenePath)
        property("Document id", document.id)
        property("Skin", document.skin)
        property("Schema version", document.schemaVersion.toString())
        property("Root type", document.root.type.name)
    }

    private fun drawNodeInfo(
        document: UiSceneDocument,
        node: UiSceneNode,
    ) {
        val skinMetadata = state.skinMetadata
        ImGui.textUnformatted("Selected Node")
        ImGui.separator()
        editableString(node, "id", node.id, emptyAsNull = false) { value ->
            val nextId = value.orEmpty()
            if (nextId.isBlank()) {
                state.statusMessage = "Node id cannot be blank."
            } else {
                operations.updateSelectedNode { it.copy(id = nextId) }
            }
        }
        property("type", node.type.name)
        editableBoolean(node, "visible", node.visible) { visible ->
            operations.updateSelectedNode { it.copy(visible = visible) }
        }
        drawSkinPickerWarning(document, skinMetadata)
        drawStyleEditor(node, skinMetadata)
        drawBackgroundEditor(node, skinMetadata)
        editableString(node, "text", node.text, emptyAsNull = false) { value ->
            operations.updateSelectedNode { it.copy(text = value ?: "") }
        }
        editableString(node, "action", node.action, emptyAsNull = true) { value ->
            operations.updateSelectedNode { it.copy(action = value) }
        }
        editableString(node, "texture", node.texture, emptyAsNull = true) { value ->
            operations.updateSelectedNode { it.copy(texture = value) }
        }
        editableEnum(node, "scaling", node.scaling, UiSceneScaling.entries.toList()) { scaling ->
            operations.updateSelectedNode { it.copy(scaling = scaling) }
        }
        editableFloat(node, "value", node.value, allowNull = true) { value ->
            operations.updateSelectedNode { it.copy(value = value) }
        }
        editableString(node, "valueBinding", node.valueBinding, emptyAsNull = true) { value ->
            operations.updateSelectedNode { it.copy(valueBinding = value) }
        }
        editableFloat(node, "min", node.min, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(min = value ?: it.min) }
        }
        editableFloat(node, "max", node.max, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(max = value ?: it.max) }
        }
        editableFloat(node, "step", node.step, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(step = value ?: it.step) }
        }
        editableFloat(node, "width", node.width, allowNull = true) { value ->
            operations.updateSelectedNode { it.copy(width = value) }
        }
        editableFloat(node, "height", node.height, allowNull = true) { value ->
            operations.updateSelectedNode { it.copy(height = value) }
        }
        editableOptionalEnum(node, "align", node.align, UiSceneAlign.entries.toList()) { align ->
            operations.updateSelectedNode { it.copy(align = align) }
        }
        editableFloat(node, "padding.left", node.padding.left, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(left = value ?: it.padding.left)) }
        }
        editableFloat(node, "padding.top", node.padding.top, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(top = value ?: it.padding.top)) }
        }
        editableFloat(node, "padding.right", node.padding.right, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(right = value ?: it.padding.right)) }
        }
        editableFloat(node, "padding.bottom", node.padding.bottom, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(bottom = value ?: it.padding.bottom)) }
        }
        editableFloat(node, "spacing", node.spacing, allowNull = false) { value ->
            operations.updateSelectedNode { it.copy(spacing = value ?: it.spacing) }
        }
        ImGui.separator()
        drawStructureContext(document, node)
        property("child count", node.children.size.toString())
        property("hovered node", state.hoveredNodeId)
        ImGui.separator()
        drawActorInfo(node)
    }

    private fun drawStructureContext(
        document: UiSceneDocument,
        node: UiSceneNode,
    ) {
        // Read-only context helps structure edits without duplicating edit controls in Inspector.
        val parent = document.parentOf(node.id)
        val siblingIndex = parent?.children?.indexOfFirst { it.id == node.id }
        property("parent id", parent?.id ?: "<root>")
        property("sibling index", siblingIndex?.takeIf { it >= 0 }?.toString() ?: "<none>")
    }

    private fun editableString(
        node: UiSceneNode,
        fieldName: String,
        current: String?,
        emptyAsNull: Boolean,
        onChanged: (String?) -> Unit,
    ) {
        val buffer = bufferFor(node.id, fieldName, current.orEmpty())
        if (ImGui.inputText("$fieldName##ui_composer_inspector_${node.id}_$fieldName", buffer)) {
            val rawValue = readBuffer(buffer)
            onChanged(rawValue.takeUnless { emptyAsNull && it.isBlank() })
        }
    }

    private fun editableFloat(
        node: UiSceneNode,
        fieldName: String,
        current: Float?,
        allowNull: Boolean,
        onChanged: (Float?) -> Unit,
    ) {
        val buffer = bufferFor(node.id, fieldName, current?.let(::formatFloat).orEmpty())
        if (ImGui.inputText("$fieldName##ui_composer_inspector_${node.id}_$fieldName", buffer)) {
            val rawValue = readBuffer(buffer).trim()
            if (rawValue.isBlank() && allowNull) {
                onChanged(null)
                return
            }
            val parsed = rawValue.toFloatOrNull()
            if (parsed == null) {
                state.statusMessage = "Invalid float for $fieldName."
            } else {
                onChanged(parsed)
            }
        }
    }

    private fun editableBoolean(
        node: UiSceneNode,
        fieldName: String,
        current: Boolean,
        onChanged: (Boolean) -> Unit,
    ) {
        val value = booleanArrayOf(current)
        if (ImGui.checkbox("$fieldName##ui_composer_inspector_${node.id}_$fieldName", value)) {
            onChanged(value[0])
        }
    }

    private fun editableNullableChoice(
        node: UiSceneNode,
        fieldName: String,
        current: String?,
        nullLabel: String,
        options: List<String>,
        missingSuffix: String,
        onChanged: (String?) -> Unit,
    ) {
        val preview = when {
            current == null -> nullLabel
            current in options -> current
            else -> "$current$missingSuffix"
        }
        if (!ImGui.beginCombo("$fieldName##ui_composer_inspector_${node.id}_$fieldName", preview)) return
        if (ImGui.selectable("$nullLabel##ui_composer_${node.id}_${fieldName}_none", current == null)) {
            onChanged(null)
        }
        options.forEach { value ->
            if (ImGui.selectable("$value##ui_composer_${node.id}_${fieldName}_$value", value == current)) {
                onChanged(value)
            }
        }
        if (current != null && current !in options) {
            // Keep missing values selectable so reload/save cycles preserve unknown Skin names.
            val missingLabel = "$current$missingSuffix"
            if (ImGui.selectable("$missingLabel##ui_composer_${node.id}_${fieldName}_missing", true)) {
                onChanged(current)
            }
        }
        ImGui.endCombo()
    }

    private fun <T : Enum<T>> editableEnum(
        node: UiSceneNode,
        fieldName: String,
        current: T,
        values: List<T>,
        onChanged: (T) -> Unit,
    ) {
        if (!ImGui.beginCombo("$fieldName##ui_composer_inspector_${node.id}_$fieldName", current.name)) return
        values.forEach { value ->
            if (ImGui.selectable("${value.name}##ui_composer_${node.id}_${fieldName}_${value.name}", value == current)) {
                onChanged(value)
            }
        }
        ImGui.endCombo()
    }

    private fun <T : Enum<T>> editableOptionalEnum(
        node: UiSceneNode,
        fieldName: String,
        current: T?,
        values: List<T>,
        onChanged: (T?) -> Unit,
    ) {
        val preview = current?.name ?: "<none>"
        if (!ImGui.beginCombo("$fieldName##ui_composer_inspector_${node.id}_$fieldName", preview)) return
        if (ImGui.selectable("<none>##ui_composer_${node.id}_${fieldName}_none", current == null)) {
            onChanged(null)
        }
        values.forEach { value ->
            if (ImGui.selectable("${value.name}##ui_composer_${node.id}_${fieldName}_${value.name}", value == current)) {
                onChanged(value)
            }
        }
        ImGui.endCombo()
    }

    private fun bufferFor(
        nodeId: String,
        fieldName: String,
        current: String,
    ): ByteArray {
        val key = "$nodeId:$fieldName"
        val buffer = editBuffers.getOrPut(key) {
            ByteArray(TextInputBufferSize).also { created -> writeBuffer(created, current) }
        }
        return buffer
    }

    private fun drawActorInfo(node: UiSceneNode) {
        ImGui.textUnformatted("Preview Actor")
        val actorInfo = state.selectedActorInfo
        if (actorInfo == null || actorInfo.nodeId != node.id) {
            property("actor", "<not built>")
            return
        }
        property("class", actorInfo.actorClass)
        property("x", "%.2f".format(actorInfo.x))
        property("y", "%.2f".format(actorInfo.y))
        property("width", "%.2f".format(actorInfo.width))
        property("height", "%.2f".format(actorInfo.height))
        property("visible", actorInfo.visible.toString())
    }

    private fun drawSkinPickerWarning(
        document: UiSceneDocument,
        skinMetadata: UiComposerSkinMetadata?,
    ) {
        val error = when {
            skinMetadata == null -> "Skin metadata unavailable: '${document.skin}' has not been inspected."
            skinMetadata.loadError != null -> "Skin metadata unavailable: ${skinMetadata.loadError}"
            else -> null
        }
        error?.let { message ->
            ImGui.textWrapped(message)
            ImGui.textWrapped(
                "Picker reads Skin only, does not edit Skin files, uses the document's path-based Skin, " +
                    "and falls back to manual text editing when inspection is unavailable.",
            )
        }
    }

    private fun drawStyleEditor(
        node: UiSceneNode,
        skinMetadata: UiComposerSkinMetadata?,
    ) {
        val styleOptions = styleOptionsFor(node.type, skinMetadata)
        if (styleOptions == null) {
            editableString(node, "style (manual)", node.style, emptyAsNull = true) { value ->
                operations.updateSelectedNode { it.copy(style = value) }
            }
            return
        }

        editableNullableChoice(
            node = node,
            fieldName = "style",
            current = node.style,
            nullLabel = "<default>",
            options = styleOptions,
            missingSuffix = " (missing from Skin)",
        ) { value ->
            operations.updateSelectedNode { it.copy(style = value) }
        }
    }

    private fun drawBackgroundEditor(
        node: UiSceneNode,
        skinMetadata: UiComposerSkinMetadata?,
    ) {
        val drawableOptions = skinMetadata?.takeUnless { it.loadError != null }?.drawables
        if (drawableOptions == null) {
            editableString(node, "background (manual)", node.background, emptyAsNull = true) { value ->
                operations.updateSelectedNode { it.copy(background = value) }
            }
            return
        }

        editableNullableChoice(
            node = node,
            fieldName = "background",
            current = node.background,
            nullLabel = "<none>",
            options = drawableOptions,
            missingSuffix = " (missing from Skin)",
        ) { value ->
            operations.updateSelectedNode { it.copy(background = value) }
        }
    }

    private fun property(
        name: String,
        value: String?,
    ) {
        ImGui.textUnformatted("$name: ${value ?: "<none>"}")
    }
}

/**
 * Preview-only payload panel for testing `.krui` binding placeholders.
 *
 * This panel belongs to editor preview UX, not the shared `.krui` model, runtime
 * Woolboy UI, or the future editing pipeline. Edited values rebuild the backend
 * preview only and are never saved to `.krui`, never treated as runtime state,
 * and never used for Skin editing, Asset Browser picking, asset-id references,
 * drag/drop canvas editing, node editing, or full Scene2D actor serialization.
 */
class UiComposerPreviewPayloadPanel(
    private val state: UiComposerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val valueBuffers = linkedMapOf<String, ByteArray>()

    /** Draws editable preview-only key/value fields for the current binding payload. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.PreviewPayload)
        val expanded = beginImGuiPanel(UiComposerPanelIds.PreviewPayload, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.PreviewPayload, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textWrapped("Preview-only binding values. They are not saved to .krui and are not runtime state.")
        ImGui.separator()
        with(dsl) {
            button("Reset Preview Payload##ui_composer_reset_preview_payload") {
                state.previewPayload.clear()
                state.previewPayload.putAll(DefaultPreviewPayload)
                syncBuffers(force = true)
                state.previewRebuildRequested = true
                state.statusMessage = "Preview payload reset."
            }
        }
        ImGui.separator()
        syncBuffers(force = false)
        previewPayloadKeys().forEach { key ->
            val buffer = valueBuffers.getValue(key)
            if (ImGui.inputText("${key}##ui_composer_payload_${payloadInputId(key)}", buffer)) {
                state.previewPayload[key] = readBuffer(buffer)
                state.previewRebuildRequested = true
                state.statusMessage = "Preview payload changed."
            }
        }
        ImGui.end()
    }

    private fun syncBuffers(force: Boolean) {
        previewPayloadKeys().forEach { key ->
            val value = state.previewPayload[key].orEmpty()
            val buffer = valueBuffers.getOrPut(key) { ByteArray(TextInputBufferSize) }
            if (force || readBuffer(buffer) != value) {
                writeBuffer(buffer, value)
            }
        }
    }

    private fun previewPayloadKeys(): List<String> {
        // Keep the Woolboy defaults in a stable order, then append any extra preview keys.
        val defaultKeys = DefaultPreviewPayload.keys.toList()
        val extraKeys = state.previewPayload.keys.filterNot { it in DefaultPreviewPayload }.sorted()
        return defaultKeys + extraKeys
    }

    private fun payloadInputId(key: String): String =
        key.filter { char -> char.isLetterOrDigit() || char == '_' || char == '-' }.ifBlank { "key" }
}

/**
 * Diagnostics panel for `.krui` parse and validation results.
 *
 * This panel belongs to editor UI diagnostics. It reports parser/build failures
 * and shared validator warnings while intentionally omitting automatic repairs,
 * schema changes, JSON editing, saving, Skin editing, Asset Browser picking,
 * asset-id references, drag/drop canvas editing, resize handles, multi-select,
 * snapping, transform gizmos, canvas structure editing, and full Actor
 * serialization.
 */
class UiComposerDiagnosticsPanel(
    private val state: UiComposerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /** Draws parser and validator diagnostics for the loaded document. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Diagnostics)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Diagnostics, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Diagnostics, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val parseError = state.parseError
        if (parseError != null) {
            ImGui.textWrapped("Parse/preview error: $parseError")
        }
        state.saveStatusMessage?.let { message ->
            ImGui.textWrapped("Save status: $message")
        }
        state.canvasStatusMessage?.let { message ->
            ImGui.textWrapped("Canvas status: $message")
        }
        ImGui.textUnformatted("Hovered node: ${state.hoveredNodeId ?: "<none>"}")

        val issues = state.validationIssues + state.styleValidationIssues
        ImGui.textUnformatted("Validation issues: ${issues.size}")
        if (issues.isEmpty() && parseError == null) {
            ImGui.textUnformatted("No validation issues.")
        } else {
            issues.forEachIndexed { index, issue ->
                val node = issue.nodeId ?: "document"
                ImGui.textWrapped("${index + 1}. [$node] ${issue.message}")
            }
        }
        ImGui.separator()
        ImGui.textWrapped(
            "MVP limitations: structure editing is hierarchy/inspector-driven only; preview payload is not saved " +
                "and is not runtime state; style/background picker reads Skin only and does not edit Skin files; " +
                "Skin inspection is path-based and falls back to manual text editing when unavailable; " +
                "style names are not asset ids; canvas interaction is selection-only; no canvas drag/drop; no resize handles; " +
                "no multi-select; no canvas editing; no Skin editing; no Asset Browser picker yet; no asset-id references; " +
                "no snapping; no transform gizmos; no custom shape overlay yet; no full Scene2D actor serialization; " +
                "no layout solver; reload dirty confirmation is toolbar-level only; selected/hover highlight is best-effort.",
        )
        ImGui.end()
    }
}

private const val TextInputBufferSize = 256

private fun formatFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')

private fun readBuffer(buffer: ByteArray): String {
    val end = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, end, StandardCharsets.UTF_8)
}

private fun writeBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    bytes.copyInto(buffer, endIndex = bytes.size.coerceAtMost(buffer.size - 1))
}

private fun UiSceneNodeType.isContainerLike(): Boolean =
    this == UiSceneNodeType.Stack || this == UiSceneNodeType.Table || this == UiSceneNodeType.Container

private fun styleOptionsFor(
    nodeType: UiSceneNodeType,
    skinMetadata: UiComposerSkinMetadata?,
): List<String>? {
    val metadata = skinMetadata?.takeUnless { it.loadError != null } ?: return null
    return when (nodeType) {
        UiSceneNodeType.Label -> metadata.labelStyles
        UiSceneNodeType.TextButton -> metadata.textButtonStyles
        UiSceneNodeType.ProgressBar -> metadata.progressBarStyles
        UiSceneNodeType.Stack,
        UiSceneNodeType.Table,
        UiSceneNodeType.Container,
        UiSceneNodeType.Image,
        UiSceneNodeType.Space,
            -> null
    }
}
