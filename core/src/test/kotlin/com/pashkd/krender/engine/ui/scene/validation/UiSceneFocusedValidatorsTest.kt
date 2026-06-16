package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneBindingDefinition
import com.pashkd.krender.engine.ui.scene.UiSceneBindingType
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationCode
import com.pashkd.krender.engine.ui.scene.UiSceneValidationSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DocumentMetadataValidatorTest {
    @Test
    fun `reports invalid document metadata`() {
        val issues =
            DocumentMetadataValidator.validate(
                context(
                    document(
                        schemaVersion = 2,
                        id = "",
                        skin = "",
                        root = node("root", UiSceneNodeType.Label),
                    ),
                ),
            )

        assertIssue(issues, UiSceneValidationCode.UnsupportedSchemaVersion, UiSceneValidationSeverity.Error)
        assertIssue(issues, UiSceneValidationCode.BlankDocumentId, UiSceneValidationSeverity.Error)
        assertIssue(issues, UiSceneValidationCode.BlankSkinPath, UiSceneValidationSeverity.Error)
        assertIssue(issues, UiSceneValidationCode.InvalidRootType, UiSceneValidationSeverity.Error, nodeId = "root")
    }
}

internal class NodeIdUniquenessValidatorTest {
    @Test
    fun `reports blank and duplicate node ids`() {
        val issues =
            NodeIdUniquenessValidator.validate(
                context(
                    document(
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("", UiSceneNodeType.Space),
                                        node("duplicate", UiSceneNodeType.Space),
                                        node("duplicate", UiSceneNodeType.Space),
                                    ),
                            ),
                    ),
                ),
            )

        assertIssue(issues, UiSceneValidationCode.BlankNodeId, UiSceneValidationSeverity.Error, fieldName = "id")
        assertIssue(issues, UiSceneValidationCode.DuplicateNodeId, UiSceneValidationSeverity.Error, nodeId = "duplicate")
    }
}

internal class NodeShapeValidatorTest {
    @Test
    fun `reports unsupported node shapes and invalid progress bars`() {
        val issues =
            NodeShapeValidator.validate(
                context(
                    document(
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("image", UiSceneNodeType.Image, children = listOf(node("child"))),
                                        node("container", UiSceneNodeType.Container, children = listOf(node("a"), node("b"))),
                                        node("missing", UiSceneNodeType.ProgressBar),
                                        node("bad_range", UiSceneNodeType.ProgressBar, value = 0f, min = 1f, max = 1f, step = 0f),
                                    ),
                            ),
                    ),
                ),
            )

        assertIssue(issues, UiSceneValidationCode.LeafNodeHasChildren, UiSceneValidationSeverity.Error, nodeId = "image")
        assertIssue(
            issues,
            UiSceneValidationCode.ContainerHasMultipleChildren,
            UiSceneValidationSeverity.Warning,
            nodeId = "container",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.MissingProgressBarValue,
            UiSceneValidationSeverity.Warning,
            nodeId = "missing",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.InvalidProgressBarRange,
            UiSceneValidationSeverity.Error,
            nodeId = "bad_range",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.InvalidProgressBarStep,
            UiSceneValidationSeverity.Error,
            nodeId = "bad_range",
        )
    }
}

internal class BindingDefinitionValidatorTest {
    @Test
    fun `reports invalid binding definitions`() {
        val issues =
            BindingDefinitionValidator.validate(
                context(
                    document(
                        bindings =
                            listOf(
                                UiSceneBindingDefinition("", UiSceneBindingType.Text),
                                UiSceneBindingDefinition("scores", UiSceneBindingType.Number, "1"),
                                UiSceneBindingDefinition("scores", UiSceneBindingType.Number, "2"),
                                UiSceneBindingDefinition("badNumber", UiSceneBindingType.Number, "abc"),
                                UiSceneBindingDefinition("texture", UiSceneBindingType.Texture, ""),
                                UiSceneBindingDefinition("action", UiSceneBindingType.Action, ""),
                            ),
                    ),
                ),
            )

        assertIssue(issues, UiSceneValidationCode.BlankBindingKey, UiSceneValidationSeverity.Error)
        assertIssue(
            issues,
            UiSceneValidationCode.DuplicateBindingKey,
            UiSceneValidationSeverity.Error,
            bindingKey = "scores",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.InvalidNumberBindingDefault,
            UiSceneValidationSeverity.Error,
            bindingKey = "badNumber",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.BlankTextureBindingDefault,
            UiSceneValidationSeverity.Warning,
            bindingKey = "texture",
        )
        assertIssue(
            issues,
            UiSceneValidationCode.BlankActionBindingDefault,
            UiSceneValidationSeverity.Warning,
            bindingKey = "action",
        )
    }
}

