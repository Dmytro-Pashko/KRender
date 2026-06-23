package com.pashkd.krender.engine.tools.skin

import java.io.File

interface SkinValidator {
    fun validate(context: SkinValidationContext): List<SkinProblem>
}

data class SkinValidationContext(
    val loadResult: SkinLoadResult,
)

class AtlasValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val project = context.loadResult.project ?: return emptyList()
        val resourceIndex = context.loadResult.resourceIndex
        val drawableReferences =
            context.loadResult.styleIndex.styles
                .flatMap(StyleInfo::resourceReferences)
                .filter { reference -> reference.category == SkinResourceCategory.Drawable }
        val problems = mutableListOf<SkinProblem>()
        if (drawableReferences.isNotEmpty() && project.atlasFiles.isEmpty()) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Atlas,
                    message = "Styles reference drawables, but no atlas files were discovered next to the skin descriptor.",
                    source = project.skinFile?.path,
                    suggestedFix = "Add the skin atlas next to the descriptor or verify drawable resource declarations.",
                )
        }
        project.atlasFiles.forEach { atlasFile ->
            val atlasResource = resourceIndex.atlasFiles.firstOrNull { resource -> resource.source == atlasFile.path }
            if (atlasResource?.details?.get("readable") == "false") {
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Atlas,
                        message = "Atlas '${atlasFile.name}' could not be read for resource inspection.",
                        source = atlasFile.path,
                        suggestedFix = "Verify that the atlas file is readable and uses a supported LibGDX text atlas format.",
                        resourceKey = atlasResource.key,
                    )
                return@forEach
            }

            atlasResource
                ?.details
                ?.get("pages")
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { pageName -> resolveRelativeFile(atlasFile.parentFile, pageName)?.isFile == true }
                .forEach { pageName ->
                    problems +=
                        SkinProblem(
                            severity = SkinProblemSeverity.Warning,
                            category = SkinProblemCategory.Atlas,
                            message = "Atlas '${atlasFile.name}' references missing page texture '$pageName'.",
                            source = atlasFile.path,
                            suggestedFix = "Place '$pageName' next to the atlas or update the atlas page path.",
                            resourceKey = atlasResource?.key,
                        )
                }

            val malformedRegions =
                resourceIndex.atlasRegions
                    .filter { region -> region.source == atlasFile.path }
                    .filterNot(::hasValidRegionBounds)
            malformedRegions.take(MaxAtlasRegionProblems).forEach { region ->
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Atlas,
                        message = "Atlas region '${region.name}' has missing or malformed xy/size metadata.",
                        source = region.source,
                        suggestedFix = "Repack the atlas or verify the region coordinates and size entries.",
                        resourceKey = region.key,
                    )
            }
            val hiddenMalformedRegions = malformedRegions.size - MaxAtlasRegionProblems
            if (hiddenMalformedRegions > 0) {
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Atlas,
                        message = "$hiddenMalformedRegions additional malformed region(s) in '${atlasFile.name}' were omitted.",
                        source = atlasFile.path,
                        suggestedFix = "Repack the atlas and verify its region metadata.",
                        resourceKey = atlasResource?.key,
                    )
            }
        }

        resourceIndex.textures
            .filter { texture -> texture.details["discoveredFile"] == "true" }
            .forEach { texture ->
                val file = texture.source?.substringBefore('#')?.let(::File)
                if (file == null || !file.isFile || !file.canRead() || file.length() <= 0L) {
                    problems +=
                        SkinProblem(
                            severity = SkinProblemSeverity.Warning,
                            category = SkinProblemCategory.Resource,
                            message = "Texture '${texture.name}' is missing, unreadable, or empty.",
                            source = texture.source,
                            suggestedFix = "Restore the texture file or update the skin resource path.",
                            resourceKey = texture.key,
                        )
                }
            }
        return problems
    }

    private fun hasValidRegionBounds(region: SkinResourceInfo): Boolean {
        val xy = region.details["xy"]?.parseIntList(expectedSize = 2)
        val size = region.details["size"]?.parseIntList(expectedSize = 2)
        if (xy != null && size != null && size.all { value -> value > 0 }) return true
        val bounds = region.details["bounds"]?.parseIntList(expectedSize = 4)
        return bounds != null && bounds[2] > 0 && bounds[3] > 0
    }
}

class FontValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val fontReferences =
            context.loadResult.styleIndex.styles
                .flatMap(StyleInfo::resourceReferences)
                .filter { reference -> reference.category == SkinResourceCategory.Font }

        val fonts = context.loadResult.resourceIndex.fonts
        val problems = mutableListOf<SkinProblem>()
        if (fontReferences.isNotEmpty() && fonts.isEmpty()) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "Styles reference fonts, but no font resources were indexed.",
                    suggestedFix = "Declare BitmapFont resources in the skin descriptor.",
                )
        }
        if (fontReferences.isNotEmpty() &&
            context.loadResult.project
                ?.fontFiles
                .isNullOrEmpty()
        ) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "Styles reference fonts, but no font files were discovered next to the skin project.",
                    source =
                        context.loadResult.project
                            ?.rootDirectory
                            ?.path,
                    suggestedFix = "Verify BitmapFont file paths or place the required font files with the skin assets.",
                )
        }
        fonts
            .filter { font ->
                font.referencedBy.isNotEmpty() &&
                    font.details["file"] != null &&
                    font.details["matchedFile"] == "<none>"
            }.forEach { font ->
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Font,
                        message = "Declared font file '${font.details["file"]}' for '${font.name}' was not discovered.",
                        source = font.source,
                        suggestedFix = "Restore the declared font file or update its read-only skin declaration.",
                        resourceKey = font.key,
                    )
            }
        fonts.forEach { font ->
            val matchedFile = font.details["matchedFile"]
            val matchedExtension = font.details["matchedFileExtension"]?.lowercase()
            if (matchedExtension == "fnt" && font.details["fntReadable"] == "false") {
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Font,
                        message = "Bitmap font '${font.name}' matched an unreadable .fnt file.",
                        source = matchedFile ?: font.source,
                        suggestedFix = "Verify that the .fnt file is readable and uses BMFont text format.",
                        resourceKey = font.key,
                    )
            }
            val ukrainianCoverage = font.details["ukrainianGlyphCoverage"]?.substringBefore('/')?.toIntOrNull()
            if (matchedExtension == "fnt" && ukrainianCoverage == 0 && font.referencedBy.isNotEmpty()) {
                val missingGlyphs = font.details["missingUkrainianGlyphs"]?.truncateForMessage(MaxGlyphMetadataLength)
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Warning,
                        category = SkinProblemCategory.Font,
                        message =
                            buildString {
                                append("Bitmap font '${font.name}' has no indexed Ukrainian Cyrillic glyph coverage.")
                                missingGlyphs?.let { append(" Missing: $it") }
                            },
                        source = matchedFile ?: font.source,
                        suggestedFix = "Re-export the BMFont with Ukrainian glyphs if this font is expected to render localized text.",
                        resourceKey = font.key,
                    )
            }
            if (
                matchedFile != null &&
                matchedFile != "<none>" &&
                matchedExtension in UnsupportedFontPreviewExtensions &&
                font.referencedBy.isNotEmpty()
            ) {
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Info,
                        category = SkinProblemCategory.Font,
                        message = "Referenced font '${font.name}' uses .$matchedExtension, which is indexed but not supported for visual preview.",
                        source = matchedFile,
                        suggestedFix = "Use a text .fnt BitmapFont for preview support, or inspect this font through its runtime skin.",
                        resourceKey = font.key,
                    )
            }
        }
        return problems
    }
}

class ColorValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.resourceIndex.colors.flatMap { color ->
            val value = color.details["value"] ?: color.details["hex"]
            val channelNames = listOf("r", "g", "b", "a")
            val presentChannels = channelNames.filter(color.details::containsKey)
            when {
                value != null && !SimpleHexColor.matches(value) ->
                    listOf(
                        colorProblem(
                            color,
                            "Color '${color.name}' uses unrecognized value '$value'.",
                            "Use RRGGBB, RRGGBBAA, #RRGGBB, or #RRGGBBAA.",
                        ),
                    )

                value != null -> emptyList()
                presentChannels.isEmpty() ->
                    listOf(
                        colorProblem(
                            color,
                            "Color '${color.name}' does not define a supported hex value or r/g/b channels.",
                            "Use a simple hex color or an object with r, g, b, and optional a channels.",
                        ),
                    )

                else -> {
                    val missing = listOf("r", "g", "b").filterNot(color.details::containsKey)
                    val invalid =
                        presentChannels.filter { channel ->
                            color.details[channel]?.toDoubleOrNull()?.let { value -> value !in 0.0..1.0 } ?: true
                        }
                    buildList {
                        if (missing.isNotEmpty()) {
                            add(
                                colorProblem(
                                    color,
                                    "Color '${color.name}' is missing channel(s): ${missing.joinToString()}.",
                                    "Provide r, g, and b channels; a is optional.",
                                ),
                            )
                        }
                        if (invalid.isNotEmpty()) {
                            add(
                                colorProblem(
                                    color,
                                    "Color '${color.name}' has invalid or out-of-range channel(s): ${invalid.joinToString()}.",
                                    "Use numeric channel values from 0.0 through 1.0.",
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun colorProblem(
        color: SkinResourceInfo,
        message: String,
        suggestedFix: String,
    ): SkinProblem =
        SkinProblem(
            severity = SkinProblemSeverity.Warning,
            category = SkinProblemCategory.Color,
            message = message,
            source = color.source,
            suggestedFix = suggestedFix,
            resourceKey = color.key,
        )
}

class DuplicateResourceNameValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.resourceIndex.resources
            .filter { resource -> resource.resolved && resource.category in DrawableResolutionCategories }
            .groupBy { resource -> resource.name.lowercase() }
            .values
            .mapNotNull { resources ->
                val categories = resources.map(SkinResourceInfo::category).distinct()
                val references = resources.flatMap(SkinResourceInfo::referencedBy).distinct()
                if (categories.size < 2 || references.isEmpty()) return@mapNotNull null
                val representative = resources.first()
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Resource,
                    message =
                        "Resource name '${representative.name}' is ambiguous across " +
                            "${categories.joinToString { category -> category.name }}.",
                    source = references.firstOrNull(),
                    suggestedFix = "Use distinct names for drawable-resolvable resources to make style reference resolution unambiguous.",
                    resourceKey = representative.key,
                )
            }
}

class DrawableValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.styleIndex.styles.flatMap { style ->
            val fieldNames = style.fields.map { field -> field.name.lowercase() }.toSet()
            when (style.type) {
                "TextButtonStyle" ->
                    missingFieldsProblem(
                        style = style,
                        fieldNames = fieldNames,
                        requiredAny = listOf("up", "down"),
                        message = "Text button style should define at least one pressed or resting drawable: up or down.",
                    )

                "ButtonStyle",
                "ImageButtonStyle",
                ->
                    missingFieldsProblem(
                        style = style,
                        fieldNames = fieldNames,
                        requiredAny = listOf("up", "down", "over", "disabled"),
                        message = "Button style defines none of the common drawable states: up, down, over, disabled.",
                    )

                "CheckBoxStyle" ->
                    missingRequiredFields(
                        style = style,
                        fieldNames = fieldNames,
                        required = listOf("checkboxon", "checkboxoff"),
                        displayNames = mapOf("checkboxon" to "checkboxOn", "checkboxoff" to "checkboxOff"),
                    )

                "TextFieldStyle",
                "TextAreaStyle",
                ->
                    missingRequiredFields(
                        style = style,
                        fieldNames = fieldNames,
                        required = listOf("font", "fontcolor", "cursor", "selection"),
                        displayNames = mapOf("fontcolor" to "fontColor"),
                    )

                "WindowStyle" ->
                    buildList {
                        if ("titlefont" !in fieldNames) {
                            add(warning(style, "Window style is missing required titleFont.", "Add a titleFont reference."))
                        }
                        if ("background" !in fieldNames) {
                            add(info(style, "Window style has no background drawable.", "Add a background if the window should render a frame."))
                        }
                    }

                "ScrollPaneStyle" -> {
                    val knobFields = setOf("vscrollknob", "hscrollknob")
                    if (fieldNames.none(knobFields::contains)) {
                        listOf(info(style, "Scroll pane style does not define a vertical or horizontal scroll knob."))
                    } else {
                        emptyList()
                    }
                }

                else -> emptyList()
            }
        }

    private fun missingFieldsProblem(
        style: StyleInfo,
        fieldNames: Set<String>,
        requiredAny: List<String>,
        message: String,
    ): List<SkinProblem> =
        if (requiredAny.none(fieldNames::contains)) {
            listOf(warning(style, message))
        } else {
            emptyList()
        }

    private fun missingRequiredFields(
        style: StyleInfo,
        fieldNames: Set<String>,
        required: List<String>,
        displayNames: Map<String, String> = emptyMap(),
    ): List<SkinProblem> {
        val missing = required.filterNot(fieldNames::contains)
        if (missing.isEmpty()) return emptyList()
        val labels = missing.map { field -> displayNames[field] ?: field }
        return listOf(
            warning(
                style,
                "${style.type.removeSuffix("Style")} style is missing field(s): ${labels.joinToString()}.",
                "Add the missing style fields or verify the intended fallback behavior.",
            ),
        )
    }

    private fun warning(
        style: StyleInfo,
        message: String,
        suggestedFix: String? = null,
    ): SkinProblem = styleProblem(SkinProblemSeverity.Warning, style, message, suggestedFix)

    private fun info(
        style: StyleInfo,
        message: String,
        suggestedFix: String? = null,
    ): SkinProblem = styleProblem(SkinProblemSeverity.Info, style, message, suggestedFix)

    private fun styleProblem(
        severity: SkinProblemSeverity,
        style: StyleInfo,
        message: String,
        suggestedFix: String?,
    ): SkinProblem =
        SkinProblem(
            severity = severity,
            category = SkinProblemCategory.Drawable,
            message = "${style.displayName}: $message",
            source = style.displayName,
            suggestedFix = suggestedFix,
            styleKey = style.key,
        )
}

class StyleReferenceValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.styleIndex.styles.flatMap { style ->
            style.fields.mapNotNull { field ->
                val reference = field.reference ?: return@mapNotNull null
                val expectedCategory = reference.category ?: return@mapNotNull null
                if (reference.resolved || reference.name.isBlank()) return@mapNotNull null
                SkinProblem(
                    severity = SkinProblemSeverity.Error,
                    category = SkinProblemCategory.Style,
                    message =
                        "${style.displayName}.${field.name} references missing " +
                            "${expectedCategory.name.lowercase()} '${reference.name}'.",
                    source = reference.source,
                    suggestedFix = "Declare '${reference.name}' as a ${expectedCategory.name.lowercase()} resource or update the style reference.",
                    styleKey = style.key,
                    resourceKey = SkinResourceKey(category = expectedCategory, name = reference.name),
                )
            }
        }
}

class UnusedResourceAnalyzer : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val unusedResources =
            context.loadResult.resourceIndex.resources
                .asSequence()
                .filter { resource -> resource.details["origin"] == "skin" }
                .filter { resource -> resource.category !in ExcludedUnusedCategories }
                .filter { resource -> resource.referencedBy.isEmpty() }
                .toList()
        val visible = unusedResources.take(MaxUnusedResourceProblems)
        return buildList {
            visible.forEach { resource ->
                add(
                    SkinProblem(
                        severity = SkinProblemSeverity.Info,
                        category = SkinProblemCategory.Resource,
                        message = "${resource.category.name} resource '${resource.name}' is not referenced by an indexed style.",
                        source = resource.source,
                        resourceKey = resource.key,
                    ),
                )
            }
            val hiddenCount = unusedResources.size - visible.size
            if (hiddenCount > 0) {
                add(
                    SkinProblem(
                        severity = SkinProblemSeverity.Info,
                        category = SkinProblemCategory.Resource,
                        message = "$hiddenCount additional unused top-level skin resource(s) were omitted.",
                        suggestedFix = "Use the resource browser to inspect the remaining unreferenced resources.",
                    ),
                )
            }
        }
    }
}

