package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue
import com.pashkd.krender.engine.ui.scene.UiSceneValidator

val BindingValidationCodes: Set<UiSceneValidationCode> =
    setOf(
        UiSceneValidationCode.BlankBindingKey,
        UiSceneValidationCode.DuplicateBindingKey,
        UiSceneValidationCode.InvalidNumberBindingDefault,
        UiSceneValidationCode.BlankTextureBindingDefault,
        UiSceneValidationCode.MissingTextureBindingDefault,
        UiSceneValidationCode.BlankActionBindingDefault,
        UiSceneValidationCode.UnknownBindingKey,
        UiSceneValidationCode.MalformedBindingPlaceholder,
        UiSceneValidationCode.InvalidBindingTypeForField,
    )

val StyleValidationCodes: Set<UiSceneValidationCode> =
    setOf(
        UiSceneValidationCode.MissingStyle,
        UiSceneValidationCode.MissingBackgroundDrawable,
    )

val TextureValidationCodes: Set<UiSceneValidationCode> =
    setOf(
        UiSceneValidationCode.MissingTexture,
        UiSceneValidationCode.NonTextureAsset,
    )

fun List<UiSceneValidationIssue>.filterBindingIssues(): List<UiSceneValidationIssue> = filter { issue -> issue.code in BindingValidationCodes || issue.bindingKey != null }

fun List<UiSceneValidationIssue>.filterStyleIssues(): List<UiSceneValidationIssue> = filter { issue -> issue.code in StyleValidationCodes }

fun List<UiSceneValidationIssue>.filterTextureIssues(): List<UiSceneValidationIssue> = filter { issue -> issue.code in TextureValidationCodes && issue.bindingKey == null }

fun List<UiSceneValidationIssue>.filterDocumentAndNodeIssues(): List<UiSceneValidationIssue> =
    filter { issue ->
        issue.code !in BindingValidationCodes &&
            issue.code !in StyleValidationCodes &&
            issue.code !in TextureValidationCodes
    }

fun formatValidationIssue(issue: UiSceneValidationIssue): String {
    val nodeId = issue.nodeId
    val bindingKey = issue.bindingKey
    val fieldName = issue.fieldName
    val scope =
        when {
            nodeId != null && fieldName != null -> "$nodeId.$fieldName"
            nodeId != null -> nodeId
            bindingKey != null && fieldName != null ->
                "$bindingKey.${
                    fieldName.substringAfterLast(
                        '.',
                    )
                }"

            bindingKey != null -> bindingKey
            fieldName != null -> fieldName
            else -> "document"
        }
    return "${issue.severity} ${issue.code} [$scope] ${issue.message}"
}

fun refreshUiComposerValidationBuckets(
    state: UiComposerState,
    document: com.pashkd.krender.engine.ui.scene.UiSceneDocument,
    validator: UiSceneValidator = UiSceneValidator(),
    includeSkinMetadata: Boolean = true,
    includeTextureMetadata: Boolean = true,
) {
    val allIssues =
        validator.validate(
            document = document,
            skinMetadata = state.skinMetadata?.toValidationMetadata().takeIf { includeSkinMetadata },
            textureMetadata =
                textureValidationMetadata(
                    textureOptions = state.textureOptions,
                    assetTypeByPath = state.textureAssetTypesByPath,
                ).takeIf { includeTextureMetadata },
        )
    state.validationIssues = allIssues.filterDocumentAndNodeIssues()
    state.styleValidationIssues = allIssues.filterStyleIssues()
    state.textureValidationIssues = allIssues.filterTextureIssues()
    state.bindingValidationIssues = allIssues.filterBindingIssues()
    val knownKeys = bindingKeys(document)
    state.missingBindingKeys = missingBindingKeys(document, knownKeys)
}