internal class PlaceholderSyntaxValidatorTest {
    @Test
    fun `accepts valid placeholders and reports malformed placeholders`() {
        val validIssues =
            PlaceholderSyntaxValidator.validate(
                context(document(root = node("label", UiSceneNodeType.Label, text = "Score: {scores}"))),
            )

        val malformedIssues =
            PlaceholderSyntaxValidator.validate(
                context(
                    document(
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("blank", UiSceneNodeType.Label, text = "{ }"),
                                        node("missing", UiSceneNodeType.Label, text = "Missing {key"),
                                        node("nested", UiSceneNodeType.Label, text = "{{bad}}"),
                                    ),
                            ),
                    ),
                ),
            )

        assertTrue(validIssues.isEmpty())
        assertEquals(3, malformedIssues.size)
        malformedIssues.forEach { issue ->
            assertEquals(UiSceneValidationCode.MalformedBindingPlaceholder, issue.code)
            assertEquals(UiSceneValidationSeverity.Warning, issue.severity)
        }
    }
}

internal class BindingReferenceValidatorTest {
    @Test
    fun `reports unknown binding keys and accepts known keys`() {
        val issues =
            BindingReferenceValidator.validate(
                context(
                    document(
                        bindings = listOf(UiSceneBindingDefinition("scores", UiSceneBindingType.Number, "0")),
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("score_label", UiSceneNodeType.Label, text = "Score: {scores}"),
                                        node("life", UiSceneNodeType.Image, texture = "{life1Texture}"),
                                        node("bar", UiSceneNodeType.ProgressBar, valueBinding = "progress"),
                                    ),
                            ),
                    ),
                ),
            )

        assertEquals(2, issues.size)
        assertIssue(issues, UiSceneValidationCode.UnknownBindingKey, UiSceneValidationSeverity.Error, bindingKey = "life1Texture")
        assertIssue(issues, UiSceneValidationCode.UnknownBindingKey, UiSceneValidationSeverity.Error, bindingKey = "progress")
        assertTrue(issues.none { issue -> issue.bindingKey == "scores" })
    }
}

internal class BindingTypeCompatibilityValidatorTest {
    @Test
    fun `reports incompatible binding types by field`() {
        val bindings =
            listOf(
                UiSceneBindingDefinition("number", UiSceneBindingType.Number, "1"),
                UiSceneBindingDefinition("text", UiSceneBindingType.Text, "Ready"),
                UiSceneBindingDefinition("texture", UiSceneBindingType.Texture, "textures/a.png"),
                UiSceneBindingDefinition("action", UiSceneBindingType.Action, "game.start"),
            )
        val okIssues =
            BindingTypeCompatibilityValidator.validate(
                context(
                    document(
                        bindings = bindings,
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("bar", UiSceneNodeType.ProgressBar, valueBinding = "number"),
                                        node("image", UiSceneNodeType.Image, texture = "{texture}"),
                                        node("button", UiSceneNodeType.TextButton, text = "{action}", action = "{action}"),
                                        node("label", UiSceneNodeType.Label, text = "{text} {number}"),
                                    ),
                            ),
                    ),
                ),
            )
        val badIssues =
            BindingTypeCompatibilityValidator.validate(
                context(
                    document(
                        bindings = bindings,
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("bar", UiSceneNodeType.ProgressBar, valueBinding = "text"),
                                        node("image", UiSceneNodeType.Image, texture = "{number}"),
                                        node("button", UiSceneNodeType.TextButton, text = "{texture}", action = "{text}"),
                                        node("label", UiSceneNodeType.Label, text = "{texture}"),
                                    ),
                            ),
                    ),
                ),
            )

        assertTrue(okIssues.isEmpty())
        assertIssue(
            badIssues,
            UiSceneValidationCode.InvalidBindingTypeForField,
            UiSceneValidationSeverity.Error,
            nodeId = "bar",
            bindingKey = "text",
        )
        assertIssue(
            badIssues,
            UiSceneValidationCode.InvalidBindingTypeForField,
            UiSceneValidationSeverity.Error,
            nodeId = "image",
            bindingKey = "number",
        )
        assertIssue(
            badIssues,
            UiSceneValidationCode.InvalidBindingTypeForField,
            UiSceneValidationSeverity.Error,
            nodeId = "button",
            bindingKey = "text",
        )
        assertTrue(
            badIssues.any { issue ->
                issue.nodeId == "label" &&
                    issue.bindingKey == "texture" &&
                    issue.severity == UiSceneValidationSeverity.Warning
            },
        )
    }
}

