package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.tools.common.ninepatch.buildNinePatchDraft as buildCommonNinePatchDraft
import com.pashkd.krender.engine.tools.common.ninepatch.toPadList as commonToPadList
import com.pashkd.krender.engine.tools.common.ninepatch.toSplitList as commonToSplitList
import com.pashkd.krender.engine.tools.common.ninepatch.validateNinePatchDraft as commonValidateNinePatchDraft

data class NinePatchEditorState(
    var selectedResourceId: String? = null,
    var draft: NinePatchDraft? = null,
    var dirty: Boolean = false,
    var validationIssues: List<NinePatchValidationIssue> = emptyList(),
)

typealias NinePatchDraft = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchDraft

internal fun buildNinePatchDraft(
    resource: NinePatchAtlasResource,
    document: NinePatchDocument?,
): NinePatchDraft? {
    val width = resource.sourceWidth ?: document?.contentWidth ?: return null
    val height = resource.sourceHeight ?: document?.contentHeight ?: return null
    return buildCommonNinePatchDraft(
        sourcePath = resource.sourcePath,
        contentWidth = width,
        contentHeight = height,
        split = resource.split,
        pad = resource.pad,
        document = document,
    )
}

internal fun NinePatchDraft.toSplitList(): List<Int> =
    commonToSplitList()

internal fun NinePatchDraft.toPadList(): List<Int> {
    return commonToPadList()
}

internal fun validateNinePatchDraft(draft: NinePatchDraft): List<NinePatchValidationIssue> = commonValidateNinePatchDraft(draft)
