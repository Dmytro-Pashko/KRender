package com.pashkd.krender.engine.tools.skin.ui

import com.pashkd.krender.engine.tools.common.ninepatch.NinePatchPreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewCanvasRect
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewOverlays
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewState
import com.pashkd.krender.engine.tools.common.texturepreview.computeTexturePreviewViewportLayout
import com.pashkd.krender.engine.tools.skin.EditableStyle
import com.pashkd.krender.engine.tools.skin.EditableStyleField
import com.pashkd.krender.engine.tools.skin.ResourceSearchWidth
import com.pashkd.krender.engine.tools.skin.SkinEditorOperations
import com.pashkd.krender.engine.tools.skin.SkinEditorPanelIds
import com.pashkd.krender.engine.tools.skin.SkinEditorState
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewKind
import com.pashkd.krender.engine.tools.skin.SkinStyleTemplates
import com.pashkd.krender.engine.tools.skin.StyleKey
import com.pashkd.krender.engine.tools.skin.findEditableStyle
import com.pashkd.krender.engine.tools.skin.readBuffer
import com.pashkd.krender.engine.tools.skin.styleFieldPreviewKey
import com.pashkd.krender.engine.tools.skin.writeBuffer
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import glm_.vec2.Vec2 as ImVec2

class SkinEditorStyleInspectorPanel(
    private val state: SkinEditorState,
    private val operations: SkinEditorOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val fieldBuffers = mutableMapOf<String, ByteArray>()
    private val renameBuffer = ByteArray(128)
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
        ImGui.textUnformatted("Draft edits update preview immediately. Use Save Changes to persist them.")
        ImGui.separator()

        val style = state.editSession.findEditableStyle(state.selectedStyleKey)
        if (style != null) {
            drawStyleInspector(style)
        } else {
            ImGui.textWrapped("Select a style in the Styles panel to inspect and edit its fields.")
        }
        drawRecentChanges()
        ImGui.end()
    }

    private fun drawStyleInspector(style: EditableStyle) {
        if (actionStyleKey != style.key) {
            writeBuffer(renameBuffer, style.key.name)
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

        ImGui.textUnformatted("Edit name:")
        ImGui.setNextItemWidth(ResourceSearchWidth)
        ImGui.inputText("##skin_editor_style_rename", renameBuffer)
        if (ImGui.button("Rename##skin_editor_style_rename_apply")) {
            operations.renameStyle(style.key, readBuffer(renameBuffer))
        }
        ImGui.sameLine()
        if (ImGui.button("Duplicate##skin_editor_style_duplicate_apply")) {
            operations.duplicateStyle(style.key, nextDuplicateName(style))
        }
        ImGui.sameLine()
        if (ImGui.button("Delete##skin_editor_style_delete")) {
            operations.deleteStyle(style.key)
        }

        ImGui.separator()
        drawAddKnownField(style)
        ImGui.separator()
        ImGui.textUnformatted("Fields")
        if (style.fields.isEmpty()) {
            ImGui.textUnformatted("No fields available.")
            return
        }
        style.fields.values.toList().forEach { field ->
            drawFieldRow(style, field)
        }
    }

    private fun drawFieldRow(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        ImGui.pushID("skin_editor_field_${style.key.type}_${style.key.name}_${field.name}")
        ImGui.textUnformatted(field.name)
        if (field.isReference) {
            drawReferencePicker(style, field)
            drawReferencePreview(style, field)
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

    private fun drawReferencePicker(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        val options = referenceOptions(field.referenceCategory)
        val currentLabel = field.value.ifBlank { "<none>" }
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.beginCombo("##reference", currentLabel)) {
            options.forEach { resource ->
                val label = "${resource.name} [${resource.category}]"
                if (ImGui.selectable("$label##${resource.category}_${resource.name}", resource.name == field.value)) {
                    operations.selectStyleFieldResource(style.key, field.name, resource)
                }
            }
            ImGui.endCombo()
        }
    }

    private fun drawReferencePreview(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        if (field.referenceCategory !in PreviewableFieldCategories || field.value.isBlank()) return
        val key = styleFieldPreviewKey(style.key, field.name)
        val info = state.styleFieldResourcePreviewInfos[key] ?: return
        ImGui.spacing()
        if (info.kind != SkinResourceVisualPreviewKind.Texture || info.texturePreviewHandle == null) {
            ImGui.textWrapped(info.statusMessage)
            return
        }
        drawTextureReferencePreview(field, info)
    }

    private fun drawTextureReferencePreview(
        field: EditableStyleField,
        info: SkinResourceVisualPreviewInfo,
    ) {
        val handle = info.texturePreviewHandle ?: return
        val sourceWidth = info.regionWidth ?: handle.width
        val sourceHeight = info.regionHeight ?: handle.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val width = ImGui.contentRegionAvail.x.coerceAtLeast(1f)
        val height = InlinePreviewHeight
        ImGui.beginChild("skin_editor_field_preview_${field.name}", ImVec2(width, height), true)
        val min = ImGui.cursorScreenPos
        val available = ImGui.contentRegionAvail
        val layout =
            computeTexturePreviewViewportLayout(
                rect = TexturePreviewCanvasRect(min.x, min.y, available.x.coerceAtLeast(1f), available.y.coerceAtLeast(1f)),
                textureWidth = sourceWidth,
                textureHeight = sourceHeight,
                previewState = TexturePreviewState(),
                contentPaddingPixels = InlinePreviewPadding,
            )
        TexturePreviewOverlays.drawCheckerboard(layout)
        val regionX = info.regionX ?: 0
        val regionY = info.regionY ?: 0
        val uSpan = handle.u1 - handle.u0
        val vSpan = handle.v1 - handle.v0
        val u0 = handle.u0 + (regionX.toFloat() / handle.width.coerceAtLeast(1).toFloat()) * uSpan
        val v0 = handle.v0 + (regionY.toFloat() / handle.height.coerceAtLeast(1).toFloat()) * vSpan
        val u1 = handle.u0 + ((regionX + sourceWidth).toFloat() / handle.width.coerceAtLeast(1).toFloat()) * uSpan
        val v1 = handle.v0 + ((regionY + sourceHeight).toFloat() / handle.height.coerceAtLeast(1).toFloat()) * vSpan
        ImGui.windowDrawList.addImage(
            handle.id,
            ImVec2(layout.imageX, layout.imageY),
            ImVec2(layout.imageX + layout.imageWidth, layout.imageY + layout.imageHeight),
            ImVec2(u0, v0),
            ImVec2(u1, v1),
        )
        TexturePreviewOverlays.drawRegionBounds(
            regions =
                listOf(
                    TexturePreviewRegion(
                        id = field.name,
                        label = field.value,
                        x = 0,
                        y = 0,
                        width = sourceWidth,
                        height = sourceHeight,
                    ),
                ),
            layout = layout,
        )
        info.ninePatchDraft?.let { draft ->
            NinePatchPreviewOverlays.drawDraftGuides(NinePatchPreviewOverlays.buildDraftOverlay(draft, layout))
        }
        ImGui.dummy(ImVec2(available.x.coerceAtLeast(1f), available.y.coerceAtLeast(1f)))
        ImGui.endChild()
    }

    private fun drawRawField(
        style: EditableStyle,
        field: EditableStyleField,
    ) {
        val bufferKey = "${style.key.type}.${style.key.name}.${field.name}"
        val buffer = fieldBuffers.getOrPut(bufferKey) { ByteArray(512) }
        syncBuffer(buffer, field.value)
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.inputText("##value", buffer)) {
            operations.updateStyleField(style.key, field.name, readBuffer(buffer))
        }
    }

    private fun drawAddKnownField(style: EditableStyle) {
        ImGui.textUnformatted("Add field")
        ImGui.textUnformatted("Use this section to create a new known field for the selected style.")
        val availableFields =
            SkinStyleTemplates
                .fieldsFor(style.key.type)
                .filterNot { template -> style.fields.keys.any { name -> name.equals(template.name, ignoreCase = true) } }
        if (availableFields.isEmpty()) {
            ImGui.textUnformatted("All known fields are already present.")
            return
        }
        pendingFieldName = pendingFieldName?.takeIf { name -> availableFields.any { it.name == name } } ?: availableFields.first().name
        ImGui.setNextItemWidth(ResourceSearchWidth)
        if (ImGui.beginCombo("##skin_editor_add_field", pendingFieldName ?: "Select field")) {
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

    private fun nextDuplicateName(style: EditableStyle): String {
        val baseName = style.key.name
        val preferred = "$baseName-copy"
        if (state.editSession.styles[StyleKey(style.key.type, preferred)]?.deleted != false) {
            return preferred
        }
        var suffix = 2
        while (true) {
            val candidate = "$preferred-$suffix"
            val candidateKey = StyleKey(style.key.type, candidate)
            if (state.editSession.styles[candidateKey]?.deleted != false) return candidate
            suffix++
        }
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
        private const val MaxVisibleChanges = 20
        private const val InlinePreviewHeight = 112f
        private const val InlinePreviewPadding = 8
        private val PreviewableFieldCategories =
            setOf(
                SkinResourceCategory.Drawable,
                SkinResourceCategory.Texture,
            )
    }
}
