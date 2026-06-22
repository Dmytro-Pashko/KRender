package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.dsl
import java.io.File
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
    private val operations: SkinEditorOperations,
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
                            operations.selectStyle(style.key)
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
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(256)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.ResourceBrowser)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.ResourceBrowser, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.ResourceBrowser, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val resourceIndex = state.loadResult.resourceIndex
        drawFilters()
        if (resourceIndex.resources.isEmpty()) {
            ImGui.textUnformatted("No resources indexed.")
        } else {
            val filteredResources = resourceIndex.resources.filter(::matchesFilters)
            ImGui.textUnformatted("Showing ${filteredResources.size} of ${resourceIndex.resources.size}")
            val categories = state.resourceBrowser.selectedCategory?.let(::listOf) ?: SkinResourceCategory.entries
            categories.forEach { category ->
                val resources = filteredResources.filter { resource -> resource.category == category }
                if (resources.isNotEmpty() && ImGui.treeNode("${category.name} (${resources.size})##skin_editor_resource_category_$category")) {
                    resources.forEach { resource ->
                        val status = if (resource.resolved) "" else " missing"
                        val source = resource.source?.substringBefore('#')?.let(::File)?.name?.takeIf(String::isNotBlank)
                        val sourceLabel = source?.let { " source: $it" }.orEmpty()
                        val label = "${resource.name} [${resource.type}]$status refs: ${resource.referencedBy.size}$sourceLabel"
                        if (ImGui.selectable("$label##skin_editor_resource_${category}_${resource.name}", state.selectedResourceKey == resource.key)) {
                            operations.selectResource(resource)
                        }
                    }
                    ImGui.treePop()
                }
            }
        }
        ImGui.end()
    }

    private fun drawFilters() {
        if (readBuffer(queryBuffer) != state.resourceBrowser.query) {
            writeBuffer(queryBuffer, state.resourceBrowser.query)
        }
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("Search##skin_editor_resource_search", queryBuffer)) {
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
}

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
                val source = problem.source?.shortSource()?.let { " [$it]" }.orEmpty()
                val label = "[${problem.severity}][${problem.category}] ${problem.message}$source"
                if (ImGui.selectable("${label.toAsciiSafeText()}##skin_editor_problem_$index", state.selectedProblemIndex == index)) {
                    operations.selectProblem(index, problem)
                }
            }
        }
        ImGui.end()
    }

    private fun drawFilters() {
        if (readBuffer(queryBuffer) != state.problemFilters.query) {
            writeBuffer(queryBuffer, state.problemFilters.query)
        }
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputText("Search##skin_editor_problem_search", queryBuffer)) {
            state.problemFilters.query = readBuffer(queryBuffer)
        }

        val severityLabel = state.problemFilters.severity?.name ?: "All severities"
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
        ImGui.sameLine()
        ImGui.textUnformatted("${previewPresetLabel(state)} @ ${formatPreviewScale(state.previewInfo.scale)}")
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

class SkinEditorResourcePreviewPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.ResourcePreview)
        ImGui.setNextWindowBgAlpha(0f)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.ResourcePreview, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.ResourcePreview, layout.title)
        if (!expanded) {
            state.resourcePreviewCanvasRect = SkinEditorCanvasRect()
            ImGui.end()
            return
        }

        val selectedResource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == state.selectedResourceKey }
        drawResourcePreviewControls(selectedResource)
        ImGui.separator()
        ImGui.textWrapped(state.resourceVisualPreviewInfo.statusMessage)
        state.resourceVisualPreviewInfo.resolvedTexturePath?.let { path ->
            ImGui.textWrapped("Texture: ${File(path).name}")
            ImGui.textUnformatted("Size: ${state.resourceVisualPreviewInfo.textureWidth} x ${state.resourceVisualPreviewInfo.textureHeight}")
        }
        state.resourceVisualPreviewInfo.resolvedFontPath?.let { path ->
            ImGui.textWrapped("Font file: ${File(path).name}")
        }
        state.resourceVisualPreviewInfo.fontPreviewSource?.let { source ->
            ImGui.textWrapped("Font source: $source")
        }
        state.resourceVisualPreviewInfo.atlasPageName?.let { page ->
            ImGui.textWrapped("Atlas page: $page")
        }
        state.resourceVisualPreviewInfo.selectedRegionName?.let { regionName ->
            ImGui.textWrapped("Region overlay: $regionName")
        }
        ImGui.separator()

        val min = ImGui.cursorScreenPos
        val available = ImGui.contentRegionAvail
        state.resourcePreviewCanvasRect =
            SkinEditorCanvasRect(
                x = min.x,
                y = min.y,
                width = available.x.coerceAtLeast(1f),
                height = available.y.coerceAtLeast(1f),
            )
        ImGui.invisibleButton("##skin_editor_resource_preview_canvas", ImVec2(state.resourcePreviewCanvasRect.width, state.resourcePreviewCanvasRect.height))
        ImGui.end()
    }

    private fun drawResourcePreviewControls(selectedResource: SkinResourceInfo?) {
        val previewState = state.resourceVisualPreview
        if (selectedResource?.category == SkinResourceCategory.Font) {
            drawFontPreviewControls()
        } else {
            if (ImGui.beginCombo("Zoom##skin_editor_resource_preview_zoom", formatResourcePreviewZoom(previewState.zoomMode))) {
                SkinResourceVisualPreviewZoomMode.entries.forEach { zoomMode ->
                    if (ImGui.selectable("${formatResourcePreviewZoom(zoomMode)}##skin_editor_resource_preview_zoom_$zoomMode", zoomMode == previewState.zoomMode)) {
                        operations.setResourcePreviewZoomMode(zoomMode)
                    }
                }
                ImGui.endCombo()
            }
            ImGui.sameLine()
            with(dsl) {
                button("Reset##skin_editor_resource_preview_zoom_reset") {
                    operations.resetResourcePreviewZoom()
                }
            }

            val showBounds = booleanArrayOf(previewState.showRegionBounds)
            if (ImGui.checkbox("Show region bounds##skin_editor_resource_preview_bounds", showBounds)) {
                operations.setShowResourceRegionBounds(showBounds[0])
            }
            val showLabels = booleanArrayOf(previewState.showRegionLabels)
            if (ImGui.checkbox("Show region labels##skin_editor_resource_preview_labels", showLabels)) {
                operations.setShowResourceRegionLabels(showLabels[0])
            }
        }

        selectedResource?.let { resource ->
            if (resource.category == SkinResourceCategory.Atlas || resource.category == SkinResourceCategory.AtlasRegion) {
                drawAtlasRegionSelector(resource)
            }
            ImGui.textUnformatted("Selected resource: ${resource.category}.${resource.name}")
        } ?: ImGui.textUnformatted("Selected resource: <none>")
    }

    private fun drawFontPreviewControls() {
        val fontPreview = state.resourceVisualPreview.fontPreview
        val selectedScale = FontPreviewScales.minBy { scale -> kotlin.math.abs(scale - fontPreview.fontScale) }
        if (ImGui.beginCombo("Font scale##skin_editor_font_preview_scale", formatPreviewScale(selectedScale))) {
            FontPreviewScales.forEach { scale ->
                if (ImGui.selectable("${formatPreviewScale(scale)}##skin_editor_font_preview_scale_$scale", scale == selectedScale)) {
                    operations.setFontPreviewScale(scale)
                }
            }
            ImGui.endCombo()
        }
        val showUkrainian = booleanArrayOf(fontPreview.showUkrainianSample)
        if (ImGui.checkbox("Show Ukrainian##skin_editor_font_preview_uk", showUkrainian)) {
            operations.setShowUkrainianFontSample(showUkrainian[0])
        }
        val showAscii = booleanArrayOf(fontPreview.showAsciiSample)
        if (ImGui.checkbox("Show ASCII##skin_editor_font_preview_ascii", showAscii)) {
            operations.setShowAsciiFontSample(showAscii[0])
        }
        ImGui.textWrapped("Sample text uses the built-in preview block plus the toggles above for Ukrainian and ASCII coverage checks.")
    }

    private fun drawAtlasRegionSelector(resource: SkinResourceInfo) {
        if (resource.category == SkinResourceCategory.AtlasRegion) {
            ImGui.textWrapped("Region overlay follows the selected atlas region.")
            return
        }
        val atlasSource = when (resource.category) {
            SkinResourceCategory.Atlas -> resource.source
            else -> null
        } ?: return
        val regions =
            state.loadResult.resourceIndex.atlasRegions
                .filter { region -> region.source == atlasSource }
                .map(SkinResourceInfo::name)
        if (regions.isEmpty()) return

        val selectedName = state.resourceVisualPreview.selectedAtlasRegionName?.takeIf { name -> name in regions }
        val comboLabel = selectedName ?: "None"
        if (ImGui.beginCombo("Region overlay##skin_editor_resource_preview_region", comboLabel)) {
            if (ImGui.selectable("None##skin_editor_resource_preview_region_none", selectedName == null)) {
                operations.selectAtlasRegionPreview(null)
            }
            regions.forEach { regionName ->
                if (ImGui.selectable("$regionName##skin_editor_resource_preview_region_$regionName", regionName == selectedName)) {
                    operations.selectAtlasRegionPreview(regionName)
                }
            }
            ImGui.endCombo()
        }
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
            problem != null -> {
                drawProblemInspector(
                    problem = problem,
                    linkedStyle = state.loadResult.styleIndex.styles.firstOrNull { it.key == problem.styleKey },
                    linkedResource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == problem.resourceKey },
                )
            }

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
                drawResourceInspector(resource, state.loadResult.resourceIndex, state.loadResult.previewSkinAvailable)
            }

            else -> {
                ImGui.textWrapped("Select a style, resource, or problem to inspect the current skin foundation.")
            }
        }
        ImGui.end()
    }
}

