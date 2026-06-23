package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.ResourceSearchWidth
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinProblem
import com.pashkd.krender.engine.tools.skin.SkinProblemCategory
import com.pashkd.krender.engine.tools.skin.SkinProblemSeverity
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.safeTextWrapped
import com.pashkd.krender.engine.tools.skin.shortSource
import com.pashkd.krender.engine.tools.skin.toAsciiSafeText
import com.pashkd.krender.engine.tools.skin.writeBuffer
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

class SkinEditorProblemsPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(256)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Problems)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Problems, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Problems, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawFilters()
        if (state.loadResult.problems.isEmpty()) {
            ImGui.textUnformatted("No problems reported.")
        } else {
            val filteredProblems =
                state.loadResult.problems
                    .withIndex()
                    .filter { indexedProblem -> matchesFilters(indexedProblem.value) }
            ImGui.textUnformatted("Showing ${filteredProblems.size} of ${state.loadResult.problems.size}")
            filteredProblems.forEach { (index, problem) ->
                val source =
                    problem.source
                        ?.shortSource()
                        ?.let { " [$it]" }
                        .orEmpty()
                val label = "[${problem.severity}][${problem.category}] ${problem.message}$source"
                if (ImGui.selectable("${label.toAsciiSafeText()}##skin_editor_problem_$index", state.selectedProblemIndex == index)) {
                    operations.selectProblem(index, problem)
                }
                if (state.selectedProblemIndex == index) {
                    safeTextWrapped(problem.message)
                }
            }
        }
        ImGui.end()
    }

    private fun drawFilters() {
        if (readBuffer(queryBuffer) != state.problemFilters.query) {
            writeBuffer(queryBuffer, state.problemFilters.query)
        }
        ImGui.textUnformatted("Filter:")
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.inputText("##skin_editor_problem_search", queryBuffer)) {
            state.problemFilters.query = readBuffer(queryBuffer)
        }

        val severityLabel = state.problemFilters.severity?.name ?: "All severities"
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.beginCombo("Severity##skin_editor_problem_severity", severityLabel)) {
            if (ImGui.selectable("All severities##skin_editor_problem_severity_all", state.problemFilters.severity == null)) {
                state.problemFilters.severity = null
            }
            SkinProblemSeverity.entries.forEach { severity ->
                if (ImGui.selectable("${severity.name}##skin_editor_problem_severity_$severity", state.problemFilters.severity == severity)) {
                    state.problemFilters.severity = severity
                }
            }
            ImGui.endCombo()
        }

        val categoryLabel = state.problemFilters.category?.name ?: "All categories"
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.beginCombo("Category##skin_editor_problem_category", categoryLabel)) {
            if (ImGui.selectable("All categories##skin_editor_problem_category_all", state.problemFilters.category == null)) {
                state.problemFilters.category = null
            }
            SkinProblemCategory.entries.forEach { category ->
                if (ImGui.selectable("${category.name}##skin_editor_problem_category_$category", state.problemFilters.category == category)) {
                    state.problemFilters.category = category
                }
            }
            ImGui.endCombo()
        }

        val showErrors = booleanArrayOf(state.problemFilters.showErrors)
        if (ImGui.checkbox("Error##skin_editor_problem_show_error", showErrors)) {
            state.problemFilters.showErrors = showErrors[0]
        }
        ImGui.sameLine()
        val showWarnings = booleanArrayOf(state.problemFilters.showWarnings)
        if (ImGui.checkbox("Warning##skin_editor_problem_show_warning", showWarnings)) {
            state.problemFilters.showWarnings = showWarnings[0]
        }
        ImGui.sameLine()
        val showInfo = booleanArrayOf(state.problemFilters.showInfo)
        if (ImGui.checkbox("Info##skin_editor_problem_show_info", showInfo)) {
            state.problemFilters.showInfo = showInfo[0]
        }
        ImGui.separator()
    }

    private fun matchesFilters(problem: SkinProblem): Boolean {
        val filters = state.problemFilters
        if (filters.severity != null && problem.severity != filters.severity) return false
        if (filters.category != null && problem.category != filters.category) return false
        if (problem.severity == SkinProblemSeverity.Error && !filters.showErrors) return false
        if (problem.severity == SkinProblemSeverity.Warning && !filters.showWarnings) return false
        if (problem.severity == SkinProblemSeverity.Info && !filters.showInfo) return false
        val query = filters.query.trim()
        if (query.isEmpty()) return true
        return sequenceOf(problem.message, problem.source.orEmpty(), problem.suggestedFix.orEmpty())
            .any { value -> value.contains(query, ignoreCase = true) }
    }
}
