package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.ResourceSearchWidth
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.parseResourceColor
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.writeBuffer
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.api.colorButton
import java.io.File
import glm_.vec2.Vec2 as ImVec2

private const val ResourceListMaxHeight = 300f

/**
 * ImGui shell for the Resources workspace.
 *
 * This panel owns resource filters and the indexed resource list only. The
 * selected resource preview is delegated to [SkinEditorResourcePreviewPanel].
 */
class SkinEditorResourceBrowserPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(256)
    private val previewPanel = SkinEditorResourcePreviewPanel(state, operations, ui)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.ResourceBrowser)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.ResourceBrowser, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.ResourceBrowser, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        drawFilters()
        drawResourceList()
        previewPanel.draw(selectedResource())
        ImGui.end()
    }

    private fun drawResourceList() {
        val resourceIndex = state.loadResult.resourceIndex
        ImGui.beginChild("skin_editor_resource_list", ImVec2(0f, ResourceListMaxHeight), true)
        if (resourceIndex.resources.isEmpty()) {
            ImGui.textUnformatted("No resources indexed.")
        } else {
            val filteredResources = resourceIndex.resources.filter(::matchesFilters)
            ImGui.textUnformatted("Showing ${filteredResources.size} of ${resourceIndex.resources.size}")
            val categories = state.resourceBrowser.selectedCategory?.let(::listOf) ?: SkinResourceCategory.entries
            categories.forEach { category ->
                val resources = filteredResources.filter { resource -> resource.category == category }
                if (resources.isNotEmpty() && ImGui.treeNode("${category.name} (${resources.size})##skin_editor_resource_category_$category")) {
                    resources.forEach(::drawResourceRow)
                    ImGui.treePop()
                }
            }
        }
        ImGui.endChild()
    }

    private fun drawResourceRow(resource: SkinResourceInfo) {
        val status = if (resource.resolved) "" else " missing"
        val source =
            resource.source
                ?.substringBefore('#')
                ?.let(::File)
                ?.name
                ?.takeIf(String::isNotBlank)
        val sourceLabel = source?.let { " source: $it" }.orEmpty()
        val label = "${resource.name} [${resource.type}]$status refs: ${resource.referencedBy.size}$sourceLabel"
        if (ImGui.selectable("$label##skin_editor_resource_${resource.category}_${resource.name}", state.selectedResourceKey == resource.key)) {
            operations.selectResource(resource)
        }
        if (resource.category == SkinResourceCategory.Color) {
            resourceColor(resource)?.let { color ->
                ImGui.sameLine()
                colorButton(
                    "Color##skin_editor_resource_color_${resource.name}",
                    color[0],
                    color[1],
                    color[2],
                    color[3],
                )
            }
        }
    }

    private fun drawFilters() {
        if (readBuffer(queryBuffer) != state.resourceBrowser.query) {
            writeBuffer(queryBuffer, state.resourceBrowser.query)
        }
        ImGui.textUnformatted("Search:")
        ImGui.sameLine()
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.inputText("##skin_editor_resource_search", queryBuffer)) {
            state.resourceBrowser.query = readBuffer(queryBuffer)
        }

        val categoryLabel = state.resourceBrowser.selectedCategory?.name ?: "All categories"
        if (ImGui.beginCombo("Category##skin_editor_resource_category_filter", categoryLabel)) {
            if (ImGui.selectable("All categories##skin_editor_resource_category_all", state.resourceBrowser.selectedCategory == null)) {
                state.resourceBrowser.selectedCategory = null
            }
            SkinResourceCategory.entries.forEach { category ->
                if (ImGui.selectable("${category.name}##skin_editor_resource_category_filter_$category", state.resourceBrowser.selectedCategory == category)) {
                    state.resourceBrowser.selectedCategory = category
                }
            }
            ImGui.endCombo()
        }

        val unresolved = booleanArrayOf(state.resourceBrowser.showOnlyUnresolved)
        if (ImGui.checkbox("Only unresolved##skin_editor_resource_unresolved", unresolved)) {
            state.resourceBrowser.showOnlyUnresolved = unresolved[0]
        }
        val referenced = booleanArrayOf(state.resourceBrowser.showOnlyReferenced)
        if (ImGui.checkbox("Only referenced##skin_editor_resource_referenced", referenced)) {
            state.resourceBrowser.showOnlyReferenced = referenced[0]
        }
        ImGui.separator()
    }

    private fun matchesFilters(resource: SkinResourceInfo): Boolean {
        val browser = state.resourceBrowser
        if (browser.selectedCategory != null && resource.category != browser.selectedCategory) return false
        if (browser.showOnlyUnresolved && resource.resolved) return false
        if (browser.showOnlyReferenced && resource.referencedBy.isEmpty()) return false
        val query = browser.query.trim()
        if (query.isEmpty()) return true
        return sequenceOf(resource.name, resource.type, resource.source.orEmpty())
            .plus(resource.details.asSequence().flatMap { (name, value) -> sequenceOf(name, value) })
            .any { value -> value.contains(query, ignoreCase = true) }
    }

    private fun selectedResource(): SkinResourceInfo? =
        state.loadResult.resourceIndex.resources
            .firstOrNull { it.key == state.selectedResourceKey }

    private fun resourceColor(resource: SkinResourceInfo): FloatArray? {
        val values = state.editSession.resources[resource.key]?.values ?: resource.details
        return parseResourceColor(values)
    }
}