private fun drawProblemInspector(
    problem: SkinProblem,
    linkedStyle: StyleInfo?,
    linkedResource: SkinResourceInfo?,
) {
    ImGui.textUnformatted("Problem")
    ImGui.textUnformatted("Severity: ${problem.severity}")
    ImGui.textUnformatted("Category: ${problem.category}")
    safeTextWrapped(problem.message)
    problem.source?.let { source -> safeTextWrapped("Source: $source") }
    problem.suggestedFix?.let { suggestedFix -> safeTextWrapped("Suggested fix: $suggestedFix") }

    problem.styleKey?.let { styleKey ->
        ImGui.separator()
        ImGui.textUnformatted("Linked style")
        ImGui.textUnformatted("${styleKey.type}.${styleKey.name}")
        linkedStyle?.let { style ->
            ImGui.textUnformatted("Fields: ${style.rawFieldCount}")
            ImGui.textUnformatted("Resource references: ${style.resourceReferences.size}")
        }
    }
    problem.resourceKey?.let { resourceKey ->
        ImGui.separator()
        ImGui.textUnformatted("Linked resource")
        ImGui.textUnformatted("${resourceKey.category}.${resourceKey.name}")
        if (linkedResource != null) {
            ImGui.textUnformatted("Type: ${linkedResource.type}")
            ImGui.textUnformatted("Resolved: ${if (linkedResource.resolved) "yes" else "no"}")
            linkedResource.source?.let { source -> safeTextWrapped("Source: $source") }
        } else {
            ImGui.textUnformatted("Resource is unresolved or no longer indexed.")
        }
    }
}

