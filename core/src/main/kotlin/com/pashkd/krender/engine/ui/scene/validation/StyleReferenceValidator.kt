package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.warning

object StyleReferenceValidator : UiSceneValidationRule {
    override val id: String = "StyleReferenceValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> {
        val metadata = context.skinMetadata ?: return emptyList()
        metadata.loadError?.let { loadError ->
            return listOf(
                warning(
                    code = UiSceneValidationCode.MissingStyle,
                    message = "Skin '${context.document.skin}' could not be loaded: $loadError",
                    fieldName = "skin",
                ),
            )
        }
        return context.nodes.flatMap { node -> validateNode(node, metadata) }
    }

    private fun validateNode(
        node: UiSceneNode,
        metadata: UiSceneSkinValidationMetadata,
    ): List<UiSceneValidationIssue> {
        val nodeId = node.id.takeIf(String::isNotBlank)
        val issues = mutableListOf<UiSceneValidationIssue>()
        val styleName = node.style?.takeIf(String::isNotBlank)
        when (node.type) {
            UiSceneNodeType.Label -> {
                if (styleName != null && styleName !in metadata.labelStyles) {
                    issues += missingStyle(nodeId, styleName, "Label", "style")
                }
            }

            UiSceneNodeType.TextButton -> {
                if (styleName != null && styleName !in metadata.textButtonStyles) {
                    issues += missingStyle(nodeId, styleName, "TextButton", "style")
                }
            }

            UiSceneNodeType.ProgressBar -> {
                if (styleName != null && styleName !in metadata.progressBarStyles) {
                    issues += missingStyle(nodeId, styleName, "ProgressBar", "style")
                }
            }

            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
            UiSceneNodeType.Image,
            UiSceneNodeType.Space,
                -> Unit
        }
        val backgroundName = node.background?.takeIf(String::isNotBlank)
        if (backgroundName != null && backgroundName !in metadata.drawables) {
            issues += warning(
                code = UiSceneValidationCode.MissingBackgroundDrawable,
                message = "Background drawable '$backgroundName' was not found in Skin.",
                nodeId = nodeId,
                fieldName = "background",
            )
        }
        return issues
    }

    private fun missingStyle(
        nodeId: String?,
        styleName: String,
        widgetName: String,
        fieldName: String,
    ): UiSceneValidationIssue =
        warning(
            code = UiSceneValidationCode.MissingStyle,
            message = "$widgetName style '$styleName' was not found in Skin.",
            nodeId = nodeId,
            fieldName = fieldName,
        )
}
