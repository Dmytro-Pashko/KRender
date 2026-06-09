package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneBindingDefinition
import com.pashkd.krender.engine.ui.scene.UiSceneBindingType
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneScaling
import com.pashkd.krender.engine.ui.scene.UiSceneTableOrientation
import imgui.ImGui
import imgui.dsl
import glm_.vec2.Vec2 as ImVec2
import java.nio.charset.StandardCharsets

/**
 * Toolbar panel for the UiComposer preview and `.krui` save/reload workflow.
 *
 * This panel belongs to editor preview UX. It exposes document reload, panel
 * layout persistence, `.krui` saving, and toolbar-level reload confirmation; it
 * intentionally does not implement a modal framework, create/delete nodes,
 * reorder nodes, drag/drop canvas editing, resize handles, multi-select,
 * snapping, transform gizmos, edit Skins, import/copy textures, add Asset
 * Browser drag/drop, introduce asset-id references, edit JSON, or serialize full
 * Scene2D actors.
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
        ImGui.beginDisabled(state.document == null || !state.history.canUndo)
        with(dsl) {
            button("Undo##ui_composer_undo") {
                operations.undo()
            }
        }
        ImGui.endDisabled()
        ImGui.sameLine()
        ImGui.beginDisabled(state.document == null || !state.history.canRedo)
        with(dsl) {
            button("Redo##ui_composer_redo") {
                operations.redo()
            }
        }
        ImGui.endDisabled()
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
        ImGui.separator()
        ImGui.textUnformatted("Path: ${state.uiScenePath}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Dirty: ${if (state.dirty) "yes" else "no"}")
        ImGui.textUnformatted("History: undo=${state.history.undoCount} redo=${state.history.redoCount}")
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
 * ImGui panel that reserves the visible canvas area for the Scene2D preview.
 *
 * This panel belongs to editor preview placement. It records the panel content
 * rectangle used by the backend preview renderer and canvas hit-test. It also
 * exposes editor-only resolution preset controls. It does not render `.krui`
 * itself, does not edit nodes, and does not implement drag/drop, resize handles,
 * snapping, safe-area simulation, transform gizmos, multi-select, DPI
 * simulation, or canvas structure editing.
 */
