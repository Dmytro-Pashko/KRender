package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorDiagnostic
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorDiagnosticSeverity
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorDiagnosticsPanel(
    private val state: TextureAtlasEditorState,
    @Suppress("unused") private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(BufferSize)
    private var synced = false

    override fun draw() {
        if (!synced) {
            writeBuffer(queryBuffer, state.diagnosticsFilter.query)
            synced = true
        }
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Diagnostics)
        val expanded = beginImGuiPanel(TextureAtlasEditorPanelIds.Diagnostics, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Diagnostics, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Search")
        ImGui.sameLine()
        ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##texture_atlas_editor_diag_query", queryBuffer)) {
            state.diagnosticsFilter.query = readBuffer(queryBuffer)
        }
        val info = booleanArrayOf(state.diagnosticsFilter.showInfo)
        ImGui.sameLine()
        if (ImGui.checkbox("Info##texture_atlas_editor_diag_info", info)) state.diagnosticsFilter.showInfo = info[0]
        val warnings = booleanArrayOf(state.diagnosticsFilter.showWarnings)
        ImGui.sameLine()
        if (ImGui.checkbox("Warnings##texture_atlas_editor_diag_warn", warnings)) state.diagnosticsFilter.showWarnings = warnings[0]
        val errors = booleanArrayOf(state.diagnosticsFilter.showErrors)
        ImGui.sameLine()
        if (ImGui.checkbox("Errors##texture_atlas_editor_diag_error", errors)) state.diagnosticsFilter.showErrors = errors[0]
        ImGui.separator()

        ImGui.beginChild("texture_atlas_editor_diagnostics_list", ImVec2(0f, 0f), true)
        visibleDiagnostics().forEach { diagnostic ->
            textLine("[${diagnostic.severity.name}] ${diagnostic.category.name}: ${diagnostic.message}")
            diagnostic.source?.let { source ->
                textLine("  $source")
            }
        }
        if (visibleDiagnostics().isEmpty()) {
            ImGui.textUnformatted("No diagnostics match the current filter.")
        }
        ImGui.endChild()
        ImGui.end()
    }

    private fun visibleDiagnostics(): List<TextureAtlasEditorDiagnostic> =
        state.diagnostics.filter { diagnostic ->
            severityVisible(diagnostic.severity) &&
                (
                    state.diagnosticsFilter.query.isBlank() ||
                        diagnostic.message.contains(state.diagnosticsFilter.query, ignoreCase = true) ||
                        diagnostic.source?.contains(state.diagnosticsFilter.query, ignoreCase = true) == true
                )
        }

    private fun severityVisible(severity: TextureAtlasEditorDiagnosticSeverity): Boolean =
        when (severity) {
            TextureAtlasEditorDiagnosticSeverity.Info -> state.diagnosticsFilter.showInfo
            TextureAtlasEditorDiagnosticSeverity.Warning -> state.diagnosticsFilter.showWarnings
            TextureAtlasEditorDiagnosticSeverity.Error -> state.diagnosticsFilter.showErrors
        }

    companion object {
        private const val BufferSize = 256
    }
}
