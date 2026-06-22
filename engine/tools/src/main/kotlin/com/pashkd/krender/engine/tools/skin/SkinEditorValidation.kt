package com.pashkd.krender.engine.tools.skin

interface SkinValidator {
    fun validate(context: SkinValidationContext): List<SkinProblem>
}

data class SkinValidationContext(
    val loadResult: SkinLoadResult,
)

class AtlasValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val project = context.loadResult.project ?: return emptyList()
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
            val regionCount =
                context.loadResult.resourceIndex.atlasRegions.count { region ->
                    region.source == atlasFile.path
                }
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Info,
                    category = SkinProblemCategory.Atlas,
                    message = "Discovered atlas '${atlasFile.name}' with $regionCount probable region(s).",
                    source = atlasFile.path,
                )
        }
        return problems
    }
}

class FontValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val fontReferences =
            context.loadResult.styleIndex.styles
                .flatMap(StyleInfo::resourceReferences)
                .filter { reference -> reference.category == SkinResourceCategory.Font }
        if (fontReferences.isEmpty()) return emptyList()

        val fonts = context.loadResult.resourceIndex.fonts
        val problems = mutableListOf<SkinProblem>()
        if (fonts.isEmpty()) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "Styles reference fonts, but no font resources were indexed.",
                    suggestedFix = "Declare BitmapFont resources in the skin descriptor.",
                )
        } else if (fonts.none { font -> font.name.equals("default", ignoreCase = true) || font.name.contains("default-font", ignoreCase = true) }) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "No default font-like resource was found.",
                    suggestedFix = "Verify that the intended default font is declared and referenced by default styles.",
                )
        }
        if (context.loadResult.project?.fontFiles.isNullOrEmpty()) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "Styles reference fonts, but no font files were discovered next to the skin project.",
                    source = context.loadResult.project?.rootDirectory?.path,
                    suggestedFix = "Verify BitmapFont file paths or place the required font files with the skin assets.",
                )
        }
        return problems
    }
}

class DrawableValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.styleIndex.styles.flatMap { style ->
            val fieldNames = style.fields.map { field -> field.name.lowercase() }.toSet()
            when (style.type) {
                "ButtonStyle",
                "TextButtonStyle",
                "ImageButtonStyle",
                "CheckBoxStyle",
                -> {
                    val stateFields = setOf("up", "down", "over", "disabled")
                    if (fieldNames.none(stateFields::contains)) {
                        listOf(
                            warning(
                                style = style,
                                message = "Button style defines none of the common drawable states: up, down, over, disabled.",
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }

                "TextFieldStyle",
                "TextAreaStyle",
                -> {
                    val missing = listOf("cursor", "selection").filterNot(fieldNames::contains)
                    missing.takeIf(List<String>::isNotEmpty)?.let {
                        listOf(
                            warning(
                                style = style,
                                message = "Text field style is missing drawable field(s): ${it.joinToString()}.",
                            ),
                        )
                    }.orEmpty()
                }

                "ScrollPaneStyle" -> {
                    val knobFields = setOf("vscrollknob", "hscrollknob")
                    if (fieldNames.none(knobFields::contains)) {
                        listOf(
                            warning(
                                style = style,
                                message = "Scroll pane style does not define a vertical or horizontal scroll knob.",
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }

                else -> emptyList()
            }
        }

    private fun warning(
        style: StyleInfo,
        message: String,
    ): SkinProblem =
        SkinProblem(
            severity = SkinProblemSeverity.Warning,
            category = SkinProblemCategory.Drawable,
            message = "${style.displayName}: $message",
            source = style.displayName,
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
                )
            }
        }
}

class UnusedResourceAnalyzer : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> =
        context.loadResult.resourceIndex.resources
            .asSequence()
            .filter { resource -> resource.details["origin"] == "skin" }
            .filter { resource -> resource.category != SkinResourceCategory.AtlasRegion }
            .filter { resource -> resource.referencedBy.isEmpty() }
            .map { resource ->
                SkinProblem(
                    severity = SkinProblemSeverity.Info,
                    category = SkinProblemCategory.Resource,
                    message = "${resource.category.name} resource '${resource.name}' is not referenced by an indexed style.",
                    source = resource.source,
                )
            }
            .toList()
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
