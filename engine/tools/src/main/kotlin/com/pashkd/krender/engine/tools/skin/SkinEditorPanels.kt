package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.dsl
import java.nio.charset.StandardCharsets
import glm_.vec2.Vec2 as ImVec2

class SkinEditorToolbarPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val pathBuffer = ByteArray(512)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Toolbar)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Toolbar, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Toolbar, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        syncPathBuffer()
        ImGui.setNextItemWidth(560f)
        ImGui.inputText("Skin path##skin_editor_path", pathBuffer)
        ImGui.sameLine()
        with(dsl) {
            button("Open Path##skin_editor_open_path") {
                operations.openPath(readBuffer(pathBuffer))
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reload##skin_editor_reload") {
                operations.requestReload()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Save Panel Layout##skin_editor_save_layout") {
                operations.saveUiLayout()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Panel Layout##skin_editor_reset_layout") {
                operations.restoreUiLayout()
            }
        }
        ImGui.separator()
        ImGui.textUnformatted("Path: ${state.currentInputPath ?: "<none>"}")
        ImGui.textUnformatted("Status: ${state.statusMessage}")
        ImGui.textUnformatted("Loaded skin: ${if (state.loadResult.previewSkinAvailable) "yes" else "no"}")
        ImGui.textUnformatted("Styles: ${state.loadResult.styleIndex.styles.size}")
        ImGui.textUnformatted("Resources: ${state.loadResult.resourceIndex.resources.size}")
        ImGui.textUnformatted("Problems: ${state.loadResult.problems.size}")
        ImGui.end()
    }

    private fun syncPathBuffer() {
        val current = state.pendingPathInput.ifBlank { state.currentInputPath.orEmpty() }
        if (readBuffer(pathBuffer) != current) {
            writeBuffer(pathBuffer, current)
        }
    }
}

