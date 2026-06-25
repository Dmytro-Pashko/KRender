package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.EngineContext

internal class TextureAtlasNinePatchOperations(
    private val state: TextureAtlasEditorState,
    private val engine: EngineContext,
) {
    fun beginNinePatchEditing(resourceId: String) {
        val resource = state.resources.items.firstOrNull { it.id == resourceId }
        if (resource !is NinePatchAtlasResource) {
            state.ninePatchEditor = NinePatchEditorState()
            return
        }
        val document = state.project.ninePatchDocuments[resource.sourcePath]
        val draft = buildNinePatchDraft(resource, document)
        if (draft == null) {
            state.ninePatchEditor =
                NinePatchEditorState(
                    selectedResourceId = resourceId,
                    validationIssues =
                        listOf(
                            NinePatchValidationIssue(NinePatchValidationSeverity.Error, "Cannot determine content dimensions for this resource."),
                        ),
                )
            state.statusMessage = "Cannot edit Nine-patch: content dimensions are unavailable."
            return
        }
        val issues = validateNinePatchDraft(draft)
        state.ninePatchEditor =
            NinePatchEditorState(
                selectedResourceId = resourceId,
                draft = draft,
                dirty = false,
                validationIssues = issues,
            )
        engine.logger.info(TAG) {
            "NinePatch editing started resource='${resource.name}' content=${draft.contentWidth}x${draft.contentHeight} issues=${issues.size}"
        }
    }

    fun updateNinePatchStretchX(
        start: Int,
        length: Int,
    ) {
        val draft = state.ninePatchEditor.draft ?: return
        engine.logger.info(TAG) { "Texture Atlas Editor NinePatch draft stretchX update resource='${state.ninePatchEditor.selectedResourceId ?: "<none>"}' old=${draft.stretchX.start}:${draft.stretchX.length} new=$start:$length" }
        applyDraftUpdate(draft.copy(stretchX = NinePatchSegment(start = start, length = length)))
    }

    fun updateNinePatchStretchY(
        start: Int,
        length: Int,
    ) {
        val draft = state.ninePatchEditor.draft ?: return
        engine.logger.info(TAG) { "Texture Atlas Editor NinePatch draft stretchY update resource='${state.ninePatchEditor.selectedResourceId ?: "<none>"}' old=${draft.stretchY.start}:${draft.stretchY.length} new=$start:$length" }
        applyDraftUpdate(draft.copy(stretchY = NinePatchSegment(start = start, length = length)))
    }

    fun updateNinePatchPaddingX(
        start: Int?,
        length: Int?,
    ) {
        val draft = state.ninePatchEditor.draft ?: return
        val paddingX = if (start != null && length != null) NinePatchSegment(start = start, length = length) else null
        engine.logger.info(TAG) {
            "Texture Atlas Editor NinePatch draft paddingX update resource='${state.ninePatchEditor.selectedResourceId ?: "<none>"}' old=${draft.paddingX?.start}:${draft.paddingX?.length} new=${paddingX?.start}:${paddingX?.length}"
        }
        applyDraftUpdate(draft.copy(paddingX = paddingX))
    }

    fun updateNinePatchPaddingY(
        start: Int?,
        length: Int?,
    ) {
        val draft = state.ninePatchEditor.draft ?: return
        val paddingY = if (start != null && length != null) NinePatchSegment(start = start, length = length) else null
        engine.logger.info(TAG) {
            "Texture Atlas Editor NinePatch draft paddingY update resource='${state.ninePatchEditor.selectedResourceId ?: "<none>"}' old=${draft.paddingY?.start}:${draft.paddingY?.length} new=${paddingY?.start}:${paddingY?.length}"
        }
        applyDraftUpdate(draft.copy(paddingY = paddingY))
    }

    fun clearNinePatchPadding() {
        val draft = state.ninePatchEditor.draft ?: return
        applyDraftUpdate(draft.copy(paddingX = null, paddingY = null))
        state.statusMessage = "Cleared Nine-patch padding guides."
    }

    fun useFullNinePatchStretch() {
        val draft = state.ninePatchEditor.draft ?: return
        applyDraftUpdate(
            draft.copy(
                stretchX = NinePatchSegment(start = 0, length = draft.contentWidth),
                stretchY = NinePatchSegment(start = 0, length = draft.contentHeight),
            ),
        )
        state.statusMessage = "Set Nine-patch stretch to full content bounds."
    }

    fun resetNinePatchDraft() {
        val resourceId = state.ninePatchEditor.selectedResourceId ?: return
        beginNinePatchEditing(resourceId)
        state.statusMessage = "Reset Nine-patch draft from source."
    }

    fun applyNinePatchDraft() {
        val draft = state.ninePatchEditor.draft ?: return
        val resourceId = state.ninePatchEditor.selectedResourceId ?: return
        val issues = validateNinePatchDraft(draft)
        state.ninePatchEditor.validationIssues = issues
        if (issues.any { it.severity == NinePatchValidationSeverity.Error }) {
            state.statusMessage = "Cannot apply Nine-patch draft: there are validation errors."
            return
        }
        val resource = state.resources.items.firstOrNull { it.id == resourceId }
        if (resource !is NinePatchAtlasResource) return
        val updatedResource =
            resource.copy(
                split = draft.toSplitList(),
                pad = draft.toPadList(),
            )
        state.resources.items =
            state.resources.items.map { item ->
                if (item.id == resourceId) updatedResource else item
            }
        state.dirty = true
        state.ninePatchEditor.dirty = false
        state.statusMessage = "Applied Nine-patch draft to resource '${resource.name}'. Pack and Save to write atlas output."
        engine.logger.info(TAG) {
            "NinePatch draft applied resource='${resource.name}' split=${updatedResource.split} pad=${updatedResource.pad}"
        }
    }

    private fun applyDraftUpdate(updated: NinePatchDraft) {
        val issues = validateNinePatchDraft(updated)
        state.ninePatchEditor.draft = updated
        state.ninePatchEditor.dirty = true
        state.ninePatchEditor.validationIssues = issues
    }

    companion object {
        private const val TAG = "TextureAtlasNinePatchOps"
    }
}
