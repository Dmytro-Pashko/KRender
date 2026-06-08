package com.pashkd.krender.engine.ui.scene

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiSceneSerializerTest {
    private val serializer = UiSceneSerializer()
    private val exampleLoadingFile = File("../assets/ui/scenes/example_loading.krui")

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
        assertEquals(UiSceneAlign.Top, decoded.root.children.single().align)
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
}
