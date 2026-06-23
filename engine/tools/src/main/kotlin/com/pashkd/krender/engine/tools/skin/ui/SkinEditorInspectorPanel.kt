package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.MaxInspectorAtlasRegions
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinProblem
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceIndex
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.StyleInfo
import com.pashkd.krender.engine.tools.skin.safeTextWrapped
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui

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

        val style =
            state.loadResult.styleIndex.styles
                .firstOrNull { it.key == state.selectedStyleKey }
        val resource =
            state.loadResult.resourceIndex.resources
                .firstOrNull { it.key == state.selectedResourceKey }
        val problem = state.selectedProblemIndex?.let(state.loadResult.problems::getOrNull)

        when {
            problem != null -> {
                drawProblemInspector(
                    problem = problem,
                    linkedStyle =
                        state.loadResult.styleIndex.styles
                            .firstOrNull { it.key == problem.styleKey },
                    linkedResource =
                        state.loadResult.resourceIndex.resources
                            .firstOrNull { it.key == problem.resourceKey },
                )
            }

            resource != null -> {
                drawResourceInspector(resource, state.loadResult.resourceIndex, state.loadResult.previewSkinAvailable)
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
            ImGui.textWrapped("Atlas region details are shown in the Resources preview section.")
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
}

private fun drawDetail(
    resource: SkinResourceInfo,
    name: String,
) {
    resource.details[name]?.let { value -> safeTextWrapped("$name: $value") }
}
