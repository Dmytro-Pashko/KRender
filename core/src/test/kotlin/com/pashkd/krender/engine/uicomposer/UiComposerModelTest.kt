package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class UiComposerModelTest {
    @Test
    internal fun `finds nested node by id`() {
        val root = UiSceneNode(
            id = "root",
            type = UiSceneNodeType.Stack,
            children = listOf(
                UiSceneNode(
                    id = "panel",
                    type = UiSceneNodeType.Table,
                    children = listOf(
                        UiSceneNode(id = "health_label", type = UiSceneNodeType.Label, text = "{healthLabel}"),
                    ),
                ),
            ),
        )

        assertEquals("health_label", findUiSceneNodeById(root, "health_label")?.id)
        assertNull(findUiSceneNodeById(root, "missing"))
    }

    @Test
    internal fun `preview payload contains Woolboy defaults`() {
        assertEquals("Loading...", DefaultPreviewPayload["title"])
        assertEquals("0.65", DefaultPreviewPayload["progress"])
        assertEquals("textures/woolboy/hud_heart_full.png", DefaultPreviewPayload["life1Texture"])
        assertTrue(DefaultPreviewPayload.containsKey("primaryButtonAction"))
    }

    @Test
    internal fun `loader captures parse failures without throwing`() {
        val state = UiComposerState(uiScenePath = "bad.krui")
        val loader = UiComposerDocumentLoader(readText = { "{" })

        loader.reload(state)

        assertNull(state.document)
        assertNotNull(state.parseError)
        assertEquals(emptyList(), state.validationIssues)
        assertEquals(false, state.reloadRequested)
    }

    @Test
    internal fun `loader preserves valid document and validation result`() {
        val documentJson = """
            {
              "schemaVersion": 1,
              "id": "test",
              "skin": "ui/skins/craftacular-ui.json",
              "root": {
                "id": "root",
                "type": "Stack",
                "children": []
              }
            }
        """.trimIndent()
        val state = UiComposerState(uiScenePath = "ok.krui")
        val loader = UiComposerDocumentLoader(readText = { documentJson })

        loader.reload(state)

        assertEquals(UiSceneDocument.CurrentSchemaVersion, state.document?.schemaVersion)
        assertEquals("test", state.document?.id)
        assertEquals(emptyList(), state.validationIssues)
        assertNull(state.parseError)
    }
}
