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
        assertTrue(DefaultPreviewPayload.containsKey("primaryButtonText"))
        assertEquals("textures/woolboy/hud_heart_full.png", DefaultPreviewPayload["life1Texture"])
        assertTrue(DefaultPreviewPayload.containsKey("life2Texture"))
        assertTrue(DefaultPreviewPayload.containsKey("life3Texture"))
        assertTrue(DefaultPreviewPayload.containsKey("primaryButtonAction"))
        assertTrue(DefaultPreviewPayload.containsKey("healthLabel"))
        assertTrue(DefaultPreviewPayload.containsKey("scores"))
        assertTrue(DefaultPreviewPayload.containsKey("lives"))
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

    @Test
    internal fun `loader preserves selection when node still exists`() {
        val state = UiComposerState(uiScenePath = "ok.krui", selectedNodeId = "child")
        val loader = UiComposerDocumentLoader(readText = {
            """
            {
              "schemaVersion": 1,
              "id": "test",
              "skin": "ui/skins/craftacular-ui.json",
              "root": {
                "id": "root",
                "type": "Stack",
                "children": [
                  { "id": "child", "type": "Space" }
                ]
              }
            }
            """.trimIndent()
        })

        loader.reload(state)

        assertEquals("child", state.selectedNodeId)
    }

    @Test
    internal fun `loader clears selection when node disappears`() {
        val state = UiComposerState(uiScenePath = "ok.krui", selectedNodeId = "missing")
        val loader = UiComposerDocumentLoader(readText = {
            """
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
        })

        loader.reload(state)

        assertNull(state.selectedNodeId)
    }

    @Test
    internal fun `preview payload reset returns default values`() {
        val state = UiComposerState(uiScenePath = "ok.krui")
        state.previewPayload["title"] = "Changed"
        state.previewPayload["progress"] = "0.10"

        state.previewPayload.clear()
        state.previewPayload.putAll(DefaultPreviewPayload)

        assertEquals(DefaultPreviewPayload, state.previewPayload)
    }

    @Test
    internal fun `updateNode replaces selected node by id`() {
        val document = editableDocument()

        val updated = document.updateNode("label") { node ->
            node.copy(text = "Edited")
        }

        assertEquals("Edited", findUiSceneNodeById(updated.root, "label")?.text)
        assertEquals("Original", findUiSceneNodeById(document.root, "label")?.text)
    }

    @Test
    internal fun `updateNode leaves document unchanged when node id is missing`() {
        val document = editableDocument()

        val updated = document.updateNode("missing") { node ->
            node.copy(text = "Edited")
        }

        assertEquals(document, updated)
    }

    @Test
    internal fun `updateNode can rename selected node id`() {
        val document = editableDocument()

        val updated = document.updateNode("label") { node ->
            node.copy(id = "renamed_label")
        }

        assertNull(findUiSceneNodeById(updated.root, "label"))
        assertEquals("Original", findUiSceneNodeById(updated.root, "renamed_label")?.text)
        assertTrue(updated.containsNodeId("renamed_label"))
    }

    private fun editableDocument(): UiSceneDocument =
        UiSceneDocument(
            id = "editable",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(
                        id = "panel",
                        type = UiSceneNodeType.Table,
                        children = listOf(
                            UiSceneNode(id = "label", type = UiSceneNodeType.Label, text = "Original"),
                        ),
                    ),
                ),
            ),
        )
}
