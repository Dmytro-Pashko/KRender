package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object NodeShapeValidator : UiSceneValidationRule {
    override val id: String = "NodeShapeValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> = context.nodes.flatMap(::validateNode)

    private fun validateNode(node: UiSceneNode): List<UiSceneValidationIssue> {
        val nodeId = node.id.takeIf(String::isNotBlank)
        val issues = mutableListOf<UiSceneValidationIssue>()
        if (node.type.isLeaf() && node.children.isNotEmpty()) {
            issues +=
                error(
                    code = UiSceneValidationCode.LeafNodeHasChildren,
                    message = "${node.type} is a leaf widget and must not define children.",
                    nodeId = nodeId,
                    fieldName = "children",
                )
        }
        if (node.type == UiSceneNodeType.Container && node.children.size > 1) {
            issues +=
                warning(
                    code = UiSceneValidationCode.ContainerHasMultipleChildren,
                    message = "Container should define at most one child in the `.krui` MVP.",
                    nodeId = nodeId,
                    fieldName = "children",
                )
        }
        if (node.type == UiSceneNodeType.ProgressBar) {
            validateProgressBar(node, nodeId, issues)
        }
        return issues
    }

    private fun validateProgressBar(
        node: UiSceneNode,
        nodeId: String?,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        if (node.value == null && node.valueBinding.isNullOrBlank()) {
            issues +=
                warning(
                    code = UiSceneValidationCode.MissingProgressBarValue,
                    message = "ProgressBar should define either value or valueBinding.",
                    nodeId = nodeId,
                    fieldName = "valueBinding",
                )
        }
        if (node.max <= node.min) {
            issues +=
                error(
                    code = UiSceneValidationCode.InvalidProgressBarRange,
                    message = "ProgressBar max must be greater than min.",
                    nodeId = nodeId,
                    fieldName = "max",
                )
        }
        if (node.step <= 0f) {
            issues +=
                error(
                    code = UiSceneValidationCode.InvalidProgressBarStep,
                    message = "ProgressBar step must be positive.",
                    nodeId = nodeId,
                    fieldName = "step",
                )
        }
    }
}

private fun UiSceneNodeType.isLeaf(): Boolean =
    this == UiSceneNodeType.Label ||
        this == UiSceneNodeType.TextButton ||
        this == UiSceneNodeType.Image ||
        this == UiSceneNodeType.ProgressBar ||
        this == UiSceneNodeType.Space
