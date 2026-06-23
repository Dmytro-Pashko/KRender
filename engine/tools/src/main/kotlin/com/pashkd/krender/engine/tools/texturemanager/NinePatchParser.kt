package com.pashkd.krender.engine.tools.texturemanager

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

class NinePatchParser {
    /**
     * Parses `.9.png` border guides into drawable-space segments and collects
     * validation issues instead of throwing when the image is malformed.
     */
    fun parse(
        sourcePath: String,
        pixelReader: NinePatchPixelReader,
    ): NinePatchDocument {
        val normalizedPath = normalizePath(sourcePath)
        return runCatching {
            val image = pixelReader.read(sourcePath)
            buildDocument(normalizedPath, image)
        }.getOrElse { error ->
            NinePatchDocument(
                sourcePath = normalizedPath,
                imageWidth = 0,
                imageHeight = 0,
                contentWidth = 0,
                contentHeight = 0,
                stretchX = emptyList(),
                stretchY = emptyList(),
                paddingX = null,
                paddingY = null,
                issues =
                    listOf(
                        NinePatchValidationIssue(
                            severity = NinePatchValidationSeverity.Error,
                            message = "Nine-patch image could not be read: ${error.message ?: error::class.simpleName ?: "unknown error"}.",
                        ),
                    ),
                readable = false,
            )
        }
    }

    private fun buildDocument(
        sourcePath: String,
        image: NinePatchPixelData,
    ): NinePatchDocument {
        val issues = mutableListOf<NinePatchValidationIssue>()
        val contentWidth = (image.width - 2).coerceAtLeast(0)
        val contentHeight = (image.height - 2).coerceAtLeast(0)
        if (image.width < 3 || image.height < 3) {
            issues += issue(NinePatchValidationSeverity.Error, "Nine-patch image is too small. Minimum size is 3x3 pixels.")
            return NinePatchDocument(
                sourcePath = sourcePath,
                imageWidth = image.width,
                imageHeight = image.height,
                contentWidth = contentWidth,
                contentHeight = contentHeight,
                stretchX = emptyList(),
                stretchY = emptyList(),
                paddingX = null,
                paddingY = null,
                issues = issues,
                readable = false,
            )
        }

        val top = parseHorizontalBorder(image, y = 0, label = "top", issues = issues)
        val left = parseVerticalBorder(image, x = 0, label = "left", issues = issues)
        val bottom = parseHorizontalBorder(image, y = image.height - 1, label = "bottom", issues = issues)
        val right = parseVerticalBorder(image, x = image.width - 1, label = "right", issues = issues)

        if (top.segments.isEmpty()) {
            issues += issue(NinePatchValidationSeverity.Error, "No horizontal stretch region was found on the top guide.")
        }
        if (left.segments.isEmpty()) {
            issues += issue(NinePatchValidationSeverity.Error, "No vertical stretch region was found on the left guide.")
        }

        val paddingX = selectPaddingSegment(bottom.segments, "bottom", issues)
        val paddingY = selectPaddingSegment(right.segments, "right", issues)
        if (paddingX == null) {
            issues += issue(NinePatchValidationSeverity.Warning, "No horizontal content/padding guide was found on the bottom border.")
        }
        if (paddingY == null) {
            issues += issue(NinePatchValidationSeverity.Warning, "No vertical content/padding guide was found on the right border.")
        }

        validateBounds(top.segments, contentWidth, "horizontal stretch", issues)
        validateBounds(left.segments, contentHeight, "vertical stretch", issues)
        paddingX?.let { validateBounds(listOf(it), contentWidth, "horizontal padding", issues) }
        paddingY?.let { validateBounds(listOf(it), contentHeight, "vertical padding", issues) }

        return NinePatchDocument(
            sourcePath = sourcePath,
            imageWidth = image.width,
            imageHeight = image.height,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            stretchX = top.segments,
            stretchY = left.segments,
            paddingX = paddingX,
            paddingY = paddingY,
            issues = issues,
            readable = issues.none { issue -> issue.severity == NinePatchValidationSeverity.Error },
        )
    }

