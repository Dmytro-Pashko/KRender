package com.pashkd.krender.engine.ui.scene

/**
 * One validation warning or structural issue found in a `.krui` document.
 *
 * Validation belongs to the shared UI pipeline so runtime loading and the future
 * UiComposerScene can report the same document problems. [nodeId] is null for
 * document-level issues.
 */
data class UiSceneValidationIssue(
    val nodeId: String?,
    val message: String,
)

/**
 * Minimal validator for the `.krui` MVP schema.
 *
 * The validator documents and enforces the current limitations: only a small
 * Scene2D widget subset is supported, styles must already exist in the Skin, asset
 * references are project-relative paths, Skin editing is out of scope, arbitrary
 * Actor serialization is unsupported, and no editor UI exists yet.
 */
class UiSceneValidator {
    /**
     * Returns validation issues without throwing, so runtime and future editor code
     * can choose whether to warn, block loading, or offer repairs.
     */
    fun validate(document: UiSceneDocument): List<UiSceneValidationIssue> {
        val issues = mutableListOf<UiSceneValidationIssue>()
        if (document.schemaVersion != UiSceneDocument.CurrentSchemaVersion) {
            issues += UiSceneValidationIssue(
                nodeId = null,
                message = "Unsupported schemaVersion ${document.schemaVersion}; expected ${UiSceneDocument.CurrentSchemaVersion}.",
            )
        }
        if (document.id.isBlank()) {
            issues += UiSceneValidationIssue(null, "Document id must not be blank.")
        }
        if (document.skin.isBlank()) {
            issues += UiSceneValidationIssue(null, "Skin path must not be blank.")
        }
        if (!document.root.type.isContainer()) {
            issues += UiSceneValidationIssue(
                nodeId = document.root.id.takeIf(String::isNotBlank),
                message = "Root node should be Stack, Table, or Container.",
            )
        }

        collectNodeIssues(document.root, mutableSetOf(), issues)
        return issues
    }

    private fun collectNodeIssues(
        node: UiSceneNode,
        seenIds: MutableSet<String>,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        val nodeId = node.id.takeIf(String::isNotBlank)
        if (node.id.isBlank()) {
            issues += UiSceneValidationIssue(null, "Node id must not be blank.")
        } else if (!seenIds.add(node.id)) {
            issues += UiSceneValidationIssue(node.id, "Node id '${node.id}' is duplicated within this document.")
        }

        validateNodeShape(node, nodeId, issues)
        node.children.forEach { child -> collectNodeIssues(child, seenIds, issues) }
    }

    private fun validateNodeShape(
        node: UiSceneNode,
        nodeId: String?,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        when (node.type) {
            UiSceneNodeType.Label -> {
                if (node.text == null) {
                    issues += UiSceneValidationIssue(nodeId, "Label should define text, even if the text is blank.")
                }
                warnForLeafChildren(node, nodeId, issues)
            }

            UiSceneNodeType.TextButton -> {
                if (node.text.isNullOrBlank()) {
                    issues += UiSceneValidationIssue(nodeId, "TextButton should define visible text.")
                }
                if (node.action.isNullOrBlank()) {
                    issues += UiSceneValidationIssue(nodeId, "TextButton should define an action string.")
                }
                warnForLeafChildren(node, nodeId, issues)
            }

            UiSceneNodeType.Image -> {
                if (node.texture.isNullOrBlank()) {
                    issues += UiSceneValidationIssue(nodeId, "Image should define a project-relative texture path.")
                }
                warnForLeafChildren(node, nodeId, issues)
            }

            UiSceneNodeType.ProgressBar -> {
                if (node.value == null && node.valueBinding.isNullOrBlank()) {
                    issues += UiSceneValidationIssue(nodeId, "ProgressBar should define either value or valueBinding.")
                }
                if (node.max <= node.min) {
                    issues += UiSceneValidationIssue(nodeId, "ProgressBar max must be greater than min.")
                }
                if (node.step <= 0f) {
                    issues += UiSceneValidationIssue(nodeId, "ProgressBar step must be positive.")
                }
                warnForLeafChildren(node, nodeId, issues)
            }

            UiSceneNodeType.Space -> warnForLeafChildren(node, nodeId, issues)
            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
                -> Unit
        }
    }

    private fun warnForLeafChildren(
        node: UiSceneNode,
        nodeId: String?,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        if (node.children.isNotEmpty()) {
            issues += UiSceneValidationIssue(
                nodeId,
                "${node.type} is a leaf widget in the `.krui` MVP and should not define children.",
            )
        }
    }

    private fun UiSceneNodeType.isContainer(): Boolean =
        this == UiSceneNodeType.Stack || this == UiSceneNodeType.Table || this == UiSceneNodeType.Container
}
