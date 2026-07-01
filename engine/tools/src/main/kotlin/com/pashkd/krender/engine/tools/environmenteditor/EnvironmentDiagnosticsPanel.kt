package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.IssueSeverity
import com.pashkd.krender.engine.assets.environment.ValidationStatus
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui

/**
 * Panel displaying environment validation issues grouped by severity.
 */
class EnvironmentDiagnosticsPanel(
    private val state: EnvironmentEditorState,
    private val environmentService: EnvironmentService,
) : UiPanel {

    override fun draw() {
        if (!ImGui.begin("Diagnostics")) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        val report = state.validation
        if (report == null) {
            ImGui.text("No validation report available.")
            if (ImGui.button("Run Validation##env_diag_validate")) {
                state.validation = environmentService.validate(env)
            }
            ImGui.end()
            return
        }

        drawSummary(report.status, report.issues.size)
        ImGui.separator()

        if (ImGui.button("Refresh Validation##env_diag_refresh")) {
            state.validation = environmentService.validate(env)
        }
        ImGui.separator()

        val errors = report.issues.filter { it.severity == IssueSeverity.Error }
        val warnings = report.issues.filter { it.severity == IssueSeverity.Warning }
        val infos = report.issues.filter { it.severity == IssueSeverity.Info }

        if (errors.isNotEmpty()) {
            ImGui.text("Errors (${errors.size})")
            for (issue in errors) {
                ImGui.text("  [${issue.code}] ${issue.message}")
                issue.relatedPath?.let { ImGui.text("    Path: $it") }
            }
            ImGui.separator()
        }

        if (warnings.isNotEmpty()) {
            ImGui.text("Warnings (${warnings.size})")
            for (issue in warnings) {
                ImGui.text("  [${issue.code}] ${issue.message}")
                issue.relatedPath?.let { ImGui.text("    Path: $it") }
            }
            ImGui.separator()
        }

        if (infos.isNotEmpty()) {
            ImGui.text("Info (${infos.size})")
            for (issue in infos) {
                ImGui.text("  [${issue.code}] ${issue.message}")
                issue.relatedPath?.let { ImGui.text("    Path: $it") }
            }
        }

        if (report.issues.isEmpty()) {
            ImGui.text("No issues found.")
        }

        ImGui.end()
    }

    private fun drawSummary(
        status: ValidationStatus,
        issueCount: Int,
    ) {
        val statusLabel = when (status) {
            ValidationStatus.Valid -> "Valid"
            ValidationStatus.Warning -> "WARNING"
            ValidationStatus.Error -> "ERROR"
        }
        ImGui.text("Status: $statusLabel ($issueCount issue(s))")
    }
}
