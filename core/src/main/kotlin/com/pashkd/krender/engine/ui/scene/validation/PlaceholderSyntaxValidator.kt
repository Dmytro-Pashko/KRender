package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object PlaceholderSyntaxValidator : UiSceneValidationRule {
    override val id: String = "PlaceholderSyntaxValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> = context.nodes.flatMap(::validateNode)

    private fun validateNode(node: UiSceneNode): List<UiSceneValidationIssue> {
        val fields =
            when (node.type) {
                UiSceneNodeType.Label,
                UiSceneNodeType.Button,
                UiSceneNodeType.CheckBox,
                UiSceneNodeType.TextField,
                UiSceneNodeType.TextTooltip,
                UiSceneNodeType.Window,
                -> listOf("text" to node.text)
                UiSceneNodeType.TextButton -> listOf("text" to node.text, "action" to node.action)
                UiSceneNodeType.Image -> listOf("texture" to node.texture)
                UiSceneNodeType.Stack,
                UiSceneNodeType.Table,
                UiSceneNodeType.Container,
                UiSceneNodeType.SelectBox,
                UiSceneNodeType.List,
                UiSceneNodeType.ScrollPane,
                UiSceneNodeType.SplitPane,
                UiSceneNodeType.Slider,
                UiSceneNodeType.ProgressBar,
                UiSceneNodeType.Tree,
                UiSceneNodeType.Space,
                -> emptyList()
            }
        return fields.flatMap { (fieldName, value) ->
            findMalformedBindingPlaceholders(value).map { raw ->
                warning(
                    code = UiSceneValidationCode.MalformedBindingPlaceholder,
                    message = "Malformed binding placeholder '$raw' in $fieldName.",
                    nodeId = node.id.takeIf(String::isNotBlank),
                    fieldName = fieldName,
                )
            }
        }
    }
}