internal class StyleReferenceValidatorTest {
    @Test
    fun `reports missing styles backgrounds and skin load errors`() {
        val metadata =
            UiSceneSkinValidationMetadata(
                labelStyles = setOf("title"),
                textButtonStyles = setOf("default"),
                progressBarStyles = setOf("default-horizontal"),
                drawables = setOf("window"),
            )
        val issues =
            StyleReferenceValidator.validate(
                context(
                    document(
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("label", UiSceneNodeType.Label, style = "missing"),
                                        node("button", UiSceneNodeType.TextButton, style = "missing"),
                                        node("bar", UiSceneNodeType.ProgressBar, style = "missing", value = 0f),
                                        node("panel", UiSceneNodeType.Container, background = "missing"),
                                    ),
                            ),
                    ),
                    skinMetadata = metadata,
                ),
            )
        val unavailableIssues =
            StyleReferenceValidator.validate(
                context(document(), skinMetadata = UiSceneSkinValidationMetadata(loadError = "missing file")),
            )
        val skippedIssues = StyleReferenceValidator.validate(context(document(), skinMetadata = null))

        assertEquals(4, issues.size)
        assertIssue(issues, UiSceneValidationCode.MissingStyle, UiSceneValidationSeverity.Warning, nodeId = "label")
        assertIssue(
            issues,
            UiSceneValidationCode.MissingBackgroundDrawable,
            UiSceneValidationSeverity.Warning,
            nodeId = "panel",
        )
        assertIssue(unavailableIssues, UiSceneValidationCode.MissingStyle, UiSceneValidationSeverity.Warning)
        assertTrue(skippedIssues.isEmpty())
    }
}

internal class TextureReferenceValidatorTest {
    @Test
    fun `reports missing textures and texture binding defaults`() {
        val metadata =
            UiSceneTextureValidationMetadata(
                texturePaths = setOf("textures/a.png"),
                nonTextureAssetPaths = setOf("ui/scenes/foo.krui"),
            )
        val issues =
            TextureReferenceValidator.validate(
                context(
                    document(
                        bindings =
                            listOf(
                                UiSceneBindingDefinition("known", UiSceneBindingType.Texture, "textures/a.png"),
                                UiSceneBindingDefinition("blank", UiSceneBindingType.Texture, ""),
                                UiSceneBindingDefinition("missing", UiSceneBindingType.Texture, "textures/missing.png"),
                            ),
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("known", UiSceneNodeType.Image, texture = "textures/a.png"),
                                        node("missing", UiSceneNodeType.Image, texture = "textures/missing.png"),
                                        node("bad", UiSceneNodeType.Image, texture = "ui/scenes/foo.krui"),
                                        node("bound", UiSceneNodeType.Image, texture = "{known}"),
                                    ),
                            ),
                    ),
                    textureMetadata = metadata,
                ),
            )

        assertIssue(issues, UiSceneValidationCode.MissingTexture, UiSceneValidationSeverity.Warning, nodeId = "missing")
        assertIssue(issues, UiSceneValidationCode.NonTextureAsset, UiSceneValidationSeverity.Warning, nodeId = "bad")
        assertIssue(
            issues,
            UiSceneValidationCode.MissingTextureBindingDefault,
            UiSceneValidationSeverity.Warning,
            bindingKey = "missing",
        )
        assertTrue(issues.none { issue -> issue.bindingKey == "blank" })
        assertTrue(issues.none { issue -> issue.nodeId == "known" || issue.nodeId == "bound" })
    }
}

