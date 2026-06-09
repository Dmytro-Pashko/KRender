package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.error

object BindingReferenceValidator : UiSceneValidationRule {
    override val id: String = "BindingReferenceValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> =
        collectBindingReferences(context.document)
            .filter { reference -> reference.key !in context.bindingDefinitionsByKey }
            .map { reference ->
                error(
                    code = UiSceneValidationCode.UnknownBindingKey,
                    message = unknownBindingMessage(reference),
                    nodeId = reference.nodeId.takeIf(String::isNotBlank),
                    fieldName = reference.fieldName,
                    bindingKey = reference.key,
                )
            }
}

fun unknownBindingMessage(reference: UiSceneBindingReference): String =
    if (reference.placeholderSyntax) {
        val guidance = when (reference.fieldName) {
            "texture" -> "Add a binding definition or use a static texture path."
            else -> "Add a binding definition or fix the placeholder."
        }
        "Unknown binding key '${reference.key}' in ${reference.fieldName}. $guidance"
    } else {
        "Unknown valueBinding key '${reference.key}'. Add a binding definition or clear valueBinding."
    }
