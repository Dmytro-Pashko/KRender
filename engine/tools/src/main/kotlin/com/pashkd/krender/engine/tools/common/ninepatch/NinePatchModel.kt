package com.pashkd.krender.engine.tools.common.ninepatch

data class NinePatchSegment(
    val start: Int,
    val length: Int,
) {
    val endInclusive: Int get() = start + length - 1
}

enum class NinePatchValidationSeverity {
    Warning,
    Error,
}

data class NinePatchValidationIssue(
    val severity: NinePatchValidationSeverity,
    val message: String,
)

data class NinePatchDocument(
    val sourcePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val contentWidth: Int,
    val contentHeight: Int,
    val stretchX: List<NinePatchSegment>,
    val stretchY: List<NinePatchSegment>,
    val paddingX: NinePatchSegment?,
    val paddingY: NinePatchSegment?,
    val issues: List<NinePatchValidationIssue> = emptyList(),
    val readable: Boolean = true,
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

data class NinePatchPixelData(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    fun pixelAt(
        x: Int,
        y: Int,
    ): Int = pixels[y * width + x]
}

interface NinePatchPixelReader {
    fun read(path: String): NinePatchPixelData
}

fun buildNinePatchDraft(
    sourcePath: String,
    contentWidth: Int,
    contentHeight: Int,
    split: List<Int> = emptyList(),
    pad: List<Int> = emptyList(),
    document: NinePatchDocument? = null,
): NinePatchDraft? {
    if (contentWidth <= 0 || contentHeight <= 0) return null
    val splitSegments = splitToSegments(split, contentWidth, contentHeight)
    val padSegments = padToSegments(pad, contentWidth, contentHeight)
    if (splitSegments != null) {
        return NinePatchDraft(
            sourcePath = sourcePath,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            stretchX = splitSegments.first,
            stretchY = splitSegments.second,
            paddingX = padSegments?.first,
            paddingY = padSegments?.second,
        )
    }
    if (document != null) {
        return buildDraftFromDocument(sourcePath, contentWidth, contentHeight, document)
    }
    return NinePatchDraft(
        sourcePath = sourcePath,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        stretchX = NinePatchSegment(start = 0, length = contentWidth),
        stretchY = NinePatchSegment(start = 0, length = contentHeight),
    )
}

fun NinePatchDraft.toSplitList(): List<Int> =
    listOf(
        stretchX.start,
        contentWidth - (stretchX.start + stretchX.length),
        stretchY.start,
        contentHeight - (stretchY.start + stretchY.length),
    )

fun NinePatchDraft.toPadList(): List<Int> {
    val px = paddingX ?: return emptyList()
    val py = paddingY ?: return emptyList()
    return listOf(
        px.start,
        contentWidth - (px.start + px.length),
        py.start,
        contentHeight - (py.start + py.length),
    )
}

fun validateNinePatchDraft(draft: NinePatchDraft): List<NinePatchValidationIssue> {
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

fun isNinePatchTexturePath(path: String): Boolean = path.endsWith(".9.png", ignoreCase = true)

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