private fun drawResourceInspector(
    resource: SkinResourceInfo,
    resourceIndex: SkinResourceIndex,
    previewSkinAvailable: Boolean,
) {
    ImGui.textUnformatted("Resource: ${resource.name}")
    ImGui.textUnformatted("Category: ${resource.category}")
    ImGui.textUnformatted("Type: ${resource.type}")
    ImGui.textWrapped("Source: ${resource.source ?: "<none>"}")
    ImGui.textUnformatted("Resolved: ${if (resource.resolved) "yes" else "no"}")

    when (resource.category) {
        SkinResourceCategory.Color -> {
            ImGui.separator()
            ImGui.textUnformatted("Color value")
            drawDetail(resource, "value")
            drawDetail(resource, "hex")
            listOf("r", "g", "b", "a").forEach { channel -> drawDetail(resource, channel) }
            drawDetail(resource, "rawValue")
        }

        SkinResourceCategory.Font -> {
            ImGui.separator()
            ImGui.textUnformatted("Font file")
            ImGui.textWrapped("Visual preview is available in the Resource Preview panel when a matched .fnt or loaded skin font can be resolved.")
            ImGui.textWrapped(
                "Visual preview: ${
                    when {
                        resource.details["fontPreviewAvailable"] == "true" -> "available from matched .fnt"
                        previewSkinAvailable -> "available from loaded skin fallback if the skin font resolves"
                        else -> "not available"
                    }
                }",
            )
            ImGui.textWrapped(
                "Declaration state: ${
                    when {
                        resource.details["declaredInSkin"] == "true" && resource.details["discoveredFile"] == "true" -> "declared in skin and discovered as file"
                        resource.details["declaredInSkin"] == "true" -> "declared only in skin JSON"
                        resource.details["discoveredFile"] == "true" -> "discovered as file"
                        else -> "unknown"
                    }
                }",
            )
            drawDetail(resource, "file")
            drawDetail(resource, "matchedFile")
            drawDetail(resource, "matchedFileExtension")
            drawDetail(resource, "matchedFileSizeBytes")
            drawDetail(resource, "extension")
            drawDetail(resource, "sizeBytes")
            listOf(
                "fntFace",
                "fntSize",
                "fntLineHeight",
                "fntBase",
                "fntPages",
                "fntCharCount",
                "asciiGlyphCoverage",
                "ukrainianGlyphCoverage",
                "missingUkrainianGlyphs",
                "missingUkrainianGlyphCount",
                "fntReadable",
            ).forEach { field -> drawDetail(resource, field) }
        }

        SkinResourceCategory.Atlas -> {
            ImGui.separator()
            ImGui.textUnformatted("Atlas contents")
            ImGui.textWrapped("Visual preview available in Resource Preview panel.")
            drawDetail(resource, "pageCount")
            drawDetail(resource, "regionCount")
            drawDetail(resource, "pages")
            val regions =
                resourceIndex.atlasRegions
                    .filter { region -> region.source == resource.source }
                    .take(MaxInspectorAtlasRegions)
            ImGui.textUnformatted("Region names: ${regions.size}${if (regions.size == MaxInspectorAtlasRegions) "+" else ""}")
            regions.forEach { region -> ImGui.textWrapped(region.name) }
        }

        SkinResourceCategory.AtlasRegion -> {
            ImGui.separator()
            ImGui.textUnformatted("Atlas region")
            ImGui.textWrapped("Visual preview available in Resource Preview panel.")
            listOf("atlas", "page", "xy", "size", "orig", "offset", "index").forEach { field -> drawDetail(resource, field) }
        }

        SkinResourceCategory.Texture -> {
            ImGui.separator()
            ImGui.textUnformatted("Texture file")
            ImGui.textWrapped("Visual preview available in Resource Preview panel.")
            drawDetail(resource, "extension")
            drawDetail(resource, "sizeBytes")
        }

        SkinResourceCategory.Drawable,
        SkinResourceCategory.Unknown,
        -> {
            ImGui.separator()
            ImGui.textUnformatted("Resolution")
            drawDetail(resource, "origin")
            drawDetail(resource, "expectedCategory")
            drawDetail(resource, "resolvesDrawableAs")
        }
    }

    ImGui.separator()
    ImGui.textUnformatted("Referenced by: ${resource.referencedBy.size}")
    resource.referencedBy.forEach(::safeTextWrapped)
    if (resource.details.isNotEmpty()) {
        ImGui.separator()
        ImGui.textUnformatted("All metadata")
        resource.details.toSortedMap().forEach { (name, value) ->
            safeTextWrapped("$name: $value")
        }
    }
}

