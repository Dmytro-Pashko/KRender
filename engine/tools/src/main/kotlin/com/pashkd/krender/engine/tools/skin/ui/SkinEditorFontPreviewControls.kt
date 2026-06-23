package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.skin.FontPreviewScales
import com.pashkd.krender.engine.tools.skin.FontPreviewTextHeight
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.formatPreviewScale
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.writeBuffer
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui

/** Font preview-specific controls for sample text and render toggles. */
internal class SkinEditorFontPreviewControls(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
) {
    private val fontSampleBuffer = ByteArray(1024)

    fun drawIfNeeded(selectedResource: SkinResourceInfo?) {
        if (selectedResource?.category != SkinResourceCategory.Font) return
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
}
