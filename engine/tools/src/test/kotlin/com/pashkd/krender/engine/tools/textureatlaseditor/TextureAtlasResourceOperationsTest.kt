package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.EngineLogService
import com.pashkd.krender.engine.api.EventBus
import com.pashkd.krender.engine.api.FrameProfilerService
import com.pashkd.krender.engine.api.FrameRuntimeStatsService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.LogService
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.SceneManager
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.NoOpAssetRegistryService
import com.pashkd.krender.engine.scene.DefaultSceneFileService
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.NoOpUiService
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.test.NoOpRuntimeUiBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TextureAtlasResourceOperationsTest {
    @Test
    fun `create nine patch converts selected image resource in place`() {
        val root = createTempDir(prefix = "krender-atlas-resource-ops-test-")
        try {
            val textureFile = File(root, "button.png")
            ImageIO.write(BufferedImage(4, 6, BufferedImage.TYPE_INT_ARGB), "png", textureFile)
            val state =
                TextureAtlasEditorState(
                    resources =
                        TextureAtlasResourceState(
                            items =
                                listOf(
                                    ImageAtlasResource(
                                        id = "button",
                                        name = "button",
                                        sourcePath = normalizePath(textureFile.path),
                                    ),
                                ),
                            selectedResourceId = "button",
                        ),
                )
            val operations =
                TextureAtlasResourceOperations(
                    state = state,
                    engine = TestTextureAtlasEngineContext(root),
                    selectionCoordinator = TextureAtlasEditorSelectionCoordinator(state),
                    importTexture = {},
                    selectResource = { state.resources.selectedResourceId = it },
                )

            operations.createNinePatchFromSelectedResource()

            val converted = state.selectedResource()
            assertIs<NinePatchAtlasResource>(converted)
            assertEquals(listOf(0, 0, 0, 0), converted.split)
            assertEquals(4, converted.sourceWidth)
            assertEquals(6, converted.sourceHeight)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `rename selected resource updates atlas region and marks editor dirty`() {
        val root = createTempDir(prefix = "krender-atlas-resource-rename-test-")
        try {
            val atlasPath = normalizePath(File(root, "ui.atlas").path)
            val regionId = AtlasRegionId(atlasPath = atlasPath, pageName = "ui.png", regionName = "button")
            val state =
                TextureAtlasEditorState(
                    selectedAssetId = TextureAssetId(atlasPath),
                    project =
                        TextureAtlasEditorProject(
                            assets =
                                listOf(
                                    TextureAtlasEditorAssetDescriptor(
                                        id = TextureAssetId(atlasPath),
                                        path = atlasPath,
                                        displayName = "ui.atlas",
                                        kind = TextureAtlasEditorAssetKind.Atlas,
                                        extension = "atlas",
                                        sizeBytes = 0,
                                        modifiedAtMillis = 0,
                                    ),
                                ),
                            atlasDocuments =
                                mapOf(
                                    atlasPath to
                                        TextureAtlasDocument(
                                            file = File(atlasPath),
                                            pages = listOf(TextureAtlasPage("ui.png")),
                                            regions = listOf(TextureAtlasRegion(id = regionId)),
                                        ),
                                ),
                        ),
                    resources =
                        TextureAtlasResourceState(
                            items =
                                listOf(
                                    ImageAtlasResource(
                                        id = "button",
                                        name = "button",
                                        sourcePath = atlasPath,
                                        atlasRegionId = regionId,
                                    ),
                                ),
                            selectedResourceId = "button",
                        ),
                    selectedRegionId = regionId,
                )
            val operations =
                TextureAtlasResourceOperations(
                    state = state,
                    engine = TestTextureAtlasEngineContext(root),
                    selectionCoordinator = TextureAtlasEditorSelectionCoordinator(state),
                    importTexture = {},
                    selectResource = { state.resources.selectedResourceId = it },
                )

            operations.renameSelectedResource("button_primary")

            val renamed = state.selectedResource() as ImageAtlasResource
            assertEquals("button_primary", renamed.name)
            assertEquals("button_primary", renamed.atlasRegionId?.regionName)
            assertEquals("button_primary", state.selectedRegionId?.regionName)
            assertEquals(
                "button_primary",
                state
                    .selectedAtlasDocument()
                    ?.regions
                    ?.firstOrNull()
                    ?.id
                    ?.regionName,
            )
            assertTrue(state.dirty)
        } finally {
            root.deleteRecursively()
        }
    }
}

private class TestTextureAtlasEngineContext(
    root: File,
) : EngineContext {
    override val scenes: SceneManager = SceneManager()
    override val assets: AssetService = NoOpAtlasAssetService
    override val assetRegistry: AssetRegistryService = NoOpAssetRegistryService(root)
    override val sceneFiles: SceneFileService = DefaultSceneFileService
    override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
    override val editorToolLauncher: EditorToolLauncher = UnusedAtlasEditorToolLauncher
    override val input: InputService = NoOpAtlasInputService
    override val ui: UiService = NoOpUiService()
    override val logger: Logger = EngineLogService()
    override val logs: LogService = logger as LogService
    override val runtimeUi: RuntimeUiService = RuntimeUiService(NoOpRuntimeUiBackend(), logger)
    override val events: EventBus = EventBus()
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val profiler = FrameProfilerService()
    override val tasks: TaskService = UnusedAtlasTaskService
    override val viewport: RuntimeViewportService = RuntimeViewportService()
    override val window: WindowService = InMemoryWindowService()
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null

    override fun requestExit() = Unit
}

private object NoOpAtlasAssetService : AssetService {
    override fun queue(asset: AssetRef<*>) = Unit

    override fun update(budgetMs: Int): Float = 1f

    override fun isLoaded(asset: AssetRef<*>): Boolean = false

    override fun <T : Any> get(asset: AssetRef<T>): T = error("not used")

    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? = null

    override fun unload(asset: AssetRef<*>) = Unit
}

private object NoOpAtlasInputService : InputService {
    override fun beginFrame() = Unit

    override fun snapshot(): InputSnapshot = InputSnapshot()

    override fun endFrame() = Unit

    override fun setCursorCaptured(captured: Boolean) = Unit

    override fun isActionPressed(action: Action): Boolean = false

    override fun isActionJustPressed(action: Action): Boolean = false

    override fun axis(axis: Axis): Float = 0f
}

private object UnusedAtlasEditorToolLauncher : EditorToolLauncher {
    override fun launchModelViewer(modelPath: String) = error("not used")

    override fun launchAnimationViewer(modelPath: String) = error("not used")

    override fun launchTerrainEditor(terrainPath: String) = error("not used")

    override fun launchSceneEditorWithScene(scenePath: String) = error("not used")

    override fun launchSkinEditor(skinPath: String?) = error("not used")

    override fun launchTextureAtlasEditor(atlasPath: String) = error("not used")

    override fun launchUiComposer(uiScenePath: String) = error("not used")

    override fun launchBitmapFontEditor(fontPath: String?) = error("not used")
}

private object UnusedAtlasTaskService : TaskService {
    override val inFlightJobs: Int = 0

    override fun launchBackground(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = error("not used")

    override suspend fun <T> onBackground(block: suspend () -> T): T = error("not used")

    override suspend fun <T> onIo(block: suspend () -> T): T = error("not used")

    override suspend fun <T> onMain(block: suspend () -> T): T = error("not used")

    override fun postToMain(block: () -> Unit) = error("not used")

    override fun flushMainThreadQueue() = Unit

    override fun dispose() = Unit
}
