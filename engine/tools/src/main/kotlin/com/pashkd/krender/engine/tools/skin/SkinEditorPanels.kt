package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.api.colorButton
import imgui.dsl
import java.io.File
import glm_.vec2.Vec2 as ImVec2

class SkinEditorResourceBrowserPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val ui: UiService,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val queryBuffer = ByteArray(256)
    private val fontSampleBuffer = ByteArray(1024)

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
                    ImGui.treePop()
                }
            }
        }
        drawResourcePreviewSection()
        ImGui.end()
    }

    private fun drawResourcePreviewSection() {
        ImGui.separator()
        ImGui.textUnformatted("Resource Preview")
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
        state.resourceVisualPreviewInfo.colorValue?.let { value ->
            ImGui.textWrapped("Color: $value")
        }
        state.resourceVisualPreviewInfo.atlasPageName?.let { page ->
            ImGui.textWrapped("Atlas page: $page")
        }
        state.resourceVisualPreviewInfo.selectedRegionName?.let { regionName ->
            ImGui.textWrapped("Region overlay: $regionName")
        }
        ImGui.separator()
        drawInlineResourcePreview()
    }

    private fun drawInlineResourcePreview() {
        val info = state.resourceVisualPreviewInfo
        when (info.kind) {
            SkinResourceVisualPreviewKind.Texture -> {
                val handle = info.texturePreviewHandle
                if (handle == null) {
                    ImGui.textWrapped("Image preview unavailable.")
                } else {
                    val availableWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
                    val scale =
                        when (state.resourceVisualPreview.zoomMode) {
                            SkinResourceVisualPreviewZoomMode.Fit ->
                                minOf(
                                    availableWidth / handle.width.coerceAtLeast(1).toFloat(),
                                    MaxInlineResourcePreviewHeight / handle.height.coerceAtLeast(1).toFloat(),
                                ).coerceAtLeast(MinInlineResourcePreviewScale)

                            SkinResourceVisualPreviewZoomMode.Percent50 -> 0.5f
                            SkinResourceVisualPreviewZoomMode.Percent100 -> 1f
                            SkinResourceVisualPreviewZoomMode.Percent200 -> 2f
                        }
                    val width = handle.width * scale
                    val height = handle.height * scale
                    if (!ui.drawTexturePreview(handle, width, height)) {
                        ImGui.textWrapped("Image preview unavailable: UI backend rejected the texture handle.")
                    }
                }
            }

            SkinResourceVisualPreviewKind.Color -> {
                val selected = state.selectedResourceKey?.let(state.editSession.resources::get)
                val values = selected?.values
                val color = values?.let(::parseResourceColor)
                if (color != null) {
                    colorButton("Color preview##skin_editor_resource_color_preview", color[0], color[1], color[2], color[3])
                }
            }

            SkinResourceVisualPreviewKind.Font -> {
                val handle = info.texturePreviewHandle
                if (handle == null) {
                    ImGui.textWrapped("Font preview image unavailable.")
                } else {
                    val availableWidth = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
                    val scale = minOf(1f, availableWidth / handle.width.coerceAtLeast(1).toFloat())
                    if (!ui.drawTexturePreview(handle, handle.width * scale, handle.height * scale)) {
                        ImGui.textWrapped("Font preview unavailable: UI backend rejected the texture handle.")
                    }
                }
            }

            SkinResourceVisualPreviewKind.None -> Unit
        }
    }

    private fun drawResourcePreviewControls(selectedResource: SkinResourceInfo?) {
        val previewState = state.resourceVisualPreview
        if (selectedResource?.category == SkinResourceCategory.Font) {
            drawFontPreviewControls()
        } else if (selectedResource?.category != SkinResourceCategory.Color) {
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
        if (readBuffer(fontSampleBuffer) != fontPreview.sampleText) {
            writeBuffer(fontSampleBuffer, fontPreview.sampleText)
        }
        ImGui.textUnformatted("Preview text:")
        if (ImGui.inputTextMultiline("##skin_editor_font_preview_text", fontSampleBuffer, ImVec2(-1f, FontPreviewTextHeight))) {
            operations.setFontPreviewSampleText(readBuffer(fontSampleBuffer))
        }
        val selectedScale = FontPreviewScales.minBy { scale -> kotlin.math.abs(scale - fontPreview.fontScale) }
        if (ImGui.beginCombo("Font scale##skin_editor_font_preview_scale", formatPreviewScale(selectedScale))) {
            FontPreviewScales.forEach { scale ->
                if (ImGui.selectable("${formatPreviewScale(scale)}##skin_editor_font_preview_scale_$scale", scale == selectedScale)) {
                    operations.setFontPreviewScale(scale)
                }
            }
            ImGui.endCombo()
        }
        val showCyrillic = booleanArrayOf(fontPreview.showCyrillicSample)
        if (ImGui.checkbox("Show Cyrillic##skin_editor_font_preview_uk", showCyrillic)) {
            operations.setShowCyrillicFontSample(showCyrillic[0])
        }
        val showAscii = booleanArrayOf(fontPreview.showAsciiSample)
        if (ImGui.checkbox("Show ASCII##skin_editor_font_preview_ascii", showAscii)) {
            operations.setShowAsciiFontSample(showAscii[0])
        }
        ImGui.textWrapped("Sample text uses the built-in preview block plus Cyrillic and ASCII coverage toggles.")
    }

    private fun drawAtlasRegionSelector(resource: SkinResourceInfo) {
        if (resource.category == SkinResourceCategory.AtlasRegion) {
            ImGui.textWrapped("Region overlay follows the selected atlas region.")
            return
        }
        val atlasSource = resource.source.takeIf { resource.category == SkinResourceCategory.Atlas } ?: return
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

    private fun resourceColor(resource: SkinResourceInfo): FloatArray? {
        val values = state.editSession.resources[resource.key]?.values ?: resource.details
        return parseResourceColor(values)
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

class SkinEditorStyleEditorPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val fieldBuffers = mutableMapOf<String, ByteArray>()
    private val colorBuffers = mutableMapOf<String, ByteArray>()
    private val renameBuffer = ByteArray(128)
    private val duplicateBuffer = ByteArray(128)
    private var pendingFieldName: String? = null
    private var actionStyleKey: StyleKey? = null

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.StyleEditor)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.StyleEditor, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.StyleEditor, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.textUnformatted("Editing: ${if (state.editSession.dirty) "dirty" else "clean"}")
        ImGui.textUnformatted("Pending changes: ${state.editSession.changes.size}")
        ImGui.textUnformatted("Edits are in-memory only. Saving is deferred.")
        if (ImGui.button("Discard All##skin_editor_style_editor_discard")) {
            operations.discardInMemoryEdits()
        }
        ImGui.sameLine()
        ImGui.textUnformatted("Save deferred")
        ImGui.separator()

        val style = state.editSession.findEditableStyle(state.selectedStyleKey)
        val colorResource =
            state.selectedResourceKey
                ?.takeIf { key -> key.category == SkinResourceCategory.Color }
                ?.let(state.editSession.resources::get)
        when {
            style != null -> drawStyleEditor(style)
            colorResource != null -> drawColorEditor(colorResource)
            else -> {
                ImGui.textWrapped("Select a style to edit its fields, or select a Color resource for lightweight color editing.")
            }
        }
        drawRecentChanges()
        ImGui.end()
    }

    private fun drawStyleEditor(style: EditableStyle) {
        if (actionStyleKey != style.key) {
            writeBuffer(renameBuffer, style.key.name)
            writeBuffer(duplicateBuffer, "${style.key.name}-copy")
            actionStyleKey = style.key
        }
        ImGui.textUnformatted("Style: ${style.key.name}")
        ImGui.textUnformatted("Type: ${style.key.type}")
        val flags =
            buildList {
                if (style.createdInEditor) add("created")
                if (style.renamedInEditor) add("renamed")
            }
        if (flags.isNotEmpty()) {
            ImGui.textUnformatted("State: ${flags.joinToString()}")
        }

        ImGui.setNextItemWidth(180f)
        ImGui.inputText("Rename##skin_editor_style_rename", renameBuffer)
        ImGui.sameLine()
        if (ImGui.button("Apply##skin_editor_style_rename_apply")) {
            operations.renameStyle(style.key, readBuffer(renameBuffer))
        }
        ImGui.setNextItemWidth(180f)
        ImGui.inputText("Duplicate as##skin_editor_style_duplicate", duplicateBuffer)
        ImGui.sameLine()
        if (ImGui.button("Duplicate##skin_editor_style_duplicate_apply")) {
            operations.duplicateStyle(style.key, readBuffer(duplicateBuffer))
        }
        if (ImGui.button("Delete In Memory##skin_editor_style_delete")) {
            operations.deleteStyle(style.key)
        }

        ImGui.separator()
        drawAddKnownField(style)
        ImGui.separator()
        ImGui.textUnformatted("Fields")
        drawFieldGroup("Properties", style, style.fields.values.filter(EditableStyleField::isReference))
        drawFieldGroup(
            "Scalar values",
            style,
            style.fields.values.filter { field -> !field.isReference && field.valueType in ScalarValueTypes },
        )
        drawFieldGroup(
            "Other / raw values",
            style,
            style.fields.values.filter { field -> !field.isReference && field.valueType !in ScalarValueTypes },
        )
    }

    private fun drawFieldGroup(
        title: String,
        style: EditableStyle,
        fields: List<EditableStyleField>,
    ) {
        if (fields.isEmpty()) return
        ImGui.textUnformatted(title)
        fields.toList().forEach { field ->
            ImGui.pushID("skin_editor_field_${style.key.type}_${style.key.name}_${field.name}")
            ImGui.textUnformatted(field.name)
            if (field.isReference) {
                drawReferencePicker(style, field)
            } else {
                drawRawField(style, field)
            }
            ImGui.sameLine()
            if (field.originalValue != null && ImGui.button("Reset")) {
                operations.resetStyleField(style.key, field.name)
            }
            ImGui.sameLine()
            if (ImGui.button("Remove")) {
                operations.removeStyleField(style.key, field.name)
            }
            ImGui.popID()
        }
        ImGui.separator()
    }

    private fun drawReferencePicker(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        val options = referenceOptions(field.referenceCategory)
        val currentLabel = field.value.ifBlank { "<none>" }
        ImGui.setNextItemWidth(-90f)
        if (ImGui.beginCombo("##reference", currentLabel)) {
            options.forEach { resource ->
                val label = "${resource.name} [${resource.category}]"
                if (ImGui.selectable("$label##${resource.category}_${resource.name}", resource.name == field.value)) {
                    operations.updateStyleField(style.key, field.name, resource.name)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawRawField(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        val bufferKey = "${style.key.type}.${style.key.name}.${field.name}"
        val buffer = fieldBuffers.getOrPut(bufferKey) { ByteArray(512) }
        syncBuffer(buffer, field.value)
        ImGui.setNextItemWidth(-90f)
        if (ImGui.inputText("##value", buffer)) {
            operations.updateStyleField(style.key, field.name, readBuffer(buffer))
        }
    }

    private fun drawAddKnownField(style: EditableStyle) {
        ImGui.textUnformatted("Create Field")
        val availableFields =
            SkinStyleTemplates.fieldsFor(style.key.type)
                .filterNot { template -> style.fields.keys.any { name -> name.equals(template.name, ignoreCase = true) } }
        if (availableFields.isEmpty()) {
            ImGui.textUnformatted("All known fields are already present.")
            return
        }
        pendingFieldName = pendingFieldName?.takeIf { name -> availableFields.any { it.name == name } } ?: availableFields.first().name
        if (ImGui.beginCombo("Add field##skin_editor_add_field", pendingFieldName ?: "Select field")) {
            availableFields.forEach { template ->
                if (ImGui.selectable(template.name, template.name == pendingFieldName)) {
                    pendingFieldName = template.name
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        if (ImGui.button("Add##skin_editor_add_field_apply")) {
            availableFields.firstOrNull { template -> template.name == pendingFieldName }?.let { template ->
                operations.addStyleField(style.key, template)
            }
        }
    }

    private fun drawColorEditor(resource: EditableResource) {
        ImGui.textUnformatted("Color: ${resource.key.name}")
        val fields =
            resource.values.keys
                .takeIf(Collection<String>::isNotEmpty)
                ?: listOf("value")
        fields.forEach { fieldName ->
            val currentValue = resource.values[fieldName].orEmpty()
            val bufferKey = "${resource.key.name}.$fieldName"
            val buffer = colorBuffers.getOrPut(bufferKey) { ByteArray(64) }
            syncBuffer(buffer, currentValue)
            ImGui.setNextItemWidth(-1f)
            if (ImGui.inputText("$fieldName##skin_editor_color_$bufferKey", buffer)) {
                operations.updateColorResource(resource.key, fieldName, readBuffer(buffer))
            }
        }
        val warning = validateEditableColor(resource)
        if (warning == null) {
            ImGui.textUnformatted("Color value is valid.")
        } else {
            ImGui.textWrapped("Edit warning: $warning")
        }
    }

    private fun drawRecentChanges() {
        ImGui.separator()
        ImGui.textUnformatted("Recent changes")
        if (state.editSession.changes.isEmpty()) {
            ImGui.textUnformatted("No pending changes.")
            return
        }
        state.editSession.changes.takeLast(MaxVisibleChanges).asReversed().forEachIndexed { index, change ->
            if (ImGui.selectable("${change.description}##skin_editor_change_$index")) {
                operations.selectEditChange(change)
            }
        }
    }

    private fun referenceOptions(category: SkinResourceCategory?): List<SkinResourceInfo> =
        when (category) {
            SkinResourceCategory.Font -> state.loadResult.resourceIndex.fonts
            SkinResourceCategory.Color -> state.loadResult.resourceIndex.colors
            SkinResourceCategory.Drawable ->
                state.loadResult.resourceIndex.drawables +
                    state.loadResult.resourceIndex.atlasRegions +
                    state.loadResult.resourceIndex.textures

            SkinResourceCategory.Texture -> state.loadResult.resourceIndex.textures
            else -> emptyList()
        }.filter(SkinResourceInfo::resolved)
            .distinctBy(SkinResourceInfo::key)
            .sortedWith(compareBy(SkinResourceInfo::category, SkinResourceInfo::name))

    private fun validateEditableColor(resource: EditableResource): String? {
        val hex = resource.values["value"] ?: resource.values["hex"]
        if (hex != null) {
            return if (SimpleEditableHexColor.matches(hex)) null else "Use #RRGGBB, #RRGGBBAA, RRGGBB, or RRGGBBAA."
        }
        val missing = listOf("r", "g", "b").filterNot(resource.values::containsKey)
        if (missing.isNotEmpty()) return "Missing channel(s): ${missing.joinToString()}."
        val invalid =
            listOf("r", "g", "b", "a")
                .filter(resource.values::containsKey)
                .filter { channel -> resource.values[channel]?.toFloatOrNull()?.let { it !in 0f..1f } ?: true }
        return invalid.takeIf(List<String>::isNotEmpty)?.let { "Invalid channel(s): ${it.joinToString()}; expected 0.0..1.0." }
    }

    private fun syncBuffer(
        buffer: ByteArray,
        value: String,
    ) {
        if (readBuffer(buffer) != value) {
            writeBuffer(buffer, value)
        }
    }

    private companion object {
        private val ScalarValueTypes = setOf("string", "boolean", "integer", "number", "primitive")
        private val SimpleEditableHexColor = Regex("^#?(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        private const val MaxVisibleChanges = 20
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
            ImGui.textWrapped("Visual preview is available in the Resources preview section when a matched .fnt or loaded skin font can be resolved.")
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
            ImGui.textWrapped("Visual preview available in the Resources preview section.")
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
            ImGui.textWrapped("Visual preview available in the Resources preview section.")
            listOf("atlas", "page", "xy", "size", "orig", "offset", "index").forEach { field -> drawDetail(resource, field) }
        }

        SkinResourceCategory.Texture -> {
            ImGui.separator()
            ImGui.textUnformatted("Texture file")
            ImGui.textWrapped("Visual preview available in the Resources preview section.")
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
    private val labelTextBuffer = ByteArray(256)
    private val buttonTextBuffer = ByteArray(256)
    private val textFieldBuffer = ByteArray(256)

    override fun draw() {
        val layout = layoutConfig.panels.getValue(SkinEditorPanelIds.PreviewControls)
        val expanded = beginImGuiPanel(SkinEditorPanelIds.PreviewControls, layout, layoutTracker)
        eventLogger.observe(SkinEditorPanelIds.PreviewControls, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        if (ImGui.beginCombo("Layout##skin_editor_preview_layout", previewLayouts.layoutOrDefault(state.previewLayoutId).displayName)) {
            previewLayouts.layouts.forEach { layoutOption ->
                if (ImGui.selectable("${layoutOption.displayName}##skin_editor_preview_layout_${layoutOption.id}", layoutOption.id == state.previewLayoutId)) {
                    operations.selectLayout(layoutOption.id)
                }
            }
            ImGui.endCombo()
        }
        drawPreviewTextSettings()
        val showFallbackWarnings = booleanArrayOf(state.previewSettings.showFallbackWarnings)
        if (ImGui.checkbox("Show fallback warnings##skin_editor_preview_warnings", showFallbackWarnings)) {
            operations.setShowFallbackWarnings(showFallbackWarnings[0])
        }
        val selectedStyle = state.selectedStyleKey
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

    private fun drawPreviewTextSettings() {
        val text = state.previewSettings.text
        syncPreviewBuffer(labelTextBuffer, text.labelText)
        if (ImGui.inputText("Label text##skin_editor_preview_label_text", labelTextBuffer)) {
            operations.setPreviewLabelText(readBuffer(labelTextBuffer))
        }
        syncPreviewBuffer(buttonTextBuffer, text.buttonText)
        if (ImGui.inputText("Button text##skin_editor_preview_button_text", buttonTextBuffer)) {
            operations.setPreviewButtonText(readBuffer(buttonTextBuffer))
        }
        syncPreviewBuffer(textFieldBuffer, text.textFieldPlaceholder)
        if (ImGui.inputText("TextField text##skin_editor_preview_field_text", textFieldBuffer)) {
            operations.setPreviewTextFieldPlaceholder(readBuffer(textFieldBuffer))
        }
    }

    private fun syncPreviewBuffer(
        buffer: ByteArray,
        value: String,
    ) {
        if (readBuffer(buffer) != value) {
            writeBuffer(buffer, value)
        }
    }
}
