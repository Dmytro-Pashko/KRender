package com.pashkd.krender.engine.tools.textureatlaseditor.ui

import com.pashkd.krender.engine.tools.textureatlaseditor.BitmapFontGlyph
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.ImageAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchValidationSeverity
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorOperations
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPanelIds
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorState
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedAtlasDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedFontDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedFontPageTexturePath
import com.pashkd.krender.engine.tools.textureatlaseditor.selectedResource
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiPanelLayout
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class TextureAtlasEditorInspectorPanel(
    private val state: TextureAtlasEditorState,
    private val operations: TextureAtlasEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val resourceNameBuf = ByteArray(256)
    private var resourceNameKey: String? = null

    override fun draw() {
        val layout = layoutConfig.panels.getValue(TextureAtlasEditorPanelIds.Inspector)
        val expanded = beginPanel(TextureAtlasEditorPanelIds.Inspector, layout, layoutTracker)
        eventLogger.observe(TextureAtlasEditorPanelIds.Inspector, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }

        state.selectedResource()?.let { resource ->
            syncResourceNameBuffer(resource)
            textLine("Resource: ${resource.name}")
            textLine("Type: ${resource.type.name}")
            ImGui.setNextItemWidth(minOf(300f, (ImGui.contentRegionAvail.x - 80f).coerceAtLeast(120f)))
            ImGui.inputText("Name##atlas_resource_name", resourceNameBuf)
            ImGui.sameLine()
            if (ImGui.button("Rename##atlas_resource_rename")) {
                operations.renameSelectedResource(readBuffer(resourceNameBuf))
            }
            when (resource) {
                is ImageAtlasResource -> {
                    textLine("Source: ${resource.sourcePath}")
                    textLine("Rect: ${resource.sourceX}, ${resource.sourceY}, ${resource.sourceWidth ?: "?"}, ${resource.sourceHeight ?: "?"}")
                }
                is NinePatchAtlasResource -> {
                    textLine("Source: ${resource.sourcePath}")
                    textLine("Rect: ${resource.sourceX}, ${resource.sourceY}, ${resource.sourceWidth ?: "?"}, ${resource.sourceHeight ?: "?"}")
                    textLine("Split: ${resource.split.joinToString().ifBlank { "<none>" }}")
                    textLine("Pad: ${resource.pad.joinToString().ifBlank { "<none>" }}")
                }
                is FontAtlasResource -> {
                    textLine("Source: ${resource.sourcePath}")
                    textLine("Glyphs: ${resource.glyphCount}  Kernings: ${resource.kerningCount}  Pages: ${resource.pageTexturePaths.size}")
                    val packInAtlas = booleanArrayOf(resource.packInAtlas)
                    if (ImGui.checkbox("Pack font in atlas##atlas_font_pack_toggle", packInAtlas)) {
                        operations.setPackFontInAtlas(packInAtlas[0])
                    }
                    state.selectedFontPageTexturePath()?.let { texturePath ->
                        textLine("Font texture: $texturePath")
                    }
                    resource.atlasTexturePath?.let { atlasTexturePath ->
                        textLine("Atlas texture: $atlasTexturePath")
                    }
                }
                else -> {
                    textLine("This resource type is hidden from the final atlas workflow.")
                }
            }
            ImGui.separator()
        } ?: run {
            ImGui.textUnformatted("No resource selected.")
            ImGui.separator()
        }

        drawNinePatchEditorSection()
        drawFontInspectorSection()

        state.selectedRegionId?.let { regionId ->
            state.selectedAtlasDocument()?.regions?.firstOrNull { it.id == regionId }?.let { region ->
                ImGui.separator()
                textLine("Selected region: ${region.id.regionName}")
                textLine("Page: ${region.id.pageName}")
                region.xy?.let { textLine("xy: ${it.first}, ${it.second}") }
                region.size?.let { textLine("size: ${it.first}, ${it.second}") }
                region.split.takeIf(List<Int>::isNotEmpty)?.let { textLine("split: ${it.joinToString()}") }
                region.pad.takeIf(List<Int>::isNotEmpty)?.let { textLine("pad: ${it.joinToString()}") }
                region.index?.let { textLine("index: $it") }
            }
        }

        ImGui.end()
    }

    private val fontSampleBuf = ByteArray(256)
    private var fontSampleSynced = false
    private val fontGlyphFilterBuf = ByteArray(64)

    private fun drawFontInspectorSection() {
        val resource = state.selectedResource() as? FontAtlasResource ?: return
        val document = state.selectedFontDocument() ?: return
        ImGui.separator()
        textLine("Font Inspector: ${resource.name}")

        val info = document.info
        if (info != null) {
            textLine("Face: ${info.face ?: "<unknown>"}")
            textLine("Size: ${info.size ?: "?"}")
            if (info.bold) textLine("Bold: yes")
            if (info.italic) textLine("Italic: yes")
        }
        val common = document.common
        if (common != null) {
            textLine("Line height: ${common.lineHeight}")
            textLine("Base: ${common.base}")
            textLine("Scale: ${common.scaleW} x ${common.scaleH}")
        }
        textLine("Pages: ${document.pages.size}")
        textLine("Glyphs: ${document.glyphs.size}")
        textLine("Kernings: ${document.kernings.size}")

        if (document.pages.size > 1) {
            val currentPageName = document.pages.getOrNull(state.fontPreview.selectedPageIndex)?.file ?: "page 0"
            if (ImGui.beginCombo("Page##font_page_select", currentPageName)) {
                document.pages.forEachIndexed { index, page ->
                    if (ImGui.selectable("${page.id}: ${page.file}", state.fontPreview.selectedPageIndex == index)) {
                        operations.setFontPreviewPage(index)
                    }
                }
                ImGui.endCombo()
            }
        }

        state.selectedFontPageTexturePath()?.let { texturePath ->
            textLine("Selected page texture: $texturePath")
        }

        if (!fontSampleSynced) {
            writeBuffer(fontGlyphFilterBuf, state.fontPreview.glyphFilter)
            fontSampleSynced = true
        }
        textLine("Filter glyphs")
        ImGui.setNextItemWidth(120f)
        if (ImGui.inputText("##font_glyph_filter", fontGlyphFilterBuf)) {
            operations.setFontGlyphFilter(readBuffer(fontGlyphFilterBuf))
        }

        val filter = state.fontPreview.glyphFilter.lowercase()
        val filteredGlyphs = if (filter.isBlank()) {
            document.glyphs.take(MaxVisibleGlyphs)
        } else {
            document.glyphs.filter { glyph ->
                glyph.id.toString().contains(filter) ||
                    glyph.char?.lowercase()?.contains(filter) == true
            }.take(MaxVisibleGlyphs)
        }
        ImGui.beginChild("font_glyph_list", glm_.vec2.Vec2(0f, 150f), true)
        filteredGlyphs.forEach { glyph ->
            val label = glyphLabel(glyph)
            val selected = state.fontPreview.selectedGlyphId == glyph.id
            if (ImGui.selectable(label, selected)) {
                operations.selectFontGlyph(glyph.id)
            }
        }
        if (filteredGlyphs.isEmpty()) {
            textLine("No glyphs match filter.")
        }
        ImGui.endChild()

        state.fontPreview.selectedGlyphId?.let { glyphId ->
            document.glyphs.firstOrNull { it.id == glyphId }?.let { glyph ->
                ImGui.separator()
                textLine("Glyph: ${glyphLabel(glyph)}")
                textLine("Position: ${glyph.x}, ${glyph.y}")
                textLine("Size: ${glyph.width} x ${glyph.height}")
                textLine("Offset: ${glyph.xOffset}, ${glyph.yOffset}")
                textLine("xAdvance: ${glyph.xAdvance}")
                textLine("Page: ${glyph.page}")
                textLine("Channel: ${glyph.channel}")
                val kerningsForGlyph = document.kernings.filter { it.first == glyphId || it.second == glyphId }
                if (kerningsForGlyph.isNotEmpty()) {
                    textLine("Kerning pairs: ${kerningsForGlyph.size}")
                    kerningsForGlyph.take(5).forEach { k ->
                        textLine("  ${k.first} -> ${k.second}: ${k.amount}")
                    }
                }
            }
        }

        if (document.diagnostics.isNotEmpty()) {
            ImGui.separator()
            textLine("Font diagnostics: ${document.diagnostics.size}")
            document.diagnostics.take(10).forEach { diag ->
                textLine("${diag.severity.name}: ${diag.message}")
            }
        }
        ImGui.separator()
    }

    private fun glyphLabel(glyph: BitmapFontGlyph): String {
        val charDisplay = glyph.char?.takeIf { it.isNotBlank() && it.first().code > 32 }?.let { " '$it'" } ?: ""
        return "id=${glyph.id}$charDisplay [${glyph.width}x${glyph.height}]##glyph_${glyph.id}"
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

    private fun syncResourceNameBuffer(resource: com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasResource) {
        if (resourceNameKey != resource.id || readBuffer(resourceNameBuf) != resource.name) {
            writeBuffer(resourceNameBuf, resource.name)
            resourceNameKey = resource.id
        }
    }

    companion object {
        private const val NpBufSize = 16
        private const val MaxVisibleGlyphs = 200
    }
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
