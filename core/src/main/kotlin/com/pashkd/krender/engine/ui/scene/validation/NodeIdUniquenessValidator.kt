package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.error

object NodeIdUniquenessValidator : UiSceneValidationRule {
    override val id: String = "NodeIdUniquenessValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> {
        val issues = mutableListOf<UiSceneValidationIssue>()
        context.nodes.forEach { node ->
            if (node.id.isBlank()) {
                issues +=
                    error(
                        code = UiSceneValidationCode.BlankNodeId,
                        message = "Node id must not be blank.",
                        fieldName = "id",
                    )
            }
        }
        context.nodesById
            .filterKeys(String::isNotBlank)
            .filterValues { nodes -> nodes.size > 1 }
            .keys
            .sorted()
            .forEach { duplicateId ->
                issues +=
                    error(
                        code = UiSceneValidationCode.DuplicateNodeId,
                        message = "Node id '$duplicateId' is duplicated within this document.",
                        nodeId = duplicateId,
                        fieldName = "id",
                    )
            }
        return issues
    }
}