fun List<SkinProblem>.sortedForDisplay(): List<SkinProblem> =
    sortedWith(
        compareBy<SkinProblem>(
            { problem ->
                when (problem.severity) {
                    SkinProblemSeverity.Error -> 0
                    SkinProblemSeverity.Warning -> 1
                    SkinProblemSeverity.Info -> 2
                }
            },
            { problem -> problem.category.name },
            { problem -> problem.message },
        ),
    )

private fun resolveRelativeFile(
    directory: File?,
    path: String,
): File? {
    val file = File(path)
    return when {
        file.isAbsolute -> file
        directory != null -> File(directory, path)
        else -> null
    }
}

private fun String.parseIntList(expectedSize: Int): List<Int>? {
    val values = split(',').map { part -> part.trim().toIntOrNull() ?: return null }
    return values.takeIf { it.size >= expectedSize }
}

private fun String.truncateForMessage(maxLength: Int): String = if (length <= maxLength) this else take(maxLength).trimEnd() + "..."

private val SimpleHexColor = Regex("^#?(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
private val DrawableResolutionCategories =
    setOf(
        SkinResourceCategory.Drawable,
        SkinResourceCategory.AtlasRegion,
        SkinResourceCategory.Texture,
    )
private val ExcludedUnusedCategories =
    setOf(
        SkinResourceCategory.Atlas,
        SkinResourceCategory.AtlasRegion,
        SkinResourceCategory.Unknown,
    )
private val UnsupportedFontPreviewExtensions = setOf("ttf", "otf")
private const val MaxGlyphMetadataLength = 96
private const val MaxAtlasRegionProblems = 25
private const val MaxUnusedResourceProblems = 25