class SkinEditorStyleTreePanel(
    private val state: SkinEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.StyleTree)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.StyleTree, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.StyleTree, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (state.loadResult.styleIndex.styles.isEmpty()) {
            ImGui.textUnformatted("No styles indexed.")
        } else {
            state.loadResult.styleIndex.styles.groupBy { it.type }.forEach { (type, styles) ->
                if (ImGui.treeNode("$type##skin_editor_style_type_$type")) {
                    styles.forEach { style ->
                        if (ImGui.selectable("${style.name}##skin_editor_style_${style.type}_${style.name}", state.selectedStyleKey == style.key)) {
                            state.selectedStyleKey = style.key
                            state.selectedResourceKey = null
                            state.selectedProblemIndex = null
                            state.previewDirty = true
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        ImGui.end()
    }
}

class SkinEditorResourceBrowserPanel(
    private val state: SkinEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.ResourceBrowser)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.ResourceBrowser, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.ResourceBrowser, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val resourceIndex = state.loadResult.resourceIndex
        if (resourceIndex.resources.isEmpty()) {
            ImGui.textUnformatted("No resources indexed.")
        } else {
            SkinResourceCategory.entries.forEach { category ->
                val resources = resourceIndex.resourcesFor(category)
                if (resources.isNotEmpty() && ImGui.treeNode("${category.name} (${resources.size})##skin_editor_resource_category_$category")) {
                    resources.forEach { resource ->
                        val label = "${resource.name} [${resource.type}]"
                        if (ImGui.selectable("$label##skin_editor_resource_${category}_${resource.name}", state.selectedResourceKey == resource.key)) {
                            state.selectedResourceKey = resource.key
                            state.selectedStyleKey = null
                            state.selectedProblemIndex = null
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        ImGui.end()
    }
}

class SkinEditorProblemsPanel(
    private val state: SkinEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Problems)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Problems, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Problems, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (state.loadResult.problems.isEmpty()) {
            ImGui.textUnformatted("No problems reported.")
        } else {
            state.loadResult.problems.forEachIndexed { index, problem ->
                val source = problem.source?.let { " [$it]" }.orEmpty()
                val label = "[${problem.severity}][${problem.category}] ${problem.message}$source"
                if (ImGui.selectable("$label##skin_editor_problem_$index", state.selectedProblemIndex == index)) {
                    state.selectedProblemIndex = index
                    state.selectedStyleKey = null
                    state.selectedResourceKey = null
                }
            }
        }
        ImGui.end()
    }
}

class SkinEditorPreviewCanvasPanel(
    private val state: SkinEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.PreviewCanvas)
        ImGui.setNextWindowBgAlpha(0f)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.PreviewCanvas, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.PreviewCanvas, layout.title)
        if (!expanded) {
            state.canvasRect = SkinEditorCanvasRect()
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Scene2D preview")
        ImGui.sameLine()
        ImGui.textUnformatted("Actors: ${state.previewInfo.actorCount}")
        ImGui.separator()

        val min = ImGui.cursorScreenPos
        val available = ImGui.contentRegionAvail
        state.canvasRect =
            SkinEditorCanvasRect(
                x = min.x,
                y = min.y,
                width = available.x.coerceAtLeast(1f),
                height = available.y.coerceAtLeast(1f),
            )
        ImGui.invisibleButton("##skin_editor_preview_canvas", ImVec2(state.canvasRect.width, state.canvasRect.height))
        ImGui.end()
    }
}

class SkinEditorInspectorPanel(
    private val state: SkinEditorState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.Inspector)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val style = state.loadResult.styleIndex.styles.firstOrNull { it.key == state.selectedStyleKey }
        val resource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == state.selectedResourceKey }
        val problem = state.selectedProblemIndex?.let(state.loadResult.problems::getOrNull)

        when {
            style != null -> {
                ImGui.textUnformatted("Style: ${style.name}")
                ImGui.textUnformatted("Type: ${style.type}")
                ImGui.textUnformatted("Fields: ${style.rawFieldCount}")
                ImGui.separator()
                if (style.fields.isEmpty()) {
                    ImGui.textUnformatted("No fields indexed.")
                } else {
                    style.fields.forEach { field ->
                        ImGui.textWrapped("${field.name}: ${field.rawValue ?: "<object>"} [${field.valueType}]")
                        field.reference?.let { reference ->
                            val category = reference.category?.name ?: "Unknown"
                            val status = if (reference.resolved) "resolved" else "missing"
                            ImGui.textWrapped("  Reference: $category '${reference.name}' ($status)")
                        }
                    }
                }
                ImGui.separator()
                ImGui.textUnformatted("Referenced resources: ${style.resourceReferences.size}")
                style.resourceReferences.forEach { reference ->
                    val category = reference.category?.name ?: "Unknown"
                    val status = if (reference.resolved) "resolved" else "missing"
                    ImGui.textWrapped("$category '${reference.name}' ($status)")
                }
            }

            resource != null -> {
                ImGui.textUnformatted("Resource: ${resource.name}")
                ImGui.textUnformatted("Category: ${resource.category}")
                ImGui.textUnformatted("Type: ${resource.type}")
                ImGui.textWrapped("Source: ${resource.source ?: "<none>"}")
                ImGui.textUnformatted("Resolved: ${if (resource.resolved) "yes" else "no"}")
                ImGui.textUnformatted("Referenced by: ${resource.referencedBy.size}")
                resource.referencedBy.forEach { reference -> ImGui.textWrapped(reference) }
                if (resource.details.isNotEmpty()) {
                    ImGui.separator()
                    resource.details.toSortedMap().forEach { (name, value) ->
                        ImGui.textWrapped("$name: $value")
                    }
                }
            }

            problem != null -> {
                ImGui.textUnformatted("Severity: ${problem.severity}")
                ImGui.textUnformatted("Category: ${problem.category}")
                ImGui.textWrapped(problem.message)
                problem.source?.let { ImGui.textWrapped("Source: $it") }
                problem.suggestedFix?.let { ImGui.textWrapped("Suggested fix: $it") }
            }

            else -> {
                ImGui.textWrapped("Select a style, resource, or problem to inspect the current skin foundation.")
            }
        }
        ImGui.end()
    }
}

class SkinEditorPreviewControlsPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val previewLayouts: PreviewLayoutRegistry,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.PreviewControls)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.PreviewControls, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.PreviewControls, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textWrapped("Preview layouts stay predefined for now. This panel establishes the future layout/parameter workspace without introducing editing yet.")
        if (ImGui.beginCombo("Layout##skin_editor_preview_layout", previewLayouts.layoutOrDefault(state.previewLayoutId).displayName)) {
            previewLayouts.layouts.forEach { layoutOption ->
                if (ImGui.selectable("${layoutOption.displayName}##skin_editor_preview_layout_${layoutOption.id}", layoutOption.id == state.previewLayoutId)) {
                    operations.selectLayout(layoutOption.id)
                }
            }
            ImGui.endCombo()
        }
        ImGui.separator()
        ImGui.textUnformatted("Loaded skin: ${if (state.loadResult.previewSkinAvailable) "yes" else "no"}")
        ImGui.textUnformatted("Root actor: ${state.previewInfo.rootActorClass ?: "<none>"}")
        ImGui.textUnformatted("Selected style: ${state.selectedStyleKey?.let { "${it.type}.${it.name}" } ?: "<none>"}")
        ImGui.textUnformatted("Selected resource: ${state.selectedResourceKey?.let { "${it.category}.${it.name}" } ?: "<none>"}")
        ImGui.textUnformatted("Canvas: ${state.canvasRect.width.toInt()} x ${state.canvasRect.height.toInt()}")
        ImGui.end()
    }
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
