package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerDiagnostic
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerDiagnosticSeverity
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerOperations
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureManagerDiagnosticsPanel(
    private val state: TextureManagerState,
    @Suppress("unused") private val operations: TextureManagerOperations,
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
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Diagnostics)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Diagnostics, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Diagnostics, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Search")
        ImGui.sameLine()
        ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##texture_manager_diag_query", queryBuffer)) {
            state.diagnosticsFilter.query = readBuffer(queryBuffer)
        }
        val info = booleanArrayOf(state.diagnosticsFilter.showInfo)
        if (ImGui.checkbox("Info##texture_manager_diag_info", info)) state.diagnosticsFilter.showInfo = info[0]
        val warnings = booleanArrayOf(state.diagnosticsFilter.showWarnings)
        if (ImGui.checkbox("Warnings##texture_manager_diag_warn", warnings)) state.diagnosticsFilter.showWarnings = warnings[0]
        val errors = booleanArrayOf(state.diagnosticsFilter.showErrors)
        if (ImGui.checkbox("Errors##texture_manager_diag_error", errors)) state.diagnosticsFilter.showErrors = errors[0]
        ImGui.separator()

        ImGui.beginChild("texture_manager_diagnostics_list", ImVec2(0f, 0f), true)
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

    private fun visibleDiagnostics(): List<TextureManagerDiagnostic> =
        state.diagnostics.filter { diagnostic ->
            severityVisible(diagnostic.severity) &&
                (
                    state.diagnosticsFilter.query.isBlank() ||
                        diagnostic.message.contains(state.diagnosticsFilter.query, ignoreCase = true) ||
                        diagnostic.source?.contains(state.diagnosticsFilter.query, ignoreCase = true) == true
                )
        }

    private fun severityVisible(severity: TextureManagerDiagnosticSeverity): Boolean =
        when (severity) {
            TextureManagerDiagnosticSeverity.Info -> state.diagnosticsFilter.showInfo
            TextureManagerDiagnosticSeverity.Warning -> state.diagnosticsFilter.showWarnings
            TextureManagerDiagnosticSeverity.Error -> state.diagnosticsFilter.showErrors
        }

    companion object {
        private const val BufferSize = 256
    }
}
