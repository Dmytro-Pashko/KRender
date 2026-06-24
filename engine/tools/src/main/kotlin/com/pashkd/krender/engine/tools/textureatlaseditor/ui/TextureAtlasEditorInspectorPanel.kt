package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.ColorAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.ImageAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchValidationSeverity
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAsset
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasNinePatchRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedNinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingPlan
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TextureAtlasEditorInspectorPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Inspector)
        val expanded = beginPanel(TextureAtlasEditorPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val asset = state.selectedAsset()
        state.selectedResource()?.let { resource ->
            textLine("Resource: ${resource.name}")
            textLine("Resource type: ${resource.type.name}")
            when (resource) {
                is ImageAtlasResource -> {
                    textLine("Source: ${resource.sourcePath}")
                    textLine("Source rect: ${resource.sourceX}, ${resource.sourceY}, ${resource.sourceWidth ?: "?"}, ${resource.sourceHeight ?: "?"}")
                }
                is NinePatchAtlasResource -> {
                    textLine("Source: ${resource.sourcePath}")
                    textLine("Source rect: ${resource.sourceX}, ${resource.sourceY}, ${resource.sourceWidth ?: "?"}, ${resource.sourceHeight ?: "?"}")
                    textLine("Split: ${resource.split.joinToString().ifBlank { "<none>" }}")
                    textLine("Pad: ${resource.pad.joinToString().ifBlank { "<none>" }}")
                }
                is ColorAtlasResource -> {
                    textLine("Color RGBA: 0x${resource.rgba.toUInt().toString(16).uppercase()}")
                    textLine("Size: ${resource.width} x ${resource.height}")
                }
                is FontAtlasResource -> {
                    textLine("Source: ${resource.sourcePath ?: "<not set>"}")
                }
            }
            ImGui.separator()
        }
        drawNinePatchEditorSection()
        if (asset != null) {
            textLine("File: ${asset.fileName}")
            textLine("Path: ${asset.path}")
            textLine("Extension: ${asset.extension.ifBlank { "<none>" }}")
            textLine("Size: ${formatBytes(asset.sizeBytes)}")
            textLine("Modified: ${ModifiedAtFormatter.format(Instant.ofEpochMilli(asset.modifiedAtMillis).atZone(ZoneId.systemDefault()))}")
            asset.textureInfo?.let { info ->
                textLine("Metadata dimensions: ${info.width ?: 0} x ${info.height ?: 0}")
                textLine("Format: ${info.colorFormat ?: "<unknown>"}")
            }
            if (state.previewInfo.textureWidth > 0 && state.previewInfo.textureHeight > 0) {
                textLine("Preview dimensions: ${state.previewInfo.textureWidth} x ${state.previewInfo.textureHeight}")
            }
            if (asset.metadataPath != null) {
                textLine(".krmeta: ${asset.metadataPath}")
            } else {
                textLine(".krmeta: <missing>")
            }
            textLine("Metadata present: ${if (asset.metadataPath != null) "yes" else "no"}")
            val selectedNinePatchDocument = state.selectedNinePatchDocument()
            val selectedAtlasNinePatchRegion = state.selectedAtlasNinePatchRegion()
            if (selectedNinePatchDocument != null) {
                val document = selectedNinePatchDocument
                ImGui.separator()
                textLine("Nine-patch: yes")
                textLine("Image size: ${document.imageWidth} x ${document.imageHeight}")
                textLine("Drawable size: ${document.contentWidth} x ${document.contentHeight}")
                textLine("Stretch X: ${formatSegments(document.stretchX)}")
                textLine("Stretch Y: ${formatSegments(document.stretchY)}")
                textLine("Padding X: ${formatSegment(document.paddingX)}")
                textLine("Padding Y: ${formatSegment(document.paddingY)}")
                textLine("Validation issues: ${document.issues.size}")
                document.issues.forEach { issue ->
                    textLine("${issue.severity.name}: ${issue.message}")
                }
            } else if (selectedAtlasNinePatchRegion != null) {
                val region = selectedAtlasNinePatchRegion
                ImGui.separator()
                textLine("Nine-patch: atlas region")
                textLine("Region: ${region.id.regionName}")
                textLine("Page: ${region.id.pageName}")
                textLine("Split: ${region.split.joinToString().ifBlank { "<none>" }}")
                textLine("Pad: ${region.pad.joinToString().ifBlank { "<none>" }}")
            } else {
                textLine("Nine-patch: ${if (asset.fileName.endsWith(".9.png", ignoreCase = true)) "unavailable" else "no"}")
            }
            if (asset.registryMetadata.isNotEmpty()) {
                ImGui.separator()
                textLine("Indexed metadata")
                asset.registryMetadata.toSortedMap().forEach { (key, value) ->
                    textLine("$key: $value")
                }
            }

            state.selectedAtlasDocument()?.let { atlas ->
                ImGui.separator()
                textLine("Pages: ${atlas.pages.size}")
                textLine("Regions: ${atlas.regions.size}")
                state.selectedAtlasPageName?.let { pageName ->
                    textLine("Selected page: $pageName")
                    atlas.pages.firstOrNull { page -> page.name == pageName }?.details?.forEach { (key, value) ->
                        textLine("$key: $value")
                    }
                }
                state.selectedRegionId?.let { regionId ->
                    atlas.regions.firstOrNull { region -> region.id == regionId }?.let { region ->
                        ImGui.separator()
                        textLine("Selected region: ${region.id.regionName}")
                        textLine("Page: ${region.id.pageName}")
                        listOf(
                            "rotate" to region.rotate,
                            "xy" to region.xy?.let { "${it.first}, ${it.second}" },
                            "size" to region.size?.let { "${it.first}, ${it.second}" },
                            "orig" to region.orig?.let { "${it.first}, ${it.second}" },
                            "offset" to region.offset?.let { "${it.first}, ${it.second}" },
                            "split" to region.split.takeIf(List<Int>::isNotEmpty)?.joinToString(),
                            "pad" to region.pad.takeIf(List<Int>::isNotEmpty)?.joinToString(),
                            "index" to region.index?.toString(),
                        ).forEach { (label, value) ->
                            if (value != null) textLine("$label: $value")
                        }
                        val metrics = computeRegionMetrics(region, state.previewInfo.textureWidth, state.previewInfo.textureHeight)
                        textLine("Area: ${metrics.areaPixels ?: 0}px")
                        val uvText =
                            if (metrics.u0 != null && metrics.v0 != null && metrics.u1 != null && metrics.v1 != null) {
                                "(${formatUv(metrics.u0)}, ${formatUv(metrics.v0)}) -> (${formatUv(metrics.u1)}, ${formatUv(metrics.v1)})"
                            } else {
                                "<unknown>"
                            }
                        textLine("UV: $uvText")
                        textLine("Outside bounds: ${if (metrics.outsidePageBounds) "yes" else "no"}")
                    }
                }
            }
        } else {
            ImGui.textUnformatted("No asset selected.")
        }

        state.selectedPackingPlan()?.let { plan ->
            ImGui.separator()
            textLine("Texture atlas packing")
            textLine("Pages: ${plan.pages.size}")
            textLine("Packed regions: ${plan.packedRegionCount}")
            textLine("Skipped: ${plan.skippedCount}")
            textLine("Diagnostics: ${state.packing.lastResult.diagnostics.size}")
            state.selectedPackingRegion()?.let { region ->
                ImGui.separator()
                textLine("Packed region: ${region.displayName}")
                textLine("Source: ${region.sourcePath}")
                textLine("Page: ${region.pageIndex + 1}")
                textLine("Packed bounds: ${region.x}, ${region.y}, ${region.width}, ${region.height}")
                textLine("Rotated: ${if (region.rotated) "yes" else "no"}")
                textLine("Padding: ${region.padding}")
            }
        }

        ImGui.end()
    }

    private fun formatUv(value: Float): String = "%.4f".format(value)

    private fun formatSegment(segment: com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchSegment?): String =
        segment?.let { "${it.start}..${it.endInclusive}" } ?: "<none>"

    private fun formatSegments(segments: List<com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchSegment>): String =
        if (segments.isEmpty()) {
            "<none>"
        } else {
            segments.joinToString { segment -> "${segment.start}..${segment.endInclusive}" }
        }

    private val npBuf = ByteArray(NpBufSize)
    private var lastSyncedDraftKey: String? = null

    private fun drawNinePatchEditorSection() {
        val editor = state.ninePatchEditor
        val draft = editor.draft ?: return
        val resourceId = editor.selectedResourceId ?: return
        val resource = state.resources.items.firstOrNull { it.id == resourceId } as? NinePatchAtlasResource ?: return

        ImGui.separator()
        textLine("Nine-patch Editor: ${resource.name}")
        textLine("Content: ${draft.contentWidth} x ${draft.contentHeight}")
        if (editor.dirty) textLine("Status: modified (unsaved)")

        textLine("Stretch X: start=${draft.stretchX.start}  length=${draft.stretchX.length}")
        drawIntField("SX Start##np_sx_s", draft.stretchX.start) { v ->
            operations.updateNinePatchStretchX(v, draft.stretchX.length)
        }
        ImGui.sameLine()
        drawIntField("SX Len##np_sx_l", draft.stretchX.length) { v ->
            operations.updateNinePatchStretchX(draft.stretchX.start, v)
        }

        textLine("Stretch Y: start=${draft.stretchY.start}  length=${draft.stretchY.length}")
        drawIntField("SY Start##np_sy_s", draft.stretchY.start) { v ->
            operations.updateNinePatchStretchY(v, draft.stretchY.length)
        }
        ImGui.sameLine()
        drawIntField("SY Len##np_sy_l", draft.stretchY.length) { v ->
            operations.updateNinePatchStretchY(draft.stretchY.start, v)
        }

        val hasPadX = draft.paddingX != null
        val hasPadY = draft.paddingY != null
        textLine("Padding X: ${draft.paddingX?.let { "start=${it.start}  length=${it.length}" } ?: "(unset)"}")
        if (hasPadX) {
            drawIntField("PX Start##np_px_s", draft.paddingX!!.start) { v ->
                operations.updateNinePatchPaddingX(v, draft.paddingX!!.length)
            }
            ImGui.sameLine()
            drawIntField("PX Len##np_px_l", draft.paddingX!!.length) { v ->
                operations.updateNinePatchPaddingX(draft.paddingX!!.start, v)
            }
        } else {
            if (ImGui.button("Set Padding X##np_set_px")) {
                operations.updateNinePatchPaddingX(0, draft.contentWidth)
            }
        }

        textLine("Padding Y: ${draft.paddingY?.let { "start=${it.start}  length=${it.length}" } ?: "(unset)"}")
        if (hasPadY) {
            drawIntField("PY Start##np_py_s", draft.paddingY!!.start) { v ->
                operations.updateNinePatchPaddingY(v, draft.paddingY!!.length)
            }
            ImGui.sameLine()
            drawIntField("PY Len##np_py_l", draft.paddingY!!.length) { v ->
                operations.updateNinePatchPaddingY(draft.paddingY!!.start, v)
            }
        } else {
            if (ImGui.button("Set Padding Y##np_set_py")) {
                operations.updateNinePatchPaddingY(0, draft.contentHeight)
            }
        }

        if (ImGui.button("Apply Draft##np_apply")) {
            operations.applyNinePatchDraft()
        }
        ImGui.sameLine()
        if (ImGui.button("Reset##np_reset")) {
            operations.resetNinePatchDraft()
        }
        ImGui.sameLine()
        if (ImGui.button("Full Stretch##np_full")) {
            operations.useFullNinePatchStretch()
        }
        ImGui.sameLine()
        if (ImGui.button("Clear Padding##np_clear_pad")) {
            operations.clearNinePatchPadding()
        }

        if (editor.validationIssues.isNotEmpty()) {
            ImGui.separator()
            editor.validationIssues.forEach { issue ->
                val prefix = if (issue.severity == NinePatchValidationSeverity.Error) "Error" else "Warning"
                textLine("$prefix: ${issue.message}")
            }
        }
        ImGui.separator()
    }

    private inline fun drawIntField(label: String, currentValue: Int, crossinline onChange: (Int) -> Unit) {
        writeBuffer(npBuf, currentValue.toString())
        ImGui.setNextItemWidth(80f)
        if (ImGui.inputText(label, npBuf)) {
            readBuffer(npBuf).trim().toIntOrNull()?.let { parsed -> onChange(parsed) }
        }
    }

    companion object {
        private val ModifiedAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val NpBufSize = 16
    }
}

