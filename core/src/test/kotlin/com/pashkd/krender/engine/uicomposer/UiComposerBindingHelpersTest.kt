package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class UiComposerBindingHelpersTest {
    @Test
    internal fun `extractBindingPlaceholders returns keys from placeholder text`() {
        assertEquals(setOf("scores"), extractBindingPlaceholders("Score: {scores}"))
        assertEquals(setOf("life1Texture"), extractBindingPlaceholders("{life1Texture}"))
        assertEquals(setOf("a", "b"), extractBindingPlaceholders("{a} and {b}"))
        assertEquals(emptySet(), extractBindingPlaceholders("No bindings"))
        assertEquals(emptySet(), extractBindingPlaceholders(null))
    }

    @Test
    internal fun `extractBindingPlaceholders ignores blank and unterminated placeholders`() {
        assertEquals(emptySet(), extractBindingPlaceholders("{ }"))
        assertEquals(emptySet(), extractBindingPlaceholders("Missing {key"))
    }

    @Test
    internal fun `extractBindingPlaceholders treats nested braces as best effort inner placeholder`() {
        assertEquals(setOf("bad"), extractBindingPlaceholders("{{bad}}"))
    }

    @Test
    internal fun `collectBindingReferences returns supported node fields`() {
        val references = collectBindingReferences(bindingDocument())
            .map { reference ->
                "${reference.nodeId}.${reference.fieldName}.${reference.key}.${reference.placeholderSyntax}"
            }
            .toSet()

        assertEquals(
            setOf(
                "score_label.text.scores.true",
                "button.text.primaryButtonText.true",
                "button.action.primaryButtonAction.true",
                "life.texture.life1Texture.true",
                "bar.valueBinding.progress.false",
            ),
            references,
        )
    }

    @Test
    internal fun `validateBindingReferences warns for unknown keys`() {
        val issues = validateBindingReferences(bindingDocument(), knownKeys = setOf("scores"))

        assertEquals(4, issues.size)
        assertTrue(issues.none { issue -> issue.message.contains("'scores'") })
        assertTrue(issues.any { issue -> issue.nodeId == "button" && issue.message.contains("primaryButtonText") })
        assertTrue(issues.any { issue -> issue.nodeId == "button" && issue.message.contains("primaryButtonAction") })
        assertTrue(issues.any { issue -> issue.nodeId == "life" && issue.message.contains("life1Texture") })
        assertTrue(issues.any { issue -> issue.nodeId == "bar" && issue.message.contains("valueBinding") })
    }

    @Test
    internal fun `missingBindingKeys groups references by key sorted`() {
        val document = UiSceneDocument(
            id = "bindings",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "score_label", type = UiSceneNodeType.Label, text = "Score: {scores}"),
                    UiSceneNode(id = "score_button", type = UiSceneNodeType.TextButton, text = "{scores}", action = "{action}"),
                ),
            ),
        )

        val missing = missingBindingKeys(document, knownKeys = emptySet())

        assertEquals(listOf("action", "scores"), missing.map { it.key })
        assertEquals(setOf("score_label", "score_button"), missing.single { it.key == "scores" }.nodeIds)
        assertEquals(setOf("text"), missing.single { it.key == "scores" }.fields)
        assertEquals(setOf("action"), missing.single { it.key == "action" }.fields)
    }

    @Test
    internal fun `insertPlaceholder appends placeholder predictably`() {
        assertEquals("{scores}", insertPlaceholder("", "scores"))
        assertEquals("{scores}", insertPlaceholder("  ", "scores"))
        assertEquals("Score: {scores}", insertPlaceholder("Score:", "scores"))
    }

    @Test
    internal fun `textureBindingPlaceholder returns placeholder syntax`() {
        assertEquals("{life1Texture}", textureBindingPlaceholder("life1Texture"))
    }

    @Test
    internal fun `defaultPreviewPayloadValueFor uses editor only heuristics`() {
        assertEquals("", defaultPreviewPayloadValueFor("life1Texture"))
        assertEquals("action.todo", defaultPreviewPayloadValueFor("primaryButtonAction"))
        assertEquals("0.5", defaultPreviewPayloadValueFor("progress"))
        assertEquals("0", defaultPreviewPayloadValueFor("scores"))
        assertEquals("100/100", defaultPreviewPayloadValueFor("healthLabel"))
        assertEquals("", defaultPreviewPayloadValueFor("unknown"))
    }

    private fun bindingDocument(): UiSceneDocument =
        UiSceneDocument(
            id = "bindings",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "score_label", type = UiSceneNodeType.Label, text = "Score: {scores}"),
                    UiSceneNode(
                        id = "button",
                        type = UiSceneNodeType.TextButton,
                        text = "{primaryButtonText}",
                        action = "{primaryButtonAction}",
                    ),
                    UiSceneNode(id = "life", type = UiSceneNodeType.Image, texture = "{life1Texture}"),
                    UiSceneNode(id = "bar", type = UiSceneNodeType.ProgressBar, valueBinding = "progress"),
                ),
            ),
        )
}
