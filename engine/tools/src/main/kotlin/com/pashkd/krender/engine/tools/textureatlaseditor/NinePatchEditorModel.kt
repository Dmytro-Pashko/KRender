package com.pashkd.krender.engine.tools.textureatlaseditor

data class NinePatchEditorState(
    var selectedResourceId: String? = null,
    var draft: NinePatchDraft? = null,
    var dirty: Boolean = false,
    var validationIssues: List<NinePatchValidationIssue> = emptyList(),
)

data class NinePatchDraft(
    val sourcePath: String,
    val contentWidth: Int,
    val contentHeight: Int,
    val stretchX: NinePatchSegment,
    val stretchY: NinePatchSegment,
    val paddingX: NinePatchSegment? = null,
    val paddingY: NinePatchSegment? = null,
)

internal fun buildNinePatchDraft(
    resource: NinePatchAtlasResource,
    document: NinePatchDocument?,
): NinePatchDraft? {
    val width = resource.sourceWidth ?: document?.contentWidth ?: return null
    val height = resource.sourceHeight ?: document?.contentHeight ?: return null
    if (width <= 0 || height <= 0) return null

    val splitSegments = splitToSegments(resource.split, width, height)
    val padSegments = padToSegments(resource.pad, width, height)

    if (splitSegments != null) {
        return NinePatchDraft(
            sourcePath = resource.sourcePath,
            contentWidth = width,
            contentHeight = height,
            stretchX = splitSegments.first,
            stretchY = splitSegments.second,
            paddingX = padSegments?.first,
            paddingY = padSegments?.second,
        )
    }

    if (document != null) {
        return buildDraftFromDocument(resource.sourcePath, width, height, document)
    }

    return NinePatchDraft(
        sourcePath = resource.sourcePath,
        contentWidth = width,
        contentHeight = height,
        stretchX = NinePatchSegment(start = 0, length = width),
        stretchY = NinePatchSegment(start = 0, length = height),
    )
}

private fun buildDraftFromDocument(
    sourcePath: String,
    contentWidth: Int,
    contentHeight: Int,
    document: NinePatchDocument,
): NinePatchDraft {
    val stretchX = document.stretchX.firstOrNull() ?: NinePatchSegment(start = 0, length = contentWidth)
    val stretchY = document.stretchY.firstOrNull() ?: NinePatchSegment(start = 0, length = contentHeight)
    return NinePatchDraft(
        sourcePath = sourcePath,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        stretchX = stretchX,
        stretchY = stretchY,
        paddingX = document.paddingX,
        paddingY = document.paddingY,
    )
}

private fun splitToSegments(
    split: List<Int>,
    contentWidth: Int,
    contentHeight: Int,
): Pair<NinePatchSegment, NinePatchSegment>? {
    if (split.size != 4) return null
    val left = split[0]
    val right = split[1]
    val top = split[2]
    val bottom = split[3]
    val stretchWidth = contentWidth - left - right
    val stretchHeight = contentHeight - top - bottom
    if (stretchWidth <= 0 || stretchHeight <= 0) return null
    return NinePatchSegment(start = left, length = stretchWidth) to
        NinePatchSegment(start = top, length = stretchHeight)
}

private fun padToSegments(
    pad: List<Int>,
    contentWidth: Int,
    contentHeight: Int,
): Pair<NinePatchSegment, NinePatchSegment>? {
    if (pad.size != 4) return null
    val left = pad[0]
    val right = pad[1]
    val top = pad[2]
    val bottom = pad[3]
    val padWidth = contentWidth - left - right
    val padHeight = contentHeight - top - bottom
    if (padWidth <= 0 || padHeight <= 0) return null
    return NinePatchSegment(start = left, length = padWidth) to
        NinePatchSegment(start = top, length = padHeight)
}

internal fun NinePatchDraft.toSplitList(): List<Int> =
    listOf(
        stretchX.start,
        contentWidth - (stretchX.start + stretchX.length),
        stretchY.start,
        contentHeight - (stretchY.start + stretchY.length),
    )

internal fun NinePatchDraft.toPadList(): List<Int> {
    val px = paddingX ?: return emptyList()
    val py = paddingY ?: return emptyList()
    return listOf(
        px.start,
        contentWidth - (px.start + px.length),
        py.start,
        contentHeight - (py.start + py.length),
    )
}

internal fun validateNinePatchDraft(draft: NinePatchDraft): List<NinePatchValidationIssue> {
    val issues = mutableListOf<NinePatchValidationIssue>()
    if (draft.contentWidth <= 0 || draft.contentHeight <= 0) {
        issues += NinePatchValidationIssue(NinePatchValidationSeverity.Error, "Content dimensions must be positive.")
        return issues
    }
    validateSegment(draft.stretchX, draft.contentWidth, "Stretch X", required = true, issues)
    validateSegment(draft.stretchY, draft.contentHeight, "Stretch Y", required = true, issues)
    draft.paddingX?.let { validateSegment(it, draft.contentWidth, "Padding X", required = false, issues) }
    draft.paddingY?.let { validateSegment(it, draft.contentHeight, "Padding Y", required = false, issues) }
    if (draft.paddingX == null && draft.paddingY == null) {
        issues += NinePatchValidationIssue(NinePatchValidationSeverity.Warning, "No padding guides are set. Padding will default to content bounds.")
    }
    if (draft.paddingX != null && draft.paddingY == null) {
        issues += NinePatchValidationIssue(NinePatchValidationSeverity.Warning, "Padding X is set but Padding Y is not.")
    }
    if (draft.paddingX == null && draft.paddingY != null) {
        issues += NinePatchValidationIssue(NinePatchValidationSeverity.Warning, "Padding Y is set but Padding X is not.")
    }
    return issues
}

private fun validateSegment(
    segment: NinePatchSegment,
    maxSize: Int,
    label: String,
    required: Boolean,
    issues: MutableList<NinePatchValidationIssue>,
) {
    if (segment.length <= 0) {
        issues +=
            NinePatchValidationIssue(
                if (required) NinePatchValidationSeverity.Error else NinePatchValidationSeverity.Warning,
                "$label length must be positive (got ${segment.length}).",
            )
    }
    if (segment.start < 0) {
        issues += NinePatchValidationIssue(NinePatchValidationSeverity.Error, "$label start must not be negative (got ${segment.start}).")
    }
    if (segment.start + segment.length > maxSize) {
        issues +=
            NinePatchValidationIssue(
                NinePatchValidationSeverity.Error,
                "$label extends beyond content bounds (${segment.start}+${segment.length} > $maxSize).",
            )
    }
}
