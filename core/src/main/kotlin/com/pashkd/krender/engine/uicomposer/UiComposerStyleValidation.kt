package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue

/**
 * Validates `.krui` style and background references against the currently inspected Skin snapshot.
 *
 * This function belongs to editor style picking and UiComposer diagnostics, not
 * the shared `.krui` schema validator or runtime UI builder. It exists because
 * the editor needs soft warnings for missing Skin names even when the document
 * is still saveable and preview rebuilds may fail independently. It
 * intentionally does not load or edit Skin files itself, block saving, repair
 * documents, introduce asset ids, or change runtime Woolboy UI behavior.
 */
fun validateStyleReferences(
    document: UiSceneDocument,
    skinMetadata: UiComposerSkinMetadata?,
): List<UiSceneValidationIssue> {
    if (skinMetadata == null) return emptyList()
    if (skinMetadata.loadError != null) {
        return listOf(
            UiSceneValidationIssue(
                nodeId = null,
                message = "Skin '${document.skin}' could not be loaded: ${skinMetadata.loadError}",
            ),
        )
    }

    val issues = mutableListOf<UiSceneValidationIssue>()
    collectStyleReferenceIssues(document.root, skinMetadata, issues)
    return issues
}

private fun collectStyleReferenceIssues(
    node: UiSceneNode,
    skinMetadata: UiComposerSkinMetadata,
    issues: MutableList<UiSceneValidationIssue>,
) {
    val nodeId = node.id.takeIf(String::isNotBlank)
    val styleName = node.style?.takeIf(String::isNotBlank)
    val backgroundName = node.background?.takeIf(String::isNotBlank)

    when (node.type) {
        UiSceneNodeType.Label -> {
            if (styleName != null && styleName !in skinMetadata.labelStyles) {
                issues += UiSceneValidationIssue(nodeId, "Label style '$styleName' was not found in Skin '${skinMetadata.skinPath}'.")
            }
        }

        UiSceneNodeType.TextButton -> {
            if (styleName != null && styleName !in skinMetadata.textButtonStyles) {
                issues += UiSceneValidationIssue(
                    nodeId,
                    "TextButton style '$styleName' was not found in Skin '${skinMetadata.skinPath}'.",
                )
            }
        }

        UiSceneNodeType.ProgressBar -> {
            if (styleName != null && styleName !in skinMetadata.progressBarStyles) {
                issues += UiSceneValidationIssue(
                    nodeId,
                    "ProgressBar style '$styleName' was not found in Skin '${skinMetadata.skinPath}'.",
                )
            }
        }

        UiSceneNodeType.Stack,
        UiSceneNodeType.Table,
        UiSceneNodeType.Container,
        UiSceneNodeType.Image,
        UiSceneNodeType.Space,
            -> Unit
    }

    if (backgroundName != null && backgroundName !in skinMetadata.drawables) {
        issues += UiSceneValidationIssue(
            nodeId,
            "Background drawable '$backgroundName' was not found in Skin '${skinMetadata.skinPath}'.",
        )
    }

    node.children.forEach { child ->
        // Style diagnostics walk the whole `.krui` tree because unresolved names can live outside the current selection.
        collectStyleReferenceIssues(child, skinMetadata, issues)
    }
}
