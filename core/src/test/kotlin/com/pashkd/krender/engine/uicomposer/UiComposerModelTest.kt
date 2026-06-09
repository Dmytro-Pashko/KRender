package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetRegistrySnapshot
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    internal fun `canvas selection state defaults to selection-only enabled`() {
        val state = UiComposerState(uiScenePath = "ok.krui")

        assertTrue(state.canvasSelectionEnabled)
        assertTrue(state.highlightHovered)
        assertTrue(state.styleValidationIssues.isEmpty())
        assertTrue(state.textureValidationIssues.isEmpty())
        assertTrue(state.textureOptions.isEmpty())
        assertFalse(state.textureOptionsReloadRequested)
        assertEquals("", state.textureSearchQuery)
        assertNull(state.hoveredNodeId)
        assertNull(state.skinMetadata)
        assertNull(state.canvasStatusMessage)
    }

    @Test
    internal fun `hovered node can be cleared independently from selected node`() {
        val state = UiComposerState(
            uiScenePath = "ok.krui",
            selectedNodeId = "selected",
            hoveredNodeId = "hovered",
        )

        state.hoveredNodeId = null

        assertEquals("selected", state.selectedNodeId)
        assertNull(state.hoveredNodeId)
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
    internal fun `texture option provider returns sorted texture descriptors only`() {
        val provider = UiComposerTextureOptionsProvider(
            assets = FakeAssetRegistry(
                listOf(
                    descriptor(
                        id = "asset:heart",
                        name = "Heart",
                        path = "textures/woolboy/hud_heart_full.png",
                        category = AssetCategory.Texture,
                        type = AssetType.Texture,
                    ),
                    descriptor(
                        id = "asset:scene",
                        name = "Scene",
                        path = "ui/scenes/hud.krui",
                        category = AssetCategory.UI,
                        type = AssetType.UiScene,
                    ),
                    descriptor(
                        id = "asset:broken",
                        name = "Broken",
                        path = "textures/broken.bin",
                        category = AssetCategory.Texture,
                        type = AssetType.Unknown,
                    ),
                    descriptor(
                        id = "asset:alpha",
                        name = "Alpha",
                        path = "textures/alpha.png",
                        category = AssetCategory.Texture,
                        type = AssetType.Texture,
                        metadata = mapOf("displayName" to "Alpha Texture"),
                    ),
                ),
            ),
        )

        val options = provider.listTextureOptions()

        assertEquals(listOf("Alpha Texture", "Heart"), options.map { it.displayName })
        assertEquals(listOf("textures/alpha.png", "textures/woolboy/hud_heart_full.png"), options.map { it.path })
        assertEquals("asset:alpha", options.first().assetId)
    }

    @Test
    internal fun `texture validation warns for Image path missing from registry`() {
        val document = UiSceneDocument(
            id = "textures",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "heart", type = UiSceneNodeType.Image, texture = "textures/missing.png"),
                ),
            ),
        )

        val issues = validateTextureReferences(
            document = document,
            textureOptions = listOf(UiComposerTextureOption("Known", "textures/known.png")),
        )

        assertEquals(1, issues.size)
        assertEquals("heart", issues.single().nodeId)
        assertTrue(issues.single().message.contains("not in Asset Registry"))
    }

    @Test
    internal fun `texture validation warns when path resolves to non texture asset`() {
        val document = UiSceneDocument(
            id = "textures",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "bad", type = UiSceneNodeType.Image, texture = "ui/scenes/hud.krui"),
                ),
            ),
        )

        val issues = validateTextureReferences(
            document = document,
            textureOptions = emptyList(),
            assetTypeByPath = mapOf("ui/scenes/hud.krui" to AssetType.UiScene),
        )

        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("not Texture"))
    }

    @Test
    internal fun `texture validation accepts known texture option`() {
        val document = UiSceneDocument(
            id = "textures",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(id = "heart", type = UiSceneNodeType.Image, texture = "textures/heart.png"),
                ),
            ),
        )

        val issues = validateTextureReferences(
            document = document,
            textureOptions = listOf(UiComposerTextureOption("Heart", "textures/heart.png")),
        )

        assertTrue(issues.isEmpty())
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

    @Test
    internal fun `addChildNode adds child to selected parent`() {
        val document = editableDocument()

        val updated = document.addChildNode(
            parentId = "panel",
            child = UiSceneNode(id = "button", type = UiSceneNodeType.TextButton, text = "Button"),
        )

        assertEquals("button", findUiSceneNodeById(updated.root, "button")?.id)
        assertNull(findUiSceneNodeById(document.root, "button"))
    }

    @Test
    internal fun `addChildNode does not add to missing parent`() {
        val document = editableDocument()

        val updated = document.addChildNode(
            parentId = "missing",
            child = UiSceneNode(id = "button", type = UiSceneNodeType.TextButton, text = "Button"),
        )

        assertEquals(document, updated)
        assertNull(findUiSceneNodeById(updated.root, "button"))
    }

    @Test
    internal fun `deleteNode deletes non-root node`() {
        val document = editableDocument()

        val updated = document.deleteNode("label")

        assertNull(findUiSceneNodeById(updated.root, "label"))
        assertNotNull(findUiSceneNodeById(document.root, "label"))
    }

    @Test
    internal fun `deleteNode does not delete root`() {
        val document = editableDocument()

        val updated = document.deleteNode("root")

        assertEquals(document, updated)
        assertEquals("root", updated.root.id)
    }

    @Test
    internal fun `duplicateNode duplicates selected node with new id`() {
        val document = editableDocument()

        val updated = document.duplicateNode("label", "label_copy")

        val panelChildren = findUiSceneNodeById(updated.root, "panel")?.children.orEmpty()
        assertEquals(listOf("label", "label_copy"), panelChildren.map { it.id })
        assertEquals("Original", findUiSceneNodeById(updated.root, "label_copy")?.text)
    }

    @Test
    internal fun `moveNodeUp swaps with previous sibling`() {
        val document = siblingDocument()

        val updated = document.moveNodeUp("third")

        val childIds = findUiSceneNodeById(updated.root, "panel")?.children.orEmpty().map { it.id }
        assertEquals(listOf("first", "third", "second"), childIds)
    }

    @Test
    internal fun `moveNodeDown swaps with next sibling`() {
        val document = siblingDocument()

        val updated = document.moveNodeDown("first")

        val childIds = findUiSceneNodeById(updated.root, "panel")?.children.orEmpty().map { it.id }
        assertEquals(listOf("second", "first", "third"), childIds)
    }

    @Test
    internal fun `moveNodeUp on first sibling leaves document unchanged`() {
        val document = siblingDocument()

        val updated = document.moveNodeUp("first")

        assertEquals(document, updated)
    }

    @Test
    internal fun `wrapNode wraps selected node in wrapper`() {
        val document = editableDocument()

        val updated = document.wrapNode(
            nodeId = "label",
            wrapper = UiSceneNode(id = "container", type = UiSceneNodeType.Container),
        )

        val wrapper = findUiSceneNodeById(updated.root, "container")
        assertNotNull(wrapper)
        assertEquals(listOf("label"), wrapper.children.map { it.id })
        val panelChildren = findUiSceneNodeById(updated.root, "panel")?.children.orEmpty()
        assertEquals(listOf("container"), panelChildren.map { it.id })
    }

    @Test
    internal fun `wrapNode does not wrap root`() {
        val document = editableDocument()

        val updated = document.wrapNode(
            nodeId = "root",
            wrapper = UiSceneNode(id = "container", type = UiSceneNodeType.Container),
        )

        assertEquals(document, updated)
        assertFalse(updated.containsNodeId("container"))
    }

    @Test
    internal fun `uniqueNodeId appends suffix when id exists`() {
        val document = editableDocument()

        assertEquals("label_1", document.uniqueNodeId("label"))
        assertEquals("awkward_id", document.uniqueNodeId(" awkward id! "))
    }

    @Test
    internal fun `createDefaultUiSceneNode creates sensible defaults`() {
        assertEquals(emptyList(), createDefaultUiSceneNode(UiSceneNodeType.Stack, "stack").children)
        assertEquals(8f, createDefaultUiSceneNode(UiSceneNodeType.Table, "table").spacing)
        assertEquals(UiSceneAlign.Center, createDefaultUiSceneNode(UiSceneNodeType.Container, "container").align)
        assertEquals("Label", createDefaultUiSceneNode(UiSceneNodeType.Label, "label").text)
        assertEquals("Button", createDefaultUiSceneNode(UiSceneNodeType.TextButton, "button").text)
        assertEquals("action.todo", createDefaultUiSceneNode(UiSceneNodeType.TextButton, "button").action)
        assertEquals(0.5f, createDefaultUiSceneNode(UiSceneNodeType.ProgressBar, "progress").value)
        assertNull(createDefaultUiSceneNode(UiSceneNodeType.Image, "image").texture)
        assertEquals(16f, createDefaultUiSceneNode(UiSceneNodeType.Space, "space").width)
        assertEquals(16f, createDefaultUiSceneNode(UiSceneNodeType.Space, "space").height)
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

    private fun siblingDocument(): UiSceneDocument =
        UiSceneDocument(
            id = "siblings",
            skin = "ui/skins/craftacular-ui.json",
            root = UiSceneNode(
                id = "root",
                type = UiSceneNodeType.Stack,
                children = listOf(
                    UiSceneNode(
                        id = "panel",
                        type = UiSceneNodeType.Table,
                        children = listOf(
                            UiSceneNode(id = "first", type = UiSceneNodeType.Label, text = "First"),
                            UiSceneNode(id = "second", type = UiSceneNodeType.Label, text = "Second"),
                            UiSceneNode(id = "third", type = UiSceneNodeType.Label, text = "Third"),
                        ),
                    ),
                ),
            ),
        )

    private fun descriptor(
        id: String,
        name: String,
        path: String,
        category: AssetCategory,
        type: AssetType,
        metadata: Map<String, String> = emptyMap(),
    ): AssetDescriptor =
        AssetDescriptor(
            id = AssetId(id),
            name = name,
            path = path,
            category = category,
            type = type,
            extension = path.substringAfterLast('.', ""),
            sizeBytes = 1L,
            modifiedAtMillis = 0L,
            metadata = metadata,
        )
}

private class FakeAssetRegistry(
    override val assets: List<AssetDescriptor>,
) : AssetRegistryService {
    override fun scanSnapshot(): AssetRegistrySnapshot =
        AssetRegistrySnapshot(assets, scannedAtMillis = 0L, durationMillis = 0L, errors = emptyList())

    override fun applySnapshot(snapshot: AssetRegistrySnapshot) = Unit

    override fun findById(id: AssetId): AssetDescriptor? =
        assets.firstOrNull { asset -> asset.id == id }

    override fun findByPath(path: String): AssetDescriptor? =
        assets.firstOrNull { asset -> asset.path == path }

    override fun byCategory(category: AssetCategory): List<AssetDescriptor> =
        assets.filter { asset -> asset.category == category }
}
