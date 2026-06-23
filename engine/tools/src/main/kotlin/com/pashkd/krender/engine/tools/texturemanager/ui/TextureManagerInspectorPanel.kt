package com.pashkd.krender.engine.tools.texturemanager.ui

import com.pashkd.krender.engine.tools.texturemanager.TextureManagerPanelIds
import com.pashkd.krender.engine.tools.texturemanager.TextureManagerState
import com.pashkd.krender.engine.tools.texturemanager.computeRegionMetrics
import com.pashkd.krender.engine.tools.texturemanager.selectedAsset
import com.pashkd.krender.engine.tools.texturemanager.selectedAtlasDocument
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TextureManagerInspectorPanel(
    private val state: TextureManagerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureManagerPanelIds.Inspector)
        val expanded = beginImGuiPanel(TextureManagerPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(TextureManagerPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        val asset = state.selectedAsset()
        if (asset == null) {
            ImGui.textUnformatted("No asset selected.")
            ImGui.end()
            return
        }

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
        asset.metadataPath?.let { path -> textLine(".krmeta: $path") } ?: textLine(".krmeta: <missing>")
        textLine("Metadata present: ${if (asset.metadataPath != null) "yes" else "no"}")
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

        ImGui.end()
    }

    private fun formatUv(value: Float): String = "%.4f".format(value)

    companion object {
        private val ModifiedAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
