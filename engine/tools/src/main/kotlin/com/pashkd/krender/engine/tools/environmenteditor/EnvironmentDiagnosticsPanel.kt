package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.IssueSeverity
import com.pashkd.krender.engine.assets.environment.ValidationStatus
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

/**
 * Panel displaying environment validation issues grouped by severity.
 */
class EnvironmentDiagnosticsPanel(
    private val state: EnvironmentEditorState,
    private val environmentService: EnvironmentService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Diagnostics)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Diagnostics, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Diagnostics, layout.title)
        if (expanded) {
            when (val content = diagnosticsContent()) {
                DiagnosticsContent.NoEnvironment -> ImGui.text("No environment loaded.")
                DiagnosticsContent.NoReport -> drawMissingReportState()
                is DiagnosticsContent.Report -> drawReport(content)
            }
        }
        ImGui.end()
    }

    private fun diagnosticsContent(): DiagnosticsContent {
        val env = state.environment
        val report = state.validation
        return when {
            env == null -> DiagnosticsContent.NoEnvironment
            report == null -> DiagnosticsContent.NoReport
            else -> DiagnosticsContent.Report(env, report)
        }
    }

    private fun drawMissingReportState() {
        ImGui.text("No validation report available.")
        if (ImGui.button("Run Validation##env_diag_validate")) {
            val env = state.environment ?: return
            state.validation = environmentService.validate(env)
        }
        tooltipOnHover("Runs Environment validation and refreshes diagnostics.")
    }

    private fun drawReport(content: DiagnosticsContent.Report) {
        drawSummary(content.report.status, content.report.issues.size)
        ImGui.separator()
        if (ImGui.button("Refresh Validation##env_diag_refresh")) {
            state.validation = environmentService.validate(content.environment)
        }
        tooltipOnHover("Re-runs validation for the current Environment.")
        ImGui.separator()

        drawIssueGroup("Errors", content.report.issues.filter { it.severity == IssueSeverity.Error })
        drawIssueGroup("Warnings", content.report.issues.filter { it.severity == IssueSeverity.Warning })
        drawIssueGroup("Info", content.report.issues.filter { it.severity == IssueSeverity.Info }, includeSeparator = false)

        if (content.report.issues.isEmpty()) {
            ImGui.text("No issues found.")
        }
    }

    private fun drawIssueGroup(
        label: String,
        issues: List<com.pashkd.krender.engine.assets.environment.EnvironmentIssue>,
        includeSeparator: Boolean = true,
    ) {
        if (issues.isEmpty()) return
        ImGui.text("$label (${issues.size})")
        issues.forEach { issue ->
            ImGui.text("  [${issue.code}] ${issue.message}")
            issue.relatedPath?.let { ImGui.text("    Path: $it") }
        }
        if (includeSeparator) {
            ImGui.separator()
        }
    }

    private fun drawSummary(
        status: ValidationStatus,
        issueCount: Int,
    ) {
        val statusLabel =
            when (status) {
                ValidationStatus.Valid -> "Valid"
                ValidationStatus.Warning -> "WARNING"
                ValidationStatus.Error -> "ERROR"
            }
        ImGui.text("Status: $statusLabel ($issueCount issue(s))")
    }

    private sealed interface DiagnosticsContent {
        data object NoEnvironment : DiagnosticsContent

        data object NoReport : DiagnosticsContent

        data class Report(
            val environment: com.pashkd.krender.engine.assets.environment.Environment,
            val report: com.pashkd.krender.engine.assets.environment.EnvironmentValidationReport,
        ) : DiagnosticsContent
    }
}
