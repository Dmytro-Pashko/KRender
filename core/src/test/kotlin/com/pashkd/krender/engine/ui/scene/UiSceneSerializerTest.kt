package com.pashkd.krender.engine.ui.scene

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSceneSerializerTest {
    private val serializer = UiSceneSerializer()
    private val exampleLoadingFile = File("../assets/ui/scenes/example_loading.krui")
    private val woolboySceneFiles = listOf(
        File("../assets/ui/scenes/woolboy_loading.krui"),
        File("../assets/ui/scenes/woolboy_main_menu.krui"),
        File("../assets/ui/scenes/woolboy_hud.krui"),
        File("../assets/ui/scenes/woolboy_final_results.krui"),
    )

    @Test
    fun `decodes example loading document`() {
        val document = serializer.decode(exampleLoadingFile.readText())

        assertEquals(1, document.schemaVersion)
        assertEquals("example_loading", document.id)
        assertEquals("ui/skins/craftacular-ui.json", document.skin)
        assertEquals(UiSceneNodeType.Table, document.root.type)
        assertEquals("title", document.root.children.first().style)
        assertEquals("progress", document.root.children.last().id)
    }

    @Test
    fun `encodes then decodes basic fields`() {
        val document = UiSceneDocument(
            id = "hud",
            skin = "ui\\skins\\craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(
                        id = "score",
                        type = UiSceneNodeType.Label,
                        background = "window",
                        text = "Score: {scores}",
                        align = UiSceneAlign.Top,
                    ),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(document))

        assertEquals("hud", decoded.id)
        assertEquals("ui/skins/craftacular-ui.json", decoded.skin)
        assertEquals(UiSceneNodeType.Stack, decoded.root.type)
        assertEquals("Score: {scores}", decoded.root.children.single().text)
        assertEquals("window", decoded.root.children.single().background)
        assertEquals(UiSceneAlign.Top, decoded.root.children.single().align)
    }

    @Test
    fun `Table orientation defaults to Vertical when omitted`() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "table_test",
              "skin": "ui/skins/craftacular-ui.json",
              "root": {
                "id": "root",
                "type": "Table"
              }
            }
        """.trimIndent()

        val document = serializer.decode(json)

        assertEquals(UiSceneTableOrientation.Vertical, document.root.tableOrientation)
    }

    @Test
    fun `Table orientation deserializes Horizontal`() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "table_test",
              "skin": "ui/skins/craftacular-ui.json",
              "root": {
                "id": "root",
                "type": "Table",
                "tableOrientation": "Horizontal"
              }
            }
        """.trimIndent()

        val document = serializer.decode(json)

        assertEquals(UiSceneTableOrientation.Horizontal, document.root.tableOrientation)
    }

    @Test
    fun `serializer preserves Horizontal table orientation`() {
        val document = UiSceneDocument(
            id = "table_test",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Table,
                tableOrientation = UiSceneTableOrientation.Horizontal,
            ),
        )

        val decoded = serializer.decode(serializer.encode(document))

        assertEquals(UiSceneTableOrientation.Horizontal, decoded.root.tableOrientation)
    }

    @Test
    fun `binding helpers replace known placeholders and keep missing placeholders`() {
        val text = UiSceneBindings.bindText(
            "Score: {scores} Health: {healthLabel} Missing: {missing}",
            mapOf("scores" to "120", "healthLabel" to "80/100"),
        )

        assertEquals("Score: 120 Health: 80/100 Missing: {missing}", text)
    }

    @Test
    fun `binding helper resolves float with fallback`() {
        val payload = mapOf("healthPercent" to "0.75", "bad" to "abc")

        assertEquals(0.75f, UiSceneBindings.boundFloat("healthPercent", payload, 0f))
        assertEquals(0.25f, UiSceneBindings.boundFloat("bad", payload, 0.25f))
        assertEquals(0.5f, UiSceneBindings.boundFloat("missing", payload, 0.5f))
    }

    @Test
    fun `validator reports duplicate ids`() {
        val document = UiSceneDocument(
            id = "bad",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Table,
                children = listOf(
                    UiSceneNode(id = "duplicate", type = UiSceneNodeType.Space),
                    UiSceneNode(id = "duplicate", type = UiSceneNodeType.Space),
                ),
            ),
        )

        val issues = UiSceneValidator().validate(document)

        assertTrue(issues.any { it.nodeId == "duplicate" && it.message.contains("duplicated") })
    }

    @Test
    fun `validator accepts valid example document`() {
        val document = serializer.decode(exampleLoadingFile.readText())

        val issues = UiSceneValidator().validate(document)

        assertEquals(emptyList(), issues)
    }

    @Test
    fun `validator accepts Woolboy scene documents`() {
        val validator = UiSceneValidator()

        woolboySceneFiles.forEach { file ->
            val document = serializer.decode(file.readText())

            assertEquals(emptyList(), validator.validate(document), "Expected ${file.name} to be valid.")
        }
    }

    @Test
    fun `validator reports invalid progress bar ranges`() {
        val document = UiSceneDocument(
            id = "bad_progress",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Table,
                children = listOf(
                    UiSceneNode(
                        id = "bad",
                        type = UiSceneNodeType.ProgressBar,
                        valueBinding = "progress",
                        min = 1f,
                        max = 1f,
                        step = 0f,
                    ),
                ),
            ),
        )

        val issues = UiSceneValidator().validate(document)

        assertTrue(issues.any { it.nodeId == "bad" && it.message == "ProgressBar max must be greater than min." })
        assertTrue(issues.any { it.nodeId == "bad" && it.message == "ProgressBar step must be positive." })
    }
}
