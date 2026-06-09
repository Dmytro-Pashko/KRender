package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class UiComposerStyleValidationTest {
    @Test
    internal fun `reports missing typed styles and background drawables`() {
        val document = UiSceneDocument(
            id = "style_test",
            skin = "ui/skins/test.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "title", type = UiSceneNodeType.Label, style = "missing_label", text = "Title"),
                    UiSceneNode(id = "play", type = UiSceneNodeType.TextButton, style = "missing_button", text = "Play", action = "play"),
                    UiSceneNode(id = "progress", type = UiSceneNodeType.ProgressBar, style = "missing_progress", value = 0.5f),
                    UiSceneNode(id = "panel", type = UiSceneNodeType.Table, background = "missing_background"),
                ),
            ),
        )

        val issues = validateStyleReferences(
            document = document,
            skinMetadata = UiComposerSkinMetadata(
                skinPath = "ui/skins/test.json",
                labelStyles = listOf("default"),
                textButtonStyles = listOf("default"),
                progressBarStyles = listOf("default-horizontal"),
                drawables = listOf("window"),
            ),
        )

        assertEquals(4, issues.size)
        assertTrue(issues.any { it.nodeId == "title" && it.message.contains("Label style 'missing_label'") })
        assertTrue(issues.any { it.nodeId == "play" && it.message.contains("TextButton style 'missing_button'") })
        assertTrue(issues.any { it.nodeId == "progress" && it.message.contains("ProgressBar style 'missing_progress'") })
        assertTrue(issues.any { it.nodeId == "panel" && it.message.contains("Background drawable 'missing_background'") })
    }

    @Test
    internal fun `reports skin load errors as document diagnostics`() {
        val document = UiSceneDocument(
            id = "style_test",
            skin = "ui/skins/missing.json",
            root = UiSceneNode(id = "root", type = UiSceneNodeType.Stack),
        )

        val issues = validateStyleReferences(
            document = document,
            skinMetadata = UiComposerSkinMetadata(
                skinPath = document.skin,
                loadError = "File not found",
            ),
        )

        assertEquals(1, issues.size)
        assertEquals(null, issues.single().nodeId)
        assertTrue(issues.single().message.contains("Skin 'ui/skins/missing.json' could not be loaded: File not found"))
    }

    @Test
    internal fun `returns no issues when metadata is unavailable`() {
        val document = UiSceneDocument(
            id = "style_test",
            skin = "ui/skins/test.json",
            root = UiSceneNode(id = "root", type = UiSceneNodeType.Stack),
        )

        assertTrue(validateStyleReferences(document, skinMetadata = null).isEmpty())
    }
}
