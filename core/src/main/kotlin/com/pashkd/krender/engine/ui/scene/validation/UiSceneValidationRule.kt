package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue

/**
 * One focused `.krui` validation rule group.
 *
 * Implementations must be small and deterministic. They should not mutate the
 * document, load files, access LibGDX, or perform editor UI actions.
 */
interface UiSceneValidationRule {
    val id: String

    fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue>
}

/**
 * Composes small `.krui` validation rules into one validation pass.
 */
class UiSceneValidationPipeline(
    private val rules: List<UiSceneValidationRule>,
) {
    fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> =
        rules.flatMap { rule -> rule.validate(context) }

    companion object {
        fun default(): UiSceneValidationPipeline =
            UiSceneValidationPipeline(
                listOf(
                    DocumentMetadataValidator,
                    NodeIdUniquenessValidator,
                    NodeShapeValidator,
                    BindingDefinitionValidator,
                    PlaceholderSyntaxValidator,
                    BindingReferenceValidator,
                    BindingTypeCompatibilityValidator,
                    StyleReferenceValidator,
                    TextureReferenceValidator,
                ),
            )
    }
}