private fun drawDetail(
    resource: SkinResourceInfo,
    name: String,
) {
    resource.details[name]?.let { value -> safeTextWrapped("$name: $value") }
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

        ImGui.textWrapped("Preview layouts stay predefined for now. This panel keeps the workflow read-only while making Scene2D preview switching faster.")
        if (ImGui.beginCombo("Layout##skin_editor_preview_layout", previewLayouts.layoutOrDefault(state.previewLayoutId).displayName)) {
            previewLayouts.layouts.forEach { layoutOption ->
                if (ImGui.selectable("${layoutOption.displayName}##skin_editor_preview_layout_${layoutOption.id}", layoutOption.id == state.previewLayoutId)) {
                    operations.selectLayout(layoutOption.id)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset##skin_editor_preview_layout_reset") {
                operations.resetPreviewLayout()
            }
        }
        val selectedPreset = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId)
        val presetLabel = "${selectedPreset.displayName} (${selectedPreset.width} x ${selectedPreset.height})"
        if (ImGui.beginCombo("Screen##skin_editor_preview_screen", presetLabel)) {
            SkinPreviewScreenPresets.presets.forEach { preset ->
                val label = "${preset.displayName} (${preset.width} x ${preset.height})"
                if (ImGui.selectable("$label##skin_editor_preview_screen_${preset.id}", preset.id == selectedPreset.id)) {
                    operations.selectScreenPreset(preset.id)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        with(dsl) {
            button("Desktop##skin_editor_preview_screen_reset") {
                operations.resetPreviewScreenPreset()
            }
        }
        ImGui.textWrapped("Fixed screen presets preserve aspect ratio and may letterbox inside the preview canvas.")
        val selectedScale = PreviewScales.minBy { scale -> kotlin.math.abs(scale - state.previewSettings.scale) }
        if (ImGui.beginCombo("Scale##skin_editor_preview_scale", formatPreviewScale(selectedScale))) {
            PreviewScales.forEach { scale ->
                if (ImGui.selectable("${formatPreviewScale(scale)}##skin_editor_preview_scale_$scale", scale == selectedScale)) {
                    operations.setPreviewScale(scale)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        with(dsl) {
            button("100%##skin_editor_preview_scale_reset") {
                operations.resetPreviewScale()
            }
        }
        val showBounds = booleanArrayOf(state.previewSettings.showBounds)
        if (ImGui.checkbox("Show bounds##skin_editor_preview_bounds", showBounds)) {
            operations.setShowBounds(showBounds[0])
        }
        val showFallbackWarnings = booleanArrayOf(state.previewSettings.showFallbackWarnings)
        if (ImGui.checkbox("Show fallback warnings##skin_editor_preview_warnings", showFallbackWarnings)) {
            operations.setShowFallbackWarnings(showFallbackWarnings[0])
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset all##skin_editor_preview_reset_all") {
                operations.resetPreviewSettings()
            }
        }
        ImGui.separator()
        with(dsl) {
            button("Preview selected style##skin_editor_preview_selected_style") {
                operations.previewSelectedStyle()
            }
        }
        val selectedStyle = state.selectedStyleKey
        if (selectedStyle != null && state.previewLayoutId != SelectedStylePreviewLayout.Id) {
            ImGui.textWrapped("Selected style can be previewed using \"Preview selected style\".")
        }
        ImGui.separator()
        ImGui.textUnformatted("Loaded skin: ${if (state.loadResult.previewSkinAvailable) "yes" else "no"}")
        ImGui.textUnformatted("Layout: ${previewLayouts.layoutOrDefault(state.previewInfo.layoutId ?: state.previewLayoutId).displayName} (${state.previewInfo.layoutId ?: state.previewLayoutId})")
        ImGui.textUnformatted("Logical screen: ${state.previewInfo.logicalWidth} x ${state.previewInfo.logicalHeight}")
        ImGui.textUnformatted("Scale: ${formatPreviewScale(state.previewInfo.scale)}")
        ImGui.textUnformatted("Root actor: ${state.previewInfo.rootActorClass ?: "<none>"}")
        ImGui.textUnformatted("Actor count: ${state.previewInfo.actorCount}")
        ImGui.textUnformatted("Fallback issues: ${state.previewInfo.fallbackIssueCount}")
        if (!state.previewSettings.showFallbackWarnings && state.previewInfo.fallbackIssueCount > 0) {
            ImGui.textWrapped("Fallback warnings hidden. Preview may use fallback widgets.")
        }
        ImGui.textUnformatted("Selected style: ${selectedStyle?.let { "${it.type}.${it.name}" } ?: "<none>"}")
        ImGui.textUnformatted("Selected resource: ${selectedResourceSummary(state) ?: "<none>"}")
        drawSelectedResourcePreviewHint(state)
        ImGui.textUnformatted("Canvas: ${state.canvasRect.width.toInt()} x ${state.canvasRect.height.toInt()}")
        ImGui.end()
    }
}

private val PreviewScales = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
private val FontPreviewScales = listOf(0.5f, 1f, 1.5f, 2f)
private const val MaxInspectorAtlasRegions = 100

private fun formatPreviewScale(scale: Float): String = "${(scale * 100f).toInt()}%"

private fun formatResourcePreviewZoom(zoomMode: SkinResourceVisualPreviewZoomMode): String =
    when (zoomMode) {
        SkinResourceVisualPreviewZoomMode.Fit -> "Fit"
        SkinResourceVisualPreviewZoomMode.Percent50 -> "50%"
        SkinResourceVisualPreviewZoomMode.Percent100 -> "100%"
        SkinResourceVisualPreviewZoomMode.Percent200 -> "200%"
    }

private fun previewPresetLabel(state: SkinEditorState): String = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId).displayName

private fun selectedResourceSummary(state: SkinEditorState): String? =
    state.selectedResourceKey?.let { key ->
        val resource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == key }
            ?: return@let "${key.category}.${key.name}"
        "${resource.category}.${resource.name}${if (resource.resolved) "" else " (missing)"}"
    }

private fun drawSelectedResourcePreviewHint(state: SkinEditorState) {
    val selectedResourceKey = state.selectedResourceKey ?: return
    val resource = state.loadResult.resourceIndex.resources.firstOrNull { it.key == selectedResourceKey } ?: return
    if (resource.category in AvailablePreviewCategories) {
        ImGui.textWrapped("Visual preview is available in the Resource Preview panel.")
    } else if (resource.category in DeferredPreviewCategories) {
        ImGui.textWrapped("Visual preview for ${resource.category.name.lowercase()} resources is planned for the next steps.")
    }
}

private val AvailablePreviewCategories =
    setOf(
        SkinResourceCategory.Atlas,
        SkinResourceCategory.AtlasRegion,
        SkinResourceCategory.Texture,
        SkinResourceCategory.Font,
    )

private val DeferredPreviewCategories =
    setOf(
        SkinResourceCategory.Color,
    )

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

private fun safeTextWrapped(value: String) {
    ImGui.textWrapped(value.toAsciiSafeText())
}

private fun String.toAsciiSafeText(): String =
    buildString(length) {
        for (character in this@toAsciiSafeText) {
            when {
                character == '\n' || character == '\r' || character == '\t' -> append(character)
                character.code in 32..126 -> append(character)
                else -> append("\\u").append(character.code.toString(16).uppercase().padStart(4, '0'))
            }
        }
    }

private fun String.shortSource(): String {
    val normalized = substringBefore('#')
    val suffix = substringAfter('#', missingDelimiterValue = "")
    val fileName = File(normalized).name.ifBlank { normalized }
    return if (suffix.isBlank()) fileName else "$fileName#$suffix"
}
