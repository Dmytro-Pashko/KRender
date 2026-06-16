package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class UiComposerStyleValidationTest {
    @Test
    internal fun `style validation accepts known Label style`() {
        val document =
            UiSceneDocument(
                id = "style_test",
                skin = "ui/skins/craftacular-ui.json",
                root =
                    UiSceneNode(
                        id = "root",
                        type = UiSceneNodeType.Stack,
                        children =
                            listOf(
                                UiSceneNode(
                                    id = "title",
                                    type = UiSceneNodeType.Label,
                                    style = "title",
                                    text = "Title",
                                ),
                            ),
                    ),
            )

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        labelStyles = listOf("title"),
                    ),
            )

        assertTrue(issues.isEmpty())
    }

    @Test
    internal fun `style validation warns for missing Label style`() {
        val document =
            simpleDocument(
                UiSceneNode(
                    id = "title",
                    type = UiSceneNodeType.Label,
                    style = "title",
                    text = "Title",
                ),
            )

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        labelStyles = listOf("default"),
                    ),
            )

        assertEquals(1, issues.size)
        assertEquals("title", issues.single().nodeId)
        assertTrue(issues.single().message.contains("Label style"))
        assertTrue(issues.single().message.contains("not found"))
    }

    @Test
    internal fun `style validation warns for missing TextButton style`() {
        val document =
            simpleDocument(
                UiSceneNode(
                    id = "start_button",
                    type = UiSceneNodeType.TextButton,
                    style = "menu-button",
                    text = "Start",
                ),
            )

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        textButtonStyles = listOf("default"),
                    ),
            )

        assertEquals(1, issues.size)
        assertEquals("start_button", issues.single().nodeId)
        assertTrue(issues.single().message.contains("TextButton style"))
        assertTrue(issues.single().message.contains("not found"))
    }

    @Test
    internal fun `style validation warns for missing ProgressBar style`() {
        val document =
            simpleDocument(
                UiSceneNode(
                    id = "health_bar",
                    type = UiSceneNodeType.ProgressBar,
                    style = "hud-health",
                ),
            )

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        progressBarStyles = listOf("default-horizontal"),
                    ),
            )

        assertEquals(1, issues.size)
        assertEquals("health_bar", issues.single().nodeId)
        assertTrue(issues.single().message.contains("ProgressBar style"))
        assertTrue(issues.single().message.contains("not found"))
    }

    @Test
    internal fun `style validation warns for missing background drawable`() {
        val document =
            simpleDocument(
                UiSceneNode(
                    id = "panel",
                    type = UiSceneNodeType.Container,
                    background = "panel-bg",
                ),
            )

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        drawables = listOf("window"),
                    ),
            )

        assertEquals(1, issues.size)
        assertEquals("panel", issues.single().nodeId)
        assertTrue(issues.single().message.contains("Background drawable"))
        assertTrue(issues.single().message.contains("not found"))
    }

    @Test
    internal fun `style validation reports Skin load error as document issue`() {
        val document = simpleDocument()

        val issues =
            validateStyleReferences(
                document = document,
                skinMetadata =
                    UiComposerSkinMetadata(
                        skinPath = document.skin,
                        loadError = "missing file",
                    ),
            )

        assertEquals(1, issues.size)
        assertNull(issues.single().nodeId)
        assertTrue(issues.single().message.contains("could not be loaded"))
    }

    @Test
    internal fun `style validation returns no issues when metadata is unavailable`() {
        val issues = validateStyleReferences(simpleDocument(), skinMetadata = null)

        assertTrue(issues.isEmpty())
    }

    private fun simpleDocument(vararg children: UiSceneNode): UiSceneDocument =
        UiSceneDocument(
            id = "style_test",
            skin = "ui/skins/craftacular-ui.json",
            root =
                UiSceneNode(
                    id = "root",
                    type = UiSceneNodeType.Stack,
                    children = children.toList(),
                ),
        )
}