class UiComposerPreviewCanvasPanel(
    private val state: UiComposerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.PreviewCanvas)
        // Scene2D preview renders before ImGui, so this window background must stay transparent.
        ImGui.setNextWindowBgAlpha(0f)
        val expanded = beginImGuiPanel(UiComposerPanelIds.PreviewCanvas, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.PreviewCanvas, layout.title)

        if (!expanded) {
            state.canvasPanelRect = UiComposerCanvasRect()
            state.canvasPreviewRect = UiComposerCanvasRect()
            state.canvasLocalMouseX = null
            state.canvasLocalMouseY = null
            state.canvasWorldMouseX = null
            state.canvasWorldMouseY = null
            ImGui.end()
            return
        }

        drawResolutionControls()
        drawViewControls()
        drawGuideControls()
        ImGui.separator()

        val min = ImGui.cursorScreenPos
        val available = ImGui.contentRegionAvail
        val panelWidth = available.x.coerceAtLeast(1f)
        val panelHeight = available.y.coerceAtLeast(1f)

        state.canvasPanelRect = UiComposerCanvasRect(
            x = min.x,
            y = min.y,
            width = panelWidth,
            height = panelHeight,
        )

        val logical = state.previewResolutionPreset.defaultResolution(
            customWidth = state.customPreviewWidth,
            customHeight = state.customPreviewHeight,
            panelWidth = panelWidth.toInt().coerceAtLeast(1),
            panelHeight = panelHeight.toInt().coerceAtLeast(1),
        )
        state.previewLogicalWidth = logical.width
        state.previewLogicalHeight = logical.height
        state.canvasPreviewRect = computePreviewRect(
            panel = state.canvasPanelRect,
            logicalWidth = state.previewLogicalWidth,
            logicalHeight = state.previewLogicalHeight,
        )

        ImGui.invisibleButton("##ui_composer_preview_canvas_area", ImVec2(panelWidth, panelHeight))

        ImGui.end()
    }

    private fun drawResolutionControls() {
        ImGui.textUnformatted("Resolution")
        ImGui.sameLine()

        if (ImGui.beginCombo(
                "##ui_composer_preview_resolution",
                state.previewResolutionPreset.displayName(),
            )
        ) {
            UiComposerPreviewResolutionPreset.entries.forEach { preset ->
                if (ImGui.selectable(
                        "${preset.displayName()}##ui_composer_preview_resolution_${preset.name}",
                        preset == state.previewResolutionPreset,
                    )
                ) {
                    state.previewResolutionPreset = preset
                    state.statusMessage = "Preview resolution set to ${preset.displayName()}."
                }
            }
            ImGui.endCombo()
        }

        if (state.previewResolutionPreset == UiComposerPreviewResolutionPreset.Custom) {
            drawCustomResolutionInputs()
        }

        ImGui.sameLine()
        ImGui.textUnformatted("Logical: ${state.previewLogicalWidth} x ${state.previewLogicalHeight}")
    }

    private fun drawCustomResolutionInputs() {
        ImGui.sameLine()
        ImGui.setNextItemWidth(96f)
        if (ImGui.input("W##ui_composer_custom_preview_width", state::customPreviewWidth, 16, 128)) {
            state.customPreviewWidth = state.customPreviewWidth.coerceIn(1, MaxCustomPreviewSize)
            state.statusMessage = "Custom preview width set to ${state.customPreviewWidth}."
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(96f)
        if (ImGui.input("H##ui_composer_custom_preview_height", state::customPreviewHeight, 16, 128)) {
            state.customPreviewHeight = state.customPreviewHeight.coerceIn(1, MaxCustomPreviewSize)
            state.statusMessage = "Custom preview height set to ${state.customPreviewHeight}."
        }
    }

    private fun drawViewControls() {
        ImGui.sameLine()
        ImGui.textUnformatted("View")
        ImGui.sameLine()
        with(dsl) {
            button("-##ui_composer_preview_zoom_out") {
                setPreviewZoom(state, state.previewZoom / ZoomButtonStep)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("+##ui_composer_preview_zoom_in") {
                setPreviewZoom(state, state.previewZoom * ZoomButtonStep)
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Fit##ui_composer_preview_fit_camera") {
                resetPreviewCamera(state)
            }
        }
        ImGui.sameLine()
        ImGui.textUnformatted("Zoom: ${formatPercent(state.previewZoom)}")
    }

    private fun drawGuideControls() {
        ImGui.textUnformatted("Guides")
        ImGui.sameLine()
        ImGui.checkbox("Selected##ui_composer_guide_selected", state::showSelectedGuide)
        ImGui.sameLine()
        ImGui.checkbox("Hovered##ui_composer_guide_hovered", state::showHoveredGuide)
        ImGui.sameLine()
        ImGui.checkbox("Parents##ui_composer_guide_parents", state::showParentChainGuides)
        ImGui.sameLine()
        ImGui.checkbox("Padding##ui_composer_guide_padding", state::showPaddingGuides)
        ImGui.sameLine()
        ImGui.checkbox("Align##ui_composer_guide_align", state::showAlignmentGuide)
        ImGui.sameLine()
        ImGui.checkbox("Table##ui_composer_guide_table", state::showTableOrientationGuide)
        ImGui.sameLine()
        ImGui.checkbox("Label##ui_composer_guide_label", state::showNodeIdLabel)
        ImGui.sameLine()
        ImGui.checkbox("Mouse##ui_composer_guide_mouse", state::showLocalMouseCoordinates)
    }
}

/**
 * Recursive hierarchy panel for UiComposer node selection.
 *
 * This panel belongs to editor UI. It selects existing `.krui` nodes by id for
 * inspection and best-effort preview highlighting, but intentionally omits
 * inline add/delete/reorder operations, drag/drop canvas editing, saving, Skin
 * editing, Asset Browser drag/drop, asset-id references, layout solving, and actor
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
 * Skin editing, Asset Browser drag/drop, asset-id references, snapping, transform
 * gizmos, JSON text editing, layout solving, and
 * full Scene2D actor serialization.
 */
class UiComposerInspectorPanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val assets: AssetService,
    private val ui: UiService,
    private val logger: Logger,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val editBuffers = mutableMapOf<String, ByteArray>()
    private val loggedPreviewDiagnostics = mutableSetOf<String>()

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
        ImGui.separator()
        drawSkinBackedFields(document, node, skinMetadata)
        drawTypeSpecificFields(node)
        drawCommonSizeFields(node)
        ImGui.separator()
        drawStructureContext(document, node)
        property("child count", node.children.size.toString())
        ImGui.separator()
        drawActorInfo(node)
    }

    private fun drawSkinBackedFields(
        document: UiSceneDocument,
        node: UiSceneNode,
        skinMetadata: UiComposerSkinMetadata?,
    ) {
        when (node.type) {
            UiSceneNodeType.Label,
            UiSceneNodeType.TextButton,
            UiSceneNodeType.ProgressBar,
                -> {
                drawSkinPickerWarning(document, skinMetadata)
                drawStyleEditor(node, skinMetadata)
                ImGui.separator()
            }

            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
                -> {
                drawSkinPickerWarning(document, skinMetadata)
                drawBackgroundEditor(node, skinMetadata)
                ImGui.separator()
            }

            UiSceneNodeType.Stack,
            UiSceneNodeType.Image,
            UiSceneNodeType.Space,
                -> Unit
        }
    }

    private fun drawTypeSpecificFields(node: UiSceneNode) {
        when (node.type) {
            UiSceneNodeType.Label -> {
                drawTextEditor(node)
                drawAlignEditor(node)
            }

            UiSceneNodeType.TextButton -> {
                drawTextEditor(node)
                drawActionEditor(node)
            }

            UiSceneNodeType.Image -> {
                drawTextureEditor(node)
                drawImageScalingEditor(node)
            }

            UiSceneNodeType.ProgressBar -> {
                drawProgressBarEditor(node)
            }

            UiSceneNodeType.Table -> {
                drawTableLayoutEditor(node)
            }

            UiSceneNodeType.Container -> {
                drawContainerLayoutEditor(node)
            }

            UiSceneNodeType.Stack,
            UiSceneNodeType.Space,
                -> Unit
        }
    }

    private fun drawTextEditor(node: UiSceneNode) {
        editableString(node, "text", node.text, emptyAsNull = false) { value ->
            operations.updateSelectedNode { it.copy(text = value ?: "") }
        }
        drawPlaceholderBindingControls(
            node = node,
            fieldName = "text",
            comboLabel = "Text Binding",
            buttonLabel = "Insert into text",
            current = node.text.orEmpty(),
            separatorAfterButton = true,
        ) { next ->
            writeBuffer(bufferFor(node.id, "text", node.text.orEmpty()), next)
            operations.updateSelectedNode { it.copy(text = next) }
        }
    }

    private fun drawActionEditor(node: UiSceneNode) {
        editableString(node, "action", node.action, emptyAsNull = true) { value ->
            operations.updateSelectedNode { it.copy(action = value) }
        }
        drawPlaceholderBindingControls(
            node = node,
            fieldName = "action",
            comboLabel = "Action Binding",
            buttonLabel = "Insert into action",
            current = node.action.orEmpty(),
            separatorAfterButton = true,
        ) { next ->
            writeBuffer(bufferFor(node.id, "action", node.action.orEmpty()), next)
            operations.updateSelectedNode { it.copy(action = next) }
        }
    }

    private fun drawImageScalingEditor(node: UiSceneNode) {
        editableEnum(
            node,
            "scaling",
            node.scaling,
            UiSceneScaling.entries.toList(),
            tooltip = "Controls how Image texture content fits inside the image actor bounds.",
        ) { scaling ->
            operations.updateSelectedNode { it.copy(scaling = scaling) }
        }
    }

    private fun drawProgressBarEditor(node: UiSceneNode) {
        editableFloat(
            node,
            "value",
            node.value,
            allowNull = true,
            tooltip = "Static ProgressBar value used when no valueBinding overrides it.",
        ) { value ->
            operations.updateSelectedNode { it.copy(value = value) }
        }
        editableString(
            node,
            "valueBinding",
            node.valueBinding,
            emptyAsNull = true,
            tooltip = "Raw binding key used by ProgressBar to read its preview/runtime value.",
        ) { value ->
            operations.updateSelectedNode { it.copy(valueBinding = value) }
        }
        drawProgressBarBindingControls(node)
        editableFloat(
            node,
            "min",
            node.min,
            allowNull = false,
            tooltip = "Lower numeric bound for ProgressBar values.",
        ) { value ->
            operations.updateSelectedNode { it.copy(min = value ?: it.min) }
        }
        editableFloat(
            node,
            "max",
            node.max,
            allowNull = false,
            tooltip = "Upper numeric bound for ProgressBar values.",
        ) { value ->
            operations.updateSelectedNode { it.copy(max = value ?: it.max) }
        }
        editableFloat(
            node,
            "step",
            node.step,
            allowNull = false,
            tooltip = "ProgressBar increment size used by the underlying widget.",
        ) { value ->
            operations.updateSelectedNode { it.copy(step = value ?: it.step) }
        }
    }

    private fun drawAlignEditor(node: UiSceneNode) {
        editableOptionalEnum(
            node,
            "align",
            node.align,
            UiSceneAlign.entries.toList(),
            tooltip = "Positions this node within its parent container or cell.",
        ) { align ->
            operations.updateSelectedNode { it.copy(align = align) }
        }
    }

    private fun drawTableLayoutEditor(node: UiSceneNode) {
        editableEnum(node, "tableOrientation", node.tableOrientation, UiSceneTableOrientation.entries.toList()) { orientation ->
            operations.updateSelectedNode { it.copy(tableOrientation = orientation) }
        }
        drawPaddingEditor(node)
        editableFloat(
            node,
            "spacing",
            node.spacing,
            allowNull = false,
            tooltip = "Gap between children in Table layout.",
        ) { value ->
            operations.updateSelectedNode { it.copy(spacing = value ?: it.spacing) }
        }
    }

    private fun drawContainerLayoutEditor(node: UiSceneNode) {
        drawAlignEditor(node)
        drawPaddingEditor(node)
    }

    private fun drawPaddingEditor(node: UiSceneNode) {
        editableFloat(
            node,
            "padding.left",
            node.padding.left,
            allowNull = false,
            tooltip = "Inner left inset before this container or table lays out its children.",
        ) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(left = value ?: it.padding.left)) }
        }
        editableFloat(
            node,
            "padding.top",
            node.padding.top,
            allowNull = false,
            tooltip = "Inner top inset before this container or table lays out its children.",
        ) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(top = value ?: it.padding.top)) }
        }
        editableFloat(
            node,
            "padding.right",
            node.padding.right,
            allowNull = false,
            tooltip = "Inner right inset before this container or table lays out its children.",
        ) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(right = value ?: it.padding.right)) }
        }
        editableFloat(
            node,
            "padding.bottom",
            node.padding.bottom,
            allowNull = false,
            tooltip = "Inner bottom inset before this container or table lays out its children.",
        ) { value ->
            operations.updateSelectedNode { it.copy(padding = it.padding.copy(bottom = value ?: it.padding.bottom)) }
        }
    }

    private fun drawCommonSizeFields(node: UiSceneNode) {
        editableFloat(
            node,
            "width",
            node.width,
            allowNull = true,
            tooltip = "Preferred widget width. Blank lets layout choose the width.",
        ) { value ->
            operations.updateSelectedNode { it.copy(width = value) }
        }
        editableFloat(
            node,
            "height",
            node.height,
            allowNull = true,
            tooltip = "Preferred widget height. Blank lets layout choose the height.",
        ) { value ->
            operations.updateSelectedNode { it.copy(height = value) }
        }
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
        tooltip: String? = null,
        onChanged: (String?) -> Unit,
    ) {
        val buffer = bufferFor(node.id, fieldName, current.orEmpty())
        if (ImGui.inputText("$fieldName##ui_composer_inspector_${node.id}_$fieldName", buffer)) {
            val rawValue = readBuffer(buffer)
            onChanged(rawValue.takeUnless { emptyAsNull && it.isBlank() })
        }
        drawFieldTooltip(tooltip)
    }

    private fun editableFloat(
        node: UiSceneNode,
        fieldName: String,
        current: Float?,
        allowNull: Boolean,
        tooltip: String? = null,
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
        drawFieldTooltip(tooltip)
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
        tooltip: String? = null,
        onChanged: (T) -> Unit,
    ) {
        val opened = ImGui.beginCombo("$fieldName##ui_composer_inspector_${node.id}_$fieldName", current.name)
        drawFieldTooltip(tooltip)
        if (!opened) return
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
        tooltip: String? = null,
        onChanged: (T?) -> Unit,
    ) {
        val preview = current?.name ?: "<none>"
        val opened = ImGui.beginCombo("$fieldName##ui_composer_inspector_${node.id}_$fieldName", preview)
        drawFieldTooltip(tooltip)
        if (!opened) return
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

    private fun drawFieldTooltip(tooltip: String?) {
        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }
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
        } else {
            property("class", actorInfo.actorClass)
            property("x", "%.2f".format(actorInfo.x))
            property("y", "%.2f".format(actorInfo.y))
            property("width", "%.2f".format(actorInfo.width))
            property("height", "%.2f".format(actorInfo.height))
            property("visible", actorInfo.visible.toString())
        }
        ImGui.separator()
        drawPreviewActorSection(node)
    }

    private fun drawPreviewActorSection(node: UiSceneNode) {
        when (node.type) {
            UiSceneNodeType.Image -> drawImagePreview(node)
            UiSceneNodeType.Container,
            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
                -> drawContainerPreview(node)
            UiSceneNodeType.Label,
            UiSceneNodeType.TextButton,
            UiSceneNodeType.ProgressBar,
            UiSceneNodeType.Space,
                -> ImGui.textUnformatted("Preview rendering skipped for ${node.type}.")
        }
    }

    private fun drawImagePreview(node: UiSceneNode) {
        val rawTexture = node.texture?.trim().orEmpty()
        val resolvedPath = resolveTexturePath(node.texture)
        when {
            rawTexture.isBlank() -> {
                ImGui.textUnformatted("No texture")
                return
            }
            resolvedPath.isNullOrBlank() && isBindingPlaceholder(rawTexture) -> {
                ImGui.textUnformatted("Texture binding has no preview value")
                return
            }
            resolvedPath.isNullOrBlank() -> {
                ImGui.textUnformatted("No texture")
                return
            }
        }

        property("Texture", resolvedPath)
        property("Texture status", if (textureExists(resolvedPath)) "found" else "missing from Asset Registry")
        val handle = assets.texturePreviewHandle(resolvedPath)
        if (handle == null) {
            val reason = texturePreviewUnavailableReason(resolvedPath)
            ImGui.textWrapped("Texture preview unavailable: $reason")
            logPreviewDiagnosticOnce(
                key = "texture:$resolvedPath:$reason",
                message = "UiComposer actor texture preview unavailable path='$resolvedPath': $reason",
            )
            return
        }

        drawPreviewHandle(
            handle = handle,
            failureKey = "texture:$resolvedPath:draw-failed:${handle.id}",
            failureMessage = { reason -> "UiComposer actor texture preview draw failed path='$resolvedPath': $reason" },
        )
        property("Image size", previewHandleSize(handle))
    }

    private fun drawContainerPreview(node: UiSceneNode) {
        val background = node.background?.takeIf(String::isNotBlank)
        property("Background", background ?: "<none>")
        drawBackgroundStatus(background)
        background?.let(::drawBackgroundPreview)
        property("Children", node.children.size.toString())
        if (node.type == UiSceneNodeType.Table) {
            property("Table orientation", node.tableOrientation.name)
            property("Spacing", node.spacing.toString())
        }
        property("Padding", paddingSummary(node))
    }

    private fun drawBackgroundPreview(drawableName: String) {
        val handle = state.skinMetadata
            ?.takeUnless { it.loadError != null }
            ?.drawablePreviewHandles
            ?.get(drawableName)
        if (handle == null) {
            val reason = "Skin drawable '$drawableName' has no atlas region preview handle."
            ImGui.textWrapped("Background preview unavailable: $reason")
            logPreviewDiagnosticOnce(
                key = "background:$drawableName:no-handle",
                message = "UiComposer background preview unavailable drawable='$drawableName': $reason",
            )
            return
        }

        drawPreviewHandle(
            handle = handle,
            failureKey = "background:$drawableName:draw-failed:${handle.id}",
            failureMessage = { reason -> "UiComposer background preview draw failed drawable='$drawableName': $reason" },
        )
        property("Background size", previewHandleSize(handle))
    }

    private fun drawPreviewHandle(
        handle: TexturePreviewHandle,
        failureKey: String,
        failureMessage: (String) -> String,
    ) {
        if (!ui.drawTexturePreview(handle, ActorPreviewBoxSize, ActorPreviewBoxSize)) {
            val reason = "UI backend rejected texture handle id=${handle.id} size=${handle.width}x${handle.height}."
            ImGui.textWrapped("Preview unavailable: $reason")
            logPreviewDiagnosticOnce(failureKey, failureMessage(reason))
        }
    }

    private fun drawBackgroundStatus(background: String?) {
        if (background == null) return
        when (val exists = drawableExists(background)) {
            true -> property("Background status", "found")
            false -> property("Background status", "missing from Skin")
            null -> ImGui.textUnformatted("Style metadata unavailable.")
        }
    }

    private fun resolveTexturePath(texture: String?): String? {
        val raw = texture?.trim().orEmpty()
        if (raw.isBlank()) return null
        val match = BindingPlaceholderRegex.matchEntire(raw)
        return if (match != null) {
            val key = match.groupValues[1].trim()
            state.previewPayload[key]?.takeIf(String::isNotBlank)
        } else {
            raw
        }
    }

    private fun isBindingPlaceholder(value: String): Boolean =
        BindingPlaceholderRegex.matches(value)

    private fun textureExists(path: String): Boolean =
        state.textureOptions.any { option -> option.path == path }

    private fun texturePreviewUnavailableReason(path: String): String {
        val registered = textureExists(path)
        val loadState = runCatching { assets.isLoaded(AssetRef.texture(path)) }
        return when {
            !registered -> "path is missing from Asset Registry."
            loadState.isFailure -> "AssetService load state check failed: ${loadState.exceptionOrNull()?.message ?: "unknown error"}."
            loadState.getOrDefault(false) -> "AssetService reports the texture is loaded, but the backend returned no preview handle."
            else -> "path is registered, but the texture is not loaded by AssetService yet."
        }
    }

    private fun drawableExists(drawableName: String): Boolean? {
        val metadata = state.skinMetadata?.takeUnless { it.loadError != null } ?: return null
        return metadata.drawables.contains(drawableName)
    }

    private fun logPreviewDiagnosticOnce(
        key: String,
        message: String,
    ) {
        if (loggedPreviewDiagnostics.add(key)) {
            logger.warn(UiComposerInspectorTag) { message }
        }
    }

    private fun previewHandleSize(handle: TexturePreviewHandle): String =
        "${handle.width} x ${handle.height}"

    private fun paddingSummary(node: UiSceneNode): String =
        "L ${node.padding.left}, T ${node.padding.top}, R ${node.padding.right}, B ${node.padding.bottom}"

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

    private fun drawTextureEditor(node: UiSceneNode) {
        ImGui.separator()
        ImGui.textUnformatted("Texture")
        editableString(node, "texture (manual)", node.texture, emptyAsNull = true) { value ->
            operations.updateSelectedNode { it.copy(texture = value) }
        }
        drawTextureBindingControls(node)
        drawSelectedTextureInfo(node)
        drawTextureWarning(node)
        drawTexturePicker(node)
        with(dsl) {
            button("Refresh Textures##ui_composer_texture_refresh") {
                state.textureOptionsReloadRequested = true
                state.statusMessage = "Texture refresh requested."
            }
        }
        ImGui.textWrapped(
            "Texture picker writes the path string only. No asset-id references, atlas region picker, " +
                "thumbnail browser, texture import/copy, Asset Browser drag/drop, or runtime behavior change.",
        )
    }

    private fun drawPlaceholderBindingControls(
        node: UiSceneNode,
        fieldName: String,
        comboLabel: String,
        buttonLabel: String,
        current: String,
        separatorAfterButton: Boolean = false,
        onInsert: (String) -> Unit,
    ) {
        val selectedKey = drawBindingCombo(node, fieldName, comboLabel)
        ImGui.beginDisabled(selectedKey == null)
        with(dsl) {
            button("$buttonLabel##ui_composer_binding_${node.id}_$fieldName") {
                selectedKey?.let { key -> onInsert(insertPlaceholder(current, key)) }
            }
        }
        ImGui.endDisabled()
        if (separatorAfterButton) {
            ImGui.separator()
        }
    }

    private fun drawTextureBindingControls(node: UiSceneNode) {
        val selectedKey = drawBindingCombo(node, "texture", "Texture Binding")
        ImGui.beginDisabled(selectedKey == null)
        with(dsl) {
            button("Use as texture binding##ui_composer_binding_${node.id}_texture") {
                selectedKey?.let { key ->
                    val next = textureBindingPlaceholder(key)
                    writeBuffer(bufferFor(node.id, "texture (manual)", node.texture.orEmpty()), next)
                    operations.updateSelectedNode { it.copy(texture = next) }
                }
            }
        }
        ImGui.endDisabled()
        ImGui.separator()
    }

    private fun drawProgressBarBindingControls(node: UiSceneNode) {
        ImGui.separator()
        val keys = bindingKeys()
        val selectionKey = bindingSelectionKey(node, "valueBinding")
        syncSelectedBindingKey(selectionKey, keys)
        val current = node.valueBinding?.takeIf(String::isNotBlank)
        val preview = current ?: "<none>"
        if (ImGui.beginCombo("Binding##ui_composer_value_binding_${node.id}", preview)) {
            if (ImGui.selectable("<none>##ui_composer_value_binding_${node.id}_none", current == null)) {
                writeBuffer(bufferFor(node.id, "valueBinding", node.valueBinding.orEmpty()), "")
                operations.updateSelectedNode { it.copy(valueBinding = null) }
            }
            keys.forEach { key ->
                if (ImGui.selectable("$key##ui_composer_value_binding_${node.id}_$key", key == current)) {
                    state.selectedBindingKeysByField[selectionKey] = key
                    writeBuffer(bufferFor(node.id, "valueBinding", node.valueBinding.orEmpty()), key)
                    operations.updateSelectedNode { it.copy(valueBinding = key) }
                }
            }
            if (keys.isEmpty()) {
                ImGui.textWrapped("No bindings are defined for this UI scene.")
            }
            ImGui.endCombo()
        }
    }

    private fun drawBindingCombo(
        node: UiSceneNode,
        fieldName: String,
        label: String,
    ): String? {
        val keys = bindingKeys()
        val selectionKey = bindingSelectionKey(node, fieldName)
        syncSelectedBindingKey(selectionKey, keys)
        val selectedKey = state.selectedBindingKeysByField[selectionKey]
        val preview = selectedKey ?: "<none>"
        if (ImGui.beginCombo("$label##ui_composer_binding_${node.id}_$fieldName", preview)) {
            keys.forEach { key ->
                if (ImGui.selectable("$key##ui_composer_binding_${node.id}_${fieldName}_$key", key == selectedKey)) {
                    state.selectedBindingKeysByField[selectionKey] = key
                }
            }
            if (keys.isEmpty()) {
                ImGui.textWrapped("No bindings are defined for this UI scene.")
            }
            ImGui.endCombo()
        }
        return state.selectedBindingKeysByField[selectionKey]
    }

    private fun bindingSelectionKey(
        node: UiSceneNode,
        fieldName: String,
    ): String =
        "${node.id}:$fieldName"

    private fun bindingKeys(): List<String> =
        state.document?.bindings
            ?.map { binding -> binding.key }
            ?.filter(String::isNotBlank)
            ?.sorted()
            ?: emptyList()

    private fun syncSelectedBindingKey(
        selectionKey: String,
        keys: List<String>,
    ) {
        val current = state.selectedBindingKeysByField[selectionKey]
        if (current != null && current in keys) return
        val fallback = keys.firstOrNull()
        if (fallback == null) {
            state.selectedBindingKeysByField.remove(selectionKey)
        } else {
            state.selectedBindingKeysByField[selectionKey] = fallback
        }
    }

    private fun drawSelectedTextureInfo(node: UiSceneNode) {
        val selected = state.textureOptions.firstOrNull { option -> option.path == node.texture }
        property("texture display", selected?.displayName)
        property("texture path", node.texture)
    }

    private fun drawTextureWarning(node: UiSceneNode) {
        val textureIssue = (state.validationIssues + state.textureValidationIssues)
            .firstOrNull { issue -> issue.nodeId == node.id && issue.message.contains("texture", ignoreCase = true) }
        textureIssue?.let { issue ->
            ImGui.textWrapped(formatValidationIssue(issue))
        }
    }

    private fun drawTexturePicker(node: UiSceneNode) {
        val options = state.textureOptions
        val selected = state.textureOptions.firstOrNull { option -> option.path == node.texture }
        val preview = when {
            node.texture == null -> "<none>"
            selected != null -> textureOptionLabel(selected)
            else -> "${node.texture} (not in Asset Registry)"
        }
        if (!ImGui.beginCombo("Texture Asset##ui_composer_texture_picker_${node.id}", preview)) return

        if (ImGui.selectable("<none>##ui_composer_texture_picker_${node.id}_none", node.texture == null)) {
            writeBuffer(bufferFor(node.id, "texture (manual)", node.texture.orEmpty()), "")
            operations.updateSelectedNode { it.copy(texture = null) }
        }
        options.forEach { option ->
            val optionLabel = textureOptionLabel(option)
            if (ImGui.selectable("$optionLabel##ui_composer_texture_picker_${node.id}_${option.path}", option.path == node.texture)) {
                writeBuffer(bufferFor(node.id, "texture (manual)", node.texture.orEmpty()), option.path)
                operations.updateSelectedNode { it.copy(texture = option.path) }
            }
        }
        if (options.isEmpty()) {
            ImGui.textWrapped("No texture assets are indexed.")
        }
        ImGui.endCombo()
    }

    private fun property(
        name: String,
        value: String?,
    ) {
        ImGui.textUnformatted("$name: ${value ?: "<none>"}")
    }
}

