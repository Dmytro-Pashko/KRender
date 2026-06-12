package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object BindingTypeCompatibilityValidator : UiSceneValidationRule {
    override val id: String = "BindingTypeCompatibilityValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> =
        context.nodes.flatMap { node -> validateNode(node, context) }

    private fun validateNode(
        node: UiSceneNode,
        context: UiSceneValidationContext,
    ): List<UiSceneValidationIssue> {
        val issues = mutableListOf<UiSceneValidationIssue>()
        when (node.type) {
            UiSceneNodeType.ProgressBar -> {
                node.valueBinding?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { key ->
                        validateRequiredType(
                            node = node,
                            fieldName = "valueBinding",
                            key = key,
                            requiredType = UiSceneBindingType.Number,
                            context = context,
                            issues = issues,
                            messagePrefix = "ProgressBar.valueBinding must reference Number binding",
                        )
                    }
            }

            UiSceneNodeType.Image -> {
                collectBindingReferences(context.document)
                    .filter { reference -> reference.nodeId == node.id && reference.fieldName == "texture" }
                    .forEach { reference ->
                        validateRequiredType(
                            node = node,
                            fieldName = reference.fieldName,
                            key = reference.key,
                            requiredType = UiSceneBindingType.Texture,
                            context = context,
                            issues = issues,
                            messagePrefix = "Image.texture binding must reference Texture binding",
                        )
                    }
            }

            UiSceneNodeType.TextButton -> {
                collectBindingReferences(context.document)
                    .filter { reference -> reference.nodeId == node.id }
                    .forEach { reference ->
                        when (reference.fieldName) {
                            "action" -> validateRequiredType(
                                node = node,
                                fieldName = reference.fieldName,
                                key = reference.key,
                                requiredType = UiSceneBindingType.Action,
                                context = context,
                                issues = issues,
                                messagePrefix = "TextButton.action binding must reference Action binding",
                            )

                            "text" -> validateTextCompatibleType(
                                node,
                                reference.fieldName,
                                reference.key,
                                context,
                                issues
                            )
                        }
                    }
            }

            UiSceneNodeType.Label -> {
                collectBindingReferences(context.document)
                    .filter { reference -> reference.nodeId == node.id && reference.fieldName == "text" }
                    .forEach { reference ->
                        validateTextCompatibleType(node, reference.fieldName, reference.key, context, issues)
                    }
            }

            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
            UiSceneNodeType.Space,
                -> Unit
        }
        return issues
    }

    private fun validateRequiredType(
        node: UiSceneNode,
        fieldName: String,
        key: String,
        requiredType: UiSceneBindingType,
        context: UiSceneValidationContext,
        issues: MutableList<UiSceneValidationIssue>,
        messagePrefix: String,
    ) {
        val actualType = context.bindingDefinitionsByKey[key]?.type ?: return
        if (actualType != requiredType) {
            issues += error(
                code = UiSceneValidationCode.InvalidBindingTypeForField,
                message = "$messagePrefix, but '$key' is $actualType.",
                nodeId = node.id.takeIf(String::isNotBlank),
                fieldName = fieldName,
                bindingKey = key,
            )
        }
    }

    private fun validateTextCompatibleType(
        node: UiSceneNode,
        fieldName: String,
        key: String,
        context: UiSceneValidationContext,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        val actualType = context.bindingDefinitionsByKey[key]?.type ?: return
        if (actualType == UiSceneBindingType.Texture) {
            issues += warning(
                code = UiSceneValidationCode.InvalidBindingTypeForField,
                message = "${node.type}.$fieldName should not reference Texture binding '$key'.",
                nodeId = node.id.takeIf(String::isNotBlank),
                fieldName = fieldName,
                bindingKey = key,
            )
        }
    }
}
