package com.pashkd.krender.engine.tools.common.ninepatch

enum class NinePatchPreviewType {
    Source,
    StretchTest,
}

enum class NinePatchStretchPreset {
    Actual,
    Button,
    Panel,
    Custom,
}

data class NinePatchStretchState(
    var previewType: NinePatchPreviewType = NinePatchPreviewType.Source,
    var preset: NinePatchStretchPreset = NinePatchStretchPreset.Actual,
    var targetWidth: Int = 160,
    var targetHeight: Int = 48,
    var showSourceGuides: Boolean = true,
    var showDestinationSlices: Boolean = true,
    var showPaddingRect: Boolean = true,
)

data class NinePatchStretchPreview(
    val targetWidth: Int,
    val targetHeight: Int,
    val fixedLeft: Int,
    val fixedRight: Int,
    val fixedTop: Int,
    val fixedBottom: Int,
    val slices: List<NinePatchStretchSlice>,
    val destinationVerticalCuts: List<Int>,
    val destinationHorizontalCuts: List<Int>,
    val paddingRect: NinePatchStretchRect?,
    val warnings: List<String> = emptyList(),
)

data class NinePatchStretchSlice(
    val sourceX: Int,
    val sourceY: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val destinationX: Int,
    val destinationY: Int,
    val destinationWidth: Int,
    val destinationHeight: Int,
)

data class NinePatchStretchRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

fun resolveNinePatchStretchTargetSize(
    stretch: NinePatchStretchState,
    draft: NinePatchDraft,
): Pair<Int, Int> =
    when (stretch.preset) {
        NinePatchStretchPreset.Actual -> draft.contentWidth to draft.contentHeight
        NinePatchStretchPreset.Button -> 160 to 48
        NinePatchStretchPreset.Panel -> 320 to 180
        NinePatchStretchPreset.Custom -> stretch.targetWidth.coerceAtLeast(1) to stretch.targetHeight.coerceAtLeast(1)
    }

fun buildNinePatchStretchPreview(
    draft: NinePatchDraft?,
    stretch: NinePatchStretchState,
): NinePatchStretchPreview? {
    draft ?: return null
    if (draft.contentWidth <= 0 || draft.contentHeight <= 0) {
        return NinePatchStretchPreview(
            targetWidth = 1,
            targetHeight = 1,
            fixedLeft = 0,
            fixedRight = 0,
            fixedTop = 0,
            fixedBottom = 0,
            slices = emptyList(),
            destinationVerticalCuts = emptyList(),
            destinationHorizontalCuts = emptyList(),
            paddingRect = null,
            warnings = listOf("Content size is unknown."),
        )
    }

    val warnings = mutableListOf<String>()
    val stretchX = draft.stretchX
    val stretchY = draft.stretchY
    if (stretchX.length <= 0 || stretchY.length <= 0) {
        warnings += "Split guides must define a non-empty stretch area."
    }
    if (stretchX.start < 0 || stretchY.start < 0) {
        warnings += "Split guides must stay within the source content bounds."
    }
    val rightFixed = draft.contentWidth - (stretchX.start + stretchX.length)
    val bottomFixed = draft.contentHeight - (stretchY.start + stretchY.length)
    if (rightFixed < 0 || bottomFixed < 0) {
        warnings += "Split guides extend past the source content bounds."
    }

    val (requestedWidth, requestedHeight) = resolveNinePatchStretchTargetSize(stretch, draft)
    val safeWidth = requestedWidth.coerceAtLeast(1)
    val safeHeight = requestedHeight.coerceAtLeast(1)
    val leftFixed = stretchX.start.coerceAtLeast(0)
    val topFixed = stretchY.start.coerceAtLeast(0)
    val safeRightFixed = rightFixed.coerceAtLeast(0)
    val safeBottomFixed = bottomFixed.coerceAtLeast(0)
    if (safeWidth < leftFixed + safeRightFixed) {
        warnings += "Target width is smaller than the fixed left and right slices."
    }
    if (safeHeight < topFixed + safeBottomFixed) {
        warnings += "Target height is smaller than the fixed top and bottom slices."
    }

    val destinationColumns = destinationSegments(safeWidth, leftFixed, stretchX.length.coerceAtLeast(0), safeRightFixed)
    val destinationRows = destinationSegments(safeHeight, topFixed, stretchY.length.coerceAtLeast(0), safeBottomFixed)
    val sourceColumns =
        listOf(
            0 to leftFixed,
            leftFixed to stretchX.length.coerceAtLeast(0),
            (leftFixed + stretchX.length).coerceAtMost(draft.contentWidth) to safeRightFixed,
        )
    val sourceRows =
        listOf(
            0 to topFixed,
            topFixed to stretchY.length.coerceAtLeast(0),
            (topFixed + stretchY.length).coerceAtMost(draft.contentHeight) to safeBottomFixed,
        )

    val slices =
        buildList {
            destinationRows.forEachIndexed { rowIndex, (destY, destHeight) ->
                sourceRows[rowIndex].let { (sourceY, sourceHeight) ->
                    if (sourceHeight <= 0 || destHeight <= 0) return@let
                    destinationColumns.forEachIndexed { columnIndex, (destX, destWidth) ->
                        val (sourceX, sourceWidth) = sourceColumns[columnIndex]
                        if (sourceWidth <= 0 || destWidth <= 0) return@forEachIndexed
                        add(
                            NinePatchStretchSlice(
                                sourceX = sourceX,
                                sourceY = sourceY,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                destinationX = destX,
                                destinationY = destY,
                                destinationWidth = destWidth,
                                destinationHeight = destHeight,
                            ),
                        )
                    }
                }
            }
        }

    return NinePatchStretchPreview(
        targetWidth = safeWidth,
        targetHeight = safeHeight,
        fixedLeft = leftFixed,
        fixedRight = safeRightFixed,
        fixedTop = topFixed,
        fixedBottom = safeBottomFixed,
        slices = slices,
        destinationVerticalCuts = destinationColumns.runningCuts(),
        destinationHorizontalCuts = destinationRows.runningCuts(),
        paddingRect = buildPaddingRect(draft, safeWidth, safeHeight, destinationColumns, destinationRows),
        warnings = warnings,
    )
}

