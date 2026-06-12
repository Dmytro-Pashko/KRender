package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.UiSceneValidator
import com.pashkd.krender.engine.ui.scene.validation.UiSceneSkinValidationMetadata

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
    val validationMetadata = skinMetadata?.toValidationMetadata() ?: return emptyList()
    return UiSceneValidator()
        .validate(document, skinMetadata = validationMetadata)
        .filterStyleIssues()
}

fun UiComposerSkinMetadata.toValidationMetadata(): UiSceneSkinValidationMetadata =
    UiSceneSkinValidationMetadata(
        labelStyles = labelStyles.toSet(),
        textButtonStyles = textButtonStyles.toSet(),
        progressBarStyles = progressBarStyles.toSet(),
        drawables = drawables.toSet(),
        loadError = loadError,
    )