    private fun parseHorizontalBorder(
        image: NinePatchPixelData,
        y: Int,
        label: String,
        issues: MutableList<NinePatchValidationIssue>,
    ): ParsedGuide {
        val segments = mutableListOf<NinePatchSegment>()
        val invalidPositions = mutableListOf<Int>()
        var segmentStart: Int? = null
        for (x in 1 until image.width - 1) {
            when (classifyPixel(image.pixelAt(x, y))) {
                GuidePixel.Guide -> {
                    if (segmentStart == null) segmentStart = x - 1
                }

                GuidePixel.Invalid -> {
                    invalidPositions += (x - 1)
                    segmentStart = closeHorizontalSegment(segments, segmentStart, x)
                }

                GuidePixel.Transparent -> {
                    segmentStart = closeHorizontalSegment(segments, segmentStart, x)
                }
            }
        }
        segmentStart?.let { start ->
            segments += NinePatchSegment(start = start, length = (image.width - 1) - start - 1)
        }
        if (invalidPositions.isNotEmpty()) {
            issues += issue(
                NinePatchValidationSeverity.Warning,
                "The $label guide contains non-black opaque pixels at drawable positions ${invalidPositions.joinToString(", ")}.",
            )
        }
        return ParsedGuide(segments)
    }

    private fun parseVerticalBorder(
        image: NinePatchPixelData,
        x: Int,
        label: String,
        issues: MutableList<NinePatchValidationIssue>,
    ): ParsedGuide {
        val segments = mutableListOf<NinePatchSegment>()
        val invalidPositions = mutableListOf<Int>()
        var segmentStart: Int? = null
        for (y in 1 until image.height - 1) {
            when (classifyPixel(image.pixelAt(x, y))) {
                GuidePixel.Guide -> {
                    if (segmentStart == null) segmentStart = y - 1
                }

                GuidePixel.Invalid -> {
                    invalidPositions += (y - 1)
                    segmentStart = closeVerticalSegment(segments, segmentStart, y)
                }

                GuidePixel.Transparent -> {
                    segmentStart = closeVerticalSegment(segments, segmentStart, y)
                }
            }
        }
        segmentStart?.let { start ->
            segments += NinePatchSegment(start = start, length = (image.height - 1) - start - 1)
        }
        if (invalidPositions.isNotEmpty()) {
            issues += issue(
                NinePatchValidationSeverity.Warning,
                "The $label guide contains non-black opaque pixels at drawable positions ${invalidPositions.joinToString(", ")}.",
            )
        }
        return ParsedGuide(segments)
    }

    private fun closeHorizontalSegment(
        segments: MutableList<NinePatchSegment>,
        segmentStart: Int?,
        x: Int,
    ): Int? {
        segmentStart?.let { start ->
            segments += NinePatchSegment(start = start, length = (x - 1) - start)
        }
        return null
    }

    private fun closeVerticalSegment(
        segments: MutableList<NinePatchSegment>,
        segmentStart: Int?,
        y: Int,
    ): Int? {
        segmentStart?.let { start ->
            segments += NinePatchSegment(start = start, length = (y - 1) - start)
        }
        return null
    }

    private fun selectPaddingSegment(
        segments: List<NinePatchSegment>,
        borderLabel: String,
        issues: MutableList<NinePatchValidationIssue>,
    ): NinePatchSegment? {
        if (segments.size > 1) {
            issues += issue(
                NinePatchValidationSeverity.Warning,
                "Multiple content/padding guide segments were found on the $borderLabel border; using the first segment only.",
            )
        }
        return segments.firstOrNull()
    }

    private fun validateBounds(
        segments: List<NinePatchSegment>,
        size: Int,
        label: String,
        issues: MutableList<NinePatchValidationIssue>,
    ) {
        segments.filter { segment ->
            segment.length <= 0 || segment.start < 0 || segment.endInclusive >= size
        }.forEach { segment ->
            issues += issue(
                NinePatchValidationSeverity.Warning,
                "$label segment ${segment.start}..${segment.endInclusive} is outside drawable bounds.",
            )
        }
    }

    private fun classifyPixel(pixel: Int): GuidePixel {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha <= 0) return GuidePixel.Transparent
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        return if (red <= BlackThreshold && green <= BlackThreshold && blue <= BlackThreshold) {
            GuidePixel.Guide
        } else {
            GuidePixel.Invalid
        }
    }

    private fun issue(
        severity: NinePatchValidationSeverity,
        message: String,
    ) = NinePatchValidationIssue(severity = severity, message = message)

    private data class ParsedGuide(
        val segments: List<NinePatchSegment>,
    )

    private enum class GuidePixel {
        Transparent,
        Guide,
        Invalid,
    }

    companion object {
        private const val BlackThreshold = 16
    }
}

fun isNinePatchTexturePath(path: String): Boolean = path.endsWith(".9.png", ignoreCase = true)