private fun destinationSegments(
    targetSize: Int,
    leadingFixed: Int,
    stretchSize: Int,
    trailingFixed: Int,
): List<Pair<Int, Int>> {
    val totalFixed = leadingFixed + trailingFixed
    val (destLeading, destCenter, destTrailing) =
        if (targetSize >= totalFixed) {
            Triple(leadingFixed, targetSize - totalFixed, trailingFixed)
        } else if (totalFixed > 0) {
            val ratio = targetSize.toFloat() / totalFixed.toFloat()
            val scaledLeading = (leadingFixed * ratio).toInt().coerceIn(0, targetSize)
            val scaledTrailing = (targetSize - scaledLeading).coerceAtLeast(0)
            Triple(scaledLeading, 0, scaledTrailing)
        } else {
            Triple(0, targetSize, 0)
        }
    return listOf(
        0 to destLeading,
        destLeading to destCenter,
        destLeading + destCenter to destTrailing,
    )
}

private fun buildPaddingRect(
    draft: NinePatchDraft,
    targetWidth: Int,
    targetHeight: Int,
    destinationColumns: List<Pair<Int, Int>>,
    destinationRows: List<Pair<Int, Int>>,
): NinePatchStretchRect {
    val paddingX = draft.paddingX ?: NinePatchSegment(start = 0, length = draft.contentWidth)
    val paddingY = draft.paddingY ?: NinePatchSegment(start = 0, length = draft.contentHeight)
    val left = mapSourceCoordinateToDestination(paddingX.start, destinationColumns, draft.stretchX.start, draft.stretchX.length, draft.contentWidth)
    val right = mapSourceCoordinateToDestination((paddingX.start + paddingX.length).coerceIn(0, draft.contentWidth), destinationColumns, draft.stretchX.start, draft.stretchX.length, draft.contentWidth)
    val top = mapSourceCoordinateToDestination(paddingY.start, destinationRows, draft.stretchY.start, draft.stretchY.length, draft.contentHeight)
    val bottom = mapSourceCoordinateToDestination((paddingY.start + paddingY.length).coerceIn(0, draft.contentHeight), destinationRows, draft.stretchY.start, draft.stretchY.length, draft.contentHeight)
    return NinePatchStretchRect(
        x = left.coerceIn(0, targetWidth),
        y = top.coerceIn(0, targetHeight),
        width = (right - left).coerceAtLeast(0),
        height = (bottom - top).coerceAtLeast(0),
    )
}

private fun mapSourceCoordinateToDestination(
    coordinate: Int,
    destinationSegments: List<Pair<Int, Int>>,
    stretchStart: Int,
    stretchLength: Int,
    sourceSize: Int,
): Int {
    val leading = stretchStart.coerceAtLeast(0)
    val trailing = (sourceSize - (stretchStart + stretchLength)).coerceAtLeast(0)
    val middleStart = leading
    val middleEnd = (leading + stretchLength).coerceAtLeast(middleStart)
    return when {
        coordinate <= middleStart -> coordinate.coerceAtMost(leading)
        coordinate >= middleEnd -> {
            val trailingProgress = (coordinate - middleEnd).coerceAtLeast(0)
            destinationSegments[2].first + trailingProgress.coerceAtMost(trailing)
        }
        else -> {
            if (stretchLength <= 0) {
                destinationSegments[1].first
            } else {
                val ratio = (coordinate - middleStart).toFloat() / stretchLength.toFloat()
                destinationSegments[1].first + (destinationSegments[1].second * ratio).toInt()
            }
        }
    }
}

private fun List<Pair<Int, Int>>.runningCuts(): List<Int> =
    mapIndexedNotNull { index, segment ->
        if (index == size - 1) null else segment.first + segment.second
    }