private data class InspectorRegionMetrics(
    val areaPixels: Int? = null,
    val u0: Float? = null,
    val v0: Float? = null,
    val u1: Float? = null,
    val v1: Float? = null,
    val outsidePageBounds: Boolean = false,
)

private fun computeRegionMetrics(
    region: TextureAtlasRegion,
    textureWidth: Int,
    textureHeight: Int,
): InspectorRegionMetrics {
    val xy = region.xy
    val size = region.size
    val area = size?.let { dimensions -> dimensions.first * dimensions.second }
    if (xy == null || size == null || textureWidth <= 0 || textureHeight <= 0) {
        return InspectorRegionMetrics(areaPixels = area)
    }
    val right = xy.first + size.first
    val bottom = xy.second + size.second
    return InspectorRegionMetrics(
        areaPixels = area,
        u0 = xy.first / textureWidth.toFloat(),
        v0 = xy.second / textureHeight.toFloat(),
        u1 = right / textureWidth.toFloat(),
        v1 = bottom / textureHeight.toFloat(),
        outsidePageBounds = xy.first < 0 || xy.second < 0 || right > textureWidth || bottom > textureHeight,
    )
}

private fun beginPanel(
    panelId: String,
    layout: ImGuiPanelLayout,
    tracker: ImGuiLayoutRuntimeTracker,
): Boolean {
    tracker.consumeRestoreLayout(panelId)?.let { restored ->
        ImGui.setNextWindowPos(ImVec2(restored.x, restored.y))
        ImGui.setNextWindowSize(ImVec2(restored.width, restored.height))
    } ?: run {
        ImGui.setNextWindowPos(ImVec2(layout.x, layout.y), imgui.Cond.FirstUseEver)
        ImGui.setNextWindowSize(ImVec2(layout.width, layout.height), imgui.Cond.FirstUseEver)
    }
    val expanded = ImGui.begin("${layout.title}###$panelId")
    tracker.capture(panelId)
    return expanded
}
