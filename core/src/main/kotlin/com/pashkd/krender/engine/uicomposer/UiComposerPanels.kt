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
import java.nio.charset.StandardCharsets

/**
 * Toolbar panel for the Phase 4 read-only UiComposer preview.
 *
 * This panel belongs to editor preview UX. It exposes document reload, panel
 * layout persistence, bounds toggling, and selected-node highlighting only; it
 * intentionally does not save `.krui`, edit node properties, create/delete
 * nodes, reorder nodes, implement drag/drop canvas editing, edit Skins, add
 * Asset Browser picking, introduce asset-id references, edit JSON, or serialize
 * full Scene2D actors.
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
        with(dsl) {
            button("Reload##ui_composer_reload") {
                state.reloadRequested = true
            }
        }
        ImGui.sameLine()
        ImGui.checkbox("Show Bounds##ui_composer_show_bounds", state::showBounds)
        ImGui.sameLine()
        ImGui.checkbox("Highlight Selected##ui_composer_highlight_selected", state::highlightSelected)
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
        ImGui.separator()
        drawActorInfo(node)
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
            "MVP limitations: read-only preview; preview payload is not saved and is not runtime state; " +
                "no .krui save/editing; no Skin editing; no drag/drop; " +
                "no Asset Browser picker yet; no asset-id references; no full Scene2D actor serialization; " +
                "no embedded ImGui viewport yet; selected-node highlight is best-effort.",
        )
        ImGui.end()
    }
}

private const val TextInputBufferSize = 256

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