internal class UiSceneValidationPipelineTest {
    @Test
    fun `default pipeline composes document binding style and texture rules`() {
        val issues =
            UiSceneValidationPipeline.default().validate(
                context(
                    document(
                        id = "",
                        bindings = listOf(UiSceneBindingDefinition("texture", UiSceneBindingType.Texture, "textures/missing.png")),
                        root =
                            node(
                                "root",
                                children =
                                    listOf(
                                        node("label", UiSceneNodeType.Label, text = "{missing}", style = "missing"),
                                        node("image", UiSceneNodeType.Image, texture = "textures/missing.png"),
                                    ),
                            ),
                    ),
                    skinMetadata = UiSceneSkinValidationMetadata(labelStyles = emptySet()),
                    textureMetadata = UiSceneTextureValidationMetadata(texturePaths = emptySet()),
                ),
            )

        assertIssue(issues, UiSceneValidationCode.BlankDocumentId, UiSceneValidationSeverity.Error)
        assertIssue(issues, UiSceneValidationCode.UnknownBindingKey, UiSceneValidationSeverity.Error, bindingKey = "missing")
        assertIssue(issues, UiSceneValidationCode.MissingStyle, UiSceneValidationSeverity.Warning, nodeId = "label")
        assertIssue(issues, UiSceneValidationCode.MissingTexture, UiSceneValidationSeverity.Warning, nodeId = "image")
    }
}

private fun context(
    document: UiSceneDocument,
    skinMetadata: UiSceneSkinValidationMetadata? = null,
    textureMetadata: UiSceneTextureValidationMetadata? = null,
): UiSceneValidationContext =
    UiSceneValidationContext(
        document = document,
        skinMetadata = skinMetadata,
        textureMetadata = textureMetadata,
    )

private fun document(
    schemaVersion: Int = UiSceneDocument.CurrentSchemaVersion,
    id: String = "scene",
    skin: String = "ui/skins/craftacular-ui.json",
    bindings: List<UiSceneBindingDefinition> = emptyList(),
    root: UiSceneNode = node("root"),
): UiSceneDocument =
    UiSceneDocument(
        schemaVersion = schemaVersion,
        id = id,
        skin = skin,
        bindings = bindings,
        root = root,
    )

private fun node(
    id: String,
    type: UiSceneNodeType = UiSceneNodeType.Stack,
    style: String? = null,
    background: String? = null,
    text: String? = null,
    action: String? = null,
    texture: String? = null,
    value: Float? = null,
    valueBinding: String? = null,
    min: Float = 0f,
    max: Float = 1f,
    step: Float = 0.01f,
    children: List<UiSceneNode> = emptyList(),
): UiSceneNode =
    UiSceneNode(
        id = id,
        type = type,
        style = style,
        background = background,
        text = text,
        action = action,
        texture = texture,
        value = value,
        valueBinding = valueBinding,
        min = min,
        max = max,
        step = step,
        children = children,
    )

private fun assertIssue(
    issues: List<com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue>,
    code: UiSceneValidationCode,
    severity: UiSceneValidationSeverity,
    nodeId: String? = null,
    fieldName: String? = null,
    bindingKey: String? = null,
) {
    assertTrue(
        issues.any { issue ->
            issue.code == code &&
                issue.severity == severity &&
                (nodeId == null || issue.nodeId == nodeId) &&
                (fieldName == null || issue.fieldName == fieldName) &&
                (bindingKey == null || issue.bindingKey == bindingKey)
        },
        "Expected $severity $code nodeId=$nodeId fieldName=$fieldName bindingKey=$bindingKey in $issues",
    )
}