/**
 * Panel for `.krui` binding definitions and default preview data.
 *
 * This panel belongs to editor preview UX and edits document-owned binding
 * defaults. Edited values rebuild the backend preview and are saved with `.krui`,
 * but are not runtime Woolboy state and are not used for Skin editing, Asset
 * Browser drag/drop, asset-id references, drag/drop canvas editing, node editing,
 * or full Scene2D actor serialization.
 */
class UiComposerSceneBindingsPanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val valueBuffers = linkedMapOf<String, ByteArray>()

    /** Draws editable scene binding definitions and their editor default preview values. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.SceneBindings)
        val expanded = beginImGuiPanel(UiComposerPanelIds.SceneBindings, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.SceneBindings, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textWrapped(
            "Scene binding definitions and editor default preview values saved with this .krui document. " +
                "Runtime systems must still provide actual payload values explicitly.",
        )
        ImGui.separator()
        syncBuffers(force = false)
        val bindings = state.document?.bindings.orEmpty()
        if (bindings.isEmpty()) {
            ImGui.textWrapped("No bindings are defined for this UI scene.")
        }
        bindings.forEach { binding ->
            drawBindingDefaultValue(binding)
        }
        ImGui.separator()
        drawBindingIssues()
        ImGui.end()
    }

    private fun drawBindingDefaultValue(binding: UiSceneBindingDefinition) {
        ImGui.textUnformatted("${binding.key} [${binding.type.name}]")
        when (binding.type) {
            UiSceneBindingType.Text -> drawTextBindingDefault(binding)
            UiSceneBindingType.Number -> drawNumberBindingDefault(binding)
            UiSceneBindingType.Texture -> drawTextureBindingDefault(binding)
            UiSceneBindingType.Action -> drawActionBindingDefault(binding)
        }
    }

    private fun drawTextBindingDefault(binding: UiSceneBindingDefinition) {
        drawDefaultValueTextInput(binding, label = "Default text")
    }

    private fun drawNumberBindingDefault(binding: UiSceneBindingDefinition) {
        drawDefaultValueTextInput(binding, label = "Default number")
    }

    private fun drawTextureBindingDefault(binding: UiSceneBindingDefinition) {
        drawDefaultValueTextInput(binding, label = "Default texture")

        if (state.textureOptions.isEmpty()) {
            ImGui.textWrapped("No texture assets indexed. Use Refresh Textures in the Image texture picker if needed.")
            return
        }

        val selected = state.textureOptions.firstOrNull { option -> option.path == binding.defaultValue }
        val preview = when {
            binding.defaultValue.isBlank() -> "<none>"
            selected != null -> textureOptionLabel(selected)
            else -> "${binding.defaultValue} (not in Asset Registry)"
        }
        if (!ImGui.beginCombo("Texture asset##binding_${payloadInputId(binding.key)}", preview)) return

        state.textureOptions.forEach { option ->
            val optionLabel = textureOptionLabel(option)
            if (ImGui.selectable("$optionLabel##binding_${payloadInputId(binding.key)}_${option.path}", option.path == binding.defaultValue)) {
                writeBuffer(valueBuffers.getValue(binding.key), option.path)
                operations.updateBindingDefaultValue(binding.key, option.path)
            }
        }
        ImGui.endCombo()
    }

    private fun drawActionBindingDefault(binding: UiSceneBindingDefinition) {
        drawDefaultValueTextInput(binding, label = "Default action")
    }

    private fun drawDefaultValueTextInput(
        binding: UiSceneBindingDefinition,
        label: String,
    ) {
        val buffer = valueBuffers.getValue(binding.key)
        if (ImGui.inputText("$label##ui_composer_payload_${payloadInputId(binding.key)}", buffer)) {
            operations.updateBindingDefaultValue(binding.key, readBuffer(buffer))
        }
    }

    private fun drawBindingIssues() {
        ImGui.textUnformatted("Binding issues: ${state.bindingValidationIssues.size}")
        state.bindingValidationIssues.forEachIndexed { index, issue ->
            ImGui.textWrapped("${index + 1}. ${formatValidationIssue(issue)}")
        }

        val missingKeys = state.missingBindingKeys
        if (missingKeys.isEmpty()) {
            ImGui.textUnformatted("No missing binding definitions.")
        } else {
            ImGui.textUnformatted("Missing binding definitions: ${missingKeys.size}")
            ImGui.textWrapped(
                "Missing binding definitions are added to this .krui document with an editor default preview value.",
            )
            missingKeys.forEach { missing ->
                ImGui.textWrapped(
                    "${missing.key} used by nodes=${missing.nodeIds.joinToString()}, " +
                        "fields=${missing.fields.joinToString()}",
                )
                ImGui.sameLine()
                with(dsl) {
                    button("Add##ui_composer_add_missing_binding_${payloadInputId(missing.key)}") {
                        operations.upsertBindingDefinition(
                            UiSceneBindingDefinition(
                                key = missing.key,
                                type = defaultBindingTypeFor(missing),
                                defaultValue = defaultPreviewPayloadValueFor(missing.key),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun syncBuffers(force: Boolean) {
        state.document?.bindings.orEmpty().forEach { binding ->
            val key = binding.key
            val value = binding.defaultValue
            val buffer = valueBuffers.getOrPut(key) { ByteArray(TextInputBufferSize) }
            if (force || readBuffer(buffer) != value) {
                writeBuffer(buffer, value)
            }
        }
    }
}

/**
 * Diagnostics panel for `.krui` parse and validation results.
 *
 * This panel belongs to editor UI diagnostics. It reports parser/build failures
 * and shared validator warnings while intentionally omitting automatic repairs,
 * schema changes, JSON editing, saving, Skin editing, Asset Browser drag/drop,
 * texture import/copy, texture thumbnails, atlas region picking, asset-id
 * references, drag/drop canvas editing, resize handles, multi-select, snapping,
 * transform gizmos, canvas structure editing, and full Actor serialization.
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
        ImGui.textUnformatted("Resolution preset: ${state.previewResolutionPreset.name}")
        ImGui.textUnformatted("Resolution: ${state.previewLogicalWidth} x ${state.previewLogicalHeight}")
        ImGui.textUnformatted("Preview camera offset: x=${formatFloat(state.previewCameraOffsetX)}, y=${formatFloat(state.previewCameraOffsetY)}")
        ImGui.textUnformatted("Preview zoom: ${formatPercent(state.previewZoom)}")
        ImGui.textUnformatted("Preview panning: ${if (state.canvasPanning) "yes" else "no"}")
        ImGui.textUnformatted("Canvas panel rect: ${formatRect(state.canvasPanelRect)}")
        ImGui.textUnformatted(
            "Canvas: ${formatFloat(state.canvasPreviewRect.width)} x ${formatFloat(state.canvasPreviewRect.height)}",
        )
        ImGui.textUnformatted(
            "Canvas local mouse: ${
                formatNullablePoint(
                    state.canvasLocalMouseX,
                    state.canvasLocalMouseY,
                )
            }",
        )
        ImGui.textUnformatted(
            "Canvas world mouse: ${
                formatNullablePoint(
                    state.canvasWorldMouseX,
                    state.canvasWorldMouseY,
                )
            }",
        )
        ImGui.separator()
        ImGui.checkbox("Bounds##ui_composer_diagnostics_show_bounds", state::showBounds)
        ImGui.sameLine()
        ImGui.checkbox("Selected##ui_composer_diagnostics_highlight_selected", state::highlightSelected)
        ImGui.sameLine()
        ImGui.checkbox("Canvas##ui_composer_diagnostics_canvas_selection", state::canvasSelectionEnabled)
        ImGui.sameLine()
        ImGui.checkbox("Hovered##ui_composer_diagnostics_highlight_hovered", state::highlightHovered)
        ImGui.separator()
        drawGuideDiagnostics()
        ImGui.separator()

        val issues = state.validationIssues +
            state.styleValidationIssues +
            state.textureValidationIssues
        ImGui.textUnformatted("Validation issues: ${issues.size}")
        if (issues.isEmpty() && parseError == null) {
            ImGui.textUnformatted("No validation issues.")
        } else {
            issues.forEachIndexed { index, issue ->
                ImGui.textWrapped("${index + 1}. ${formatValidationIssue(issue)}")
            }
        }
        ImGui.end()
    }

    private fun drawGuideDiagnostics() {
        val snapshot = state.guideSnapshot
        ImGui.textUnformatted("Guide snapshot")
        property("selected", snapshot.selected?.nodeId)
        property(
            "parent chain",
            snapshot.parentChain.joinToString(" > ") { it.nodeId ?: it.label }.ifBlank { "<none>" },
        )
        property("selected guide available", if (snapshot.selected != null) "yes" else "no")
        property("hovered guide available", if (snapshot.hovered != null) "yes" else "no")
    }

    private fun property(
        name: String,
        value: String?,
    ) {
        ImGui.textUnformatted("$name: ${value ?: "<none>"}")
    }
}

private const val TextInputBufferSize = 256
private const val MaxCustomPreviewSize = 8192
private const val ZoomButtonStep = 1.25f
private const val ActorPreviewBoxSize = 100f
private const val UiComposerInspectorTag = "UiComposerInspectorPanel"

private fun formatFloat(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')

private fun formatPercent(value: Float): String =
    "${(clampPreviewZoom(value) * 100f).toInt()}%"

private fun formatRect(rect: UiComposerCanvasRect): String =
    "x=${formatFloat(rect.x)}, y=${formatFloat(rect.y)}, w=${formatFloat(rect.width)}, h=${formatFloat(rect.height)}"

private fun formatNullablePoint(x: Float?, y: Float?): String =
    if (x == null || y == null) {
        "<none>"
    } else {
        "x=${formatFloat(x)}, y=${formatFloat(y)}"
    }

private fun UiComposerPreviewResolutionPreset.displayName(): String =
    when (this) {
        UiComposerPreviewResolutionPreset.FitPanel -> "Fit Panel"
        UiComposerPreviewResolutionPreset.HD_1280x720 -> "1280 x 720"
        UiComposerPreviewResolutionPreset.FullHD_1920x1080 -> "1920 x 1080"
        UiComposerPreviewResolutionPreset.QHD_2560x1440 -> "2560 x 1440"
        UiComposerPreviewResolutionPreset.Ultrawide_3440x1440 -> "3440 x 1440"
        UiComposerPreviewResolutionPreset.XGA_1024x768 -> "1024 x 768"
        UiComposerPreviewResolutionPreset.Custom -> "Custom"
    }

private fun setPreviewZoom(
    state: UiComposerState,
    zoom: Float,
) {
    state.previewZoom = clampPreviewZoom(zoom)
    state.statusMessage = "Preview zoom set to ${formatPercent(state.previewZoom)}."
}

private fun resetPreviewCamera(state: UiComposerState) {
    state.previewCameraOffsetX = 0f
    state.previewCameraOffsetY = 0f
    state.previewZoom = 1f
    state.canvasPanning = false
    state.statusMessage = "Preview camera fit to viewport."
}

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

private fun textureOptionLabel(option: UiComposerTextureOption): String =
    "${option.displayName} - ${option.path}"

private fun payloadInputId(key: String): String =
    key.filter { char -> char.isLetterOrDigit() || char == '_' || char == '-' }.ifBlank { "key" }

private val BindingPlaceholderRegex = Regex("""\{([^{}]+)}""")

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
