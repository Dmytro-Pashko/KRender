package com.pashkd.krender.engine.ui.scene

import com.pashkd.krender.engine.ui.scene.validation.UiSceneTextureValidationMetadata
import com.pashkd.krender.engine.ui.scene.validation.UiSceneSkinValidationMetadata
import com.pashkd.krender.engine.ui.scene.validation.UiSceneValidationContext
import com.pashkd.krender.engine.ui.scene.validation.UiSceneValidationPipeline

/**
 * Severity of one `.krui` validation issue.
 *
 * Errors represent invalid document states that should block reliable runtime
 * usage. Warnings represent suspicious or unsupported editor/runtime patterns.
 * Info issues document non-blocking notes or hints.
 */
enum class UiSceneValidationSeverity {
    Info,
    Warning,
    Error,
}

/**
 * Stable validation issue code.
 *
 * Codes are intentionally explicit so UiComposer can group/filter issues and
 * future runtime validation can throw clear exceptions with stable identifiers.
 */
enum class UiSceneValidationCode {
    UnsupportedSchemaVersion,
    BlankDocumentId,
    BlankSkinPath,
    InvalidRootType,

    BlankNodeId,
    DuplicateNodeId,
    LeafNodeHasChildren,
    ContainerHasMultipleChildren,
    InvalidProgressBarRange,
    InvalidProgressBarStep,
    MissingProgressBarValue,

    BlankBindingKey,
    DuplicateBindingKey,
    InvalidNumberBindingDefault,
    BlankTextureBindingDefault,
    MissingTextureBindingDefault,
    BlankActionBindingDefault,

    UnknownBindingKey,
    MalformedBindingPlaceholder,
    InvalidBindingTypeForField,

    MissingStyle,
    MissingBackgroundDrawable,
    MissingTexture,
    NonTextureAsset,
}

/**
 * One validation issue found in a `.krui` document.
 *
 * [nodeId] is present for node-scoped issues.
 * [bindingKey] is present for binding-definition or binding-reference issues.
 * [fieldName] identifies the affected field when known, for example:
 * `text`, `action`, `texture`, `valueBinding`, `bindings.defaultValue`.
 */
data class UiSceneValidationIssue(
    val severity: UiSceneValidationSeverity,
    val code: UiSceneValidationCode,
    val message: String,
    val nodeId: String? = null,
    val fieldName: String? = null,
    val bindingKey: String? = null,
)

fun error(
    code: UiSceneValidationCode,
    message: String,
    nodeId: String? = null,
    fieldName: String? = null,
    bindingKey: String? = null,
): UiSceneValidationIssue =
    UiSceneValidationIssue(
        severity = UiSceneValidationSeverity.Error,
        code = code,
        message = message,
        nodeId = nodeId,
        fieldName = fieldName,
        bindingKey = bindingKey,
    )

fun warning(
    code: UiSceneValidationCode,
    message: String,
    nodeId: String? = null,
    fieldName: String? = null,
    bindingKey: String? = null,
): UiSceneValidationIssue =
    UiSceneValidationIssue(
        severity = UiSceneValidationSeverity.Warning,
        code = code,
        message = message,
        nodeId = nodeId,
        fieldName = fieldName,
        bindingKey = bindingKey,
    )

fun info(
    code: UiSceneValidationCode,
    message: String,
    nodeId: String? = null,
    fieldName: String? = null,
    bindingKey: String? = null,
): UiSceneValidationIssue =
    UiSceneValidationIssue(
        severity = UiSceneValidationSeverity.Info,
        code = code,
        message = message,
        nodeId = nodeId,
        fieldName = fieldName,
        bindingKey = bindingKey,
    )

/**
 * Facade for the default modular `.krui` validation pipeline.
 */
class UiSceneValidator(
    private val pipeline: UiSceneValidationPipeline = UiSceneValidationPipeline.default(),
) {
    /**
     * Returns validation issues without throwing, so runtime and editor code can
     * choose whether to warn, block loading, or offer repairs.
     */
    fun validate(
        document: UiSceneDocument,
        skinMetadata: UiSceneSkinValidationMetadata? = null,
        textureMetadata: UiSceneTextureValidationMetadata? = null,
    ): List<UiSceneValidationIssue> =
        validate(
            UiSceneValidationContext(
                document = document,
                skinMetadata = skinMetadata,
                textureMetadata = textureMetadata,
            ),
        )

    fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> =
        pipeline.validate(context)
}
