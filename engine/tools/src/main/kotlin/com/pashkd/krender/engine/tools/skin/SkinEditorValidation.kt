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
        if (project.skinFile != null && project.atlasFiles.isEmpty()) {
            return listOf(
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Atlas,
                    message = "No atlas files were discovered next to the skin descriptor.",
                    source = project.skinFile.path,
                ),
            )
        }
        return emptyList()
    }
}

class FontValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> {
        val styleFields = context.loadResult.styleIndex.styles.flatMap(StyleInfo::fields)
        val referencesFonts = styleFields.any { field -> field.name.contains("font", ignoreCase = true) }
        if (referencesFonts && context.loadResult.project?.fontFiles.isNullOrEmpty()) {
            return listOf(
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Font,
                    message = "Styles reference font fields, but no font files were discovered in the skin project.",
                ),
            )
        }
        return emptyList()
    }
}

class DrawableValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> = emptyList()
}

class StyleReferenceValidator : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> = emptyList()
}

class UnusedResourceAnalyzer : SkinValidator {
    override fun validate(context: SkinValidationContext): List<SkinProblem> = emptyList()
}
