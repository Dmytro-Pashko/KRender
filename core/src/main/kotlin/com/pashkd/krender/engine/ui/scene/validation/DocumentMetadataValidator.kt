package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object DocumentMetadataValidator : UiSceneValidationRule {
    override val id: String = "DocumentMetadataValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> {
        val document = context.document
        val issues = mutableListOf<UiSceneValidationIssue>()
        if (document.schemaVersion != UiSceneDocument.CurrentSchemaVersion) {
            issues +=
                error(
                    code = UiSceneValidationCode.UnsupportedSchemaVersion,
                    message = "Unsupported schemaVersion ${document.schemaVersion}; expected ${UiSceneDocument.CurrentSchemaVersion}.",
                    fieldName = "schemaVersion",
                )
        }
        if (document.id.isBlank()) {
            issues +=
                error(
                    code = UiSceneValidationCode.BlankDocumentId,
                    message = "Document id must not be blank.",
                    fieldName = "id",
                )
        }
        if (document.skin.isBlank()) {
            issues +=
                error(
                    code = UiSceneValidationCode.BlankSkinPath,
                    message = "Skin path must not be blank.",
                    fieldName = "skin",
                )
        }
        if (!document.root.type.isRootContainer()) {
            issues +=
                error(
                    code = UiSceneValidationCode.InvalidRootType,
                    message = "Root node should be Stack, Table, or Container.",
                    nodeId = document.root.id.takeIf(String::isNotBlank),
                    fieldName = "root.type",
                )
        }
        return issues
    }
}

private fun UiSceneNodeType.isRootContainer(): Boolean = this == UiSceneNodeType.Stack || this == UiSceneNodeType.Table || this == UiSceneNodeType.Container
