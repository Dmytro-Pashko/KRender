package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import imgui.ImGui
import imgui.dsl

/**
 * Toolbar panel for the Phase 4 read-only UiComposer preview.
 *
 * This panel belongs to editor UI. It exposes reload and bounds toggling only;
 * it intentionally does not implement saving, property editing, node creation,
 * node deletion, drag/drop canvas editing, Skin editing, Asset Browser picking,
 * asset-id references, JSON editing, or full Scene2D actor serialization.
 */
class UiComposerToolbarPanel(
    private val state: UiComposerState,
    private val operations: UiComposerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /** Draws the read-only toolbar controls. */
    override fun draw() {
        val layout = layoutConfig.panels.getValue(UiComposerPanelIds.Toolbar)
        val expanded = beginImGuiPanel(UiComposerPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(UiComposerPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("Persist UI##ui_composer_persist_ui") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset UI to Default##ui_composer_reset_ui") {
                operations.restoreUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reload##ui_composer_reload") {
                state.reloadRequested = true
            }
        }
        ImGui.sameLine()
        ImGui.checkbox("Show Bounds##ui_composer_show_bounds", state::showBounds)
        ImGui.separator()
        ImGui.textUnformatted("Path: ${state.uiScenePath}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Mode: read-only preview; no save/editing controls are available.")
        ImGui.end()
    }
}

/**
 * Recursive hierarchy panel for the Phase 4 read-only UiComposer preview.
 *
 * This panel belongs to editor UI. It selects existing `.krui` nodes by id for
 * inspection and best-effort preview highlighting, but intentionally omits
 * add/delete/reorder operations, drag/drop canvas editing, saving, Skin editing,
 * Asset Browser picking, asset-id references, and actor serialization.
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
 * Read-only inspector panel for the Phase 4 UiComposer preview.
 *
 * This panel belongs to editor UI. It reports document and selected-node fields
 * without inputs or mutating actions, and intentionally omits saving, editing,
 * node creation/deletion/reorder, drag/drop canvas editing, Skin editing, Asset
 * Browser picking, asset-id references, JSON editing, and full Scene2D actor
 * serialization.
 */
class UiComposerInspectorPanel(
    private val state: UiComposerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    /** Draws document details or selected-node details as read-only text. */
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
            drawNodeInfo(selectedNode)
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

    private fun drawNodeInfo(node: UiSceneNode) {
        ImGui.textUnformatted("Selected Node")
        ImGui.separator()
        property("id", node.id)
        property("type", node.type.name)
        property("visible", node.visible.toString())
        property("style", node.style)
        property("background", node.background)
        property("text", node.text)
        property("action", node.action)
        property("texture", node.texture)
        property("scaling", node.scaling.name)
        property("value", node.value?.toString())
        property("valueBinding", node.valueBinding)
        property("min", node.min.toString())
        property("max", node.max.toString())
        property("step", node.step.toString())
        property("width", node.width?.toString())
        property("height", node.height?.toString())
        property("align", node.align?.name)
        property("padding", "l=${node.padding.left}, t=${node.padding.top}, r=${node.padding.right}, b=${node.padding.bottom}")
        property("spacing", node.spacing.toString())
        property("child count", node.children.size.toString())
    }

    private fun property(
        name: String,
        value: String?,
    ) {
        ImGui.textUnformatted("$name: ${value ?: "<none>"}")
    }
}

/**
 * Diagnostics panel for `.krui` parse and validation results.
 *
 * This panel belongs to editor UI diagnostics. It reports parser/build failures
 * and shared validator warnings while intentionally omitting automatic repairs,
 * schema changes, JSON editing, saving, Skin editing, Asset Browser picking,
 * asset-id references, drag/drop canvas editing, and full Actor serialization.
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

        val issues = state.validationIssues
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
            "MVP limitations: read-only preview; no save/editing; no Skin editing; no drag/drop; " +
                "no Asset Browser picker yet; no asset-id references; no full Scene2D actor serialization; " +
                "selected-node highlight is best-effort.",
        )
        ImGui.end()
    }
}
