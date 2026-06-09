package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.warning

object PlaceholderSyntaxValidator : UiSceneValidationRule {
    override val id: String = "PlaceholderSyntaxValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> =
        context.nodes.flatMap(::validateNode)

    private fun validateNode(node: UiSceneNode): List<UiSceneValidationIssue> {
        val fields = when (node.type) {
            UiSceneNodeType.Label -> listOf("text" to node.text)
            UiSceneNodeType.TextButton -> listOf("text" to node.text, "action" to node.action)
            UiSceneNodeType.Image -> listOf("texture" to node.texture)
            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
            UiSceneNodeType.ProgressBar,
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
