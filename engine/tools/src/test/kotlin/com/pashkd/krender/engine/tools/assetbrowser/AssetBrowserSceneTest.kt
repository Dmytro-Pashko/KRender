package com.pashkd.krender.engine.tools.assetbrowser

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
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.NoOpAssetRegistryService
import com.pashkd.krender.engine.scene.DefaultSceneFileService
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.ui.NoOpUiService
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService
import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.test.newTestRuntimeUiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetBrowserSceneTest {
    @Test
    fun `default terrain content creates valid empty flat 64x64 terrain`() {
        val content = defaultTerrainContent("woolboy_terrain_sandbox")
        val descriptor = TerrainPersistence().decodeDescriptor(content)

        assertEquals("woolboy_terrain_sandbox", descriptor.name)
        assertEquals(64, descriptor.terrain.width)
        assertEquals(64, descriptor.terrain.height)
        assertEquals(1f, descriptor.terrain.vertexSpacing)
        assertEquals(64 * 64, descriptor.terrain.heights.size)
        assertTrue(descriptor.terrain.layers.isEmpty())
        assertContentEquals(FloatArray(64 * 64), descriptor.terrain.heights)
    }

    @Test
    fun `ui composer asset tool routes UiScene path to editor launcher`() {
        val launcher = RecordingEditorToolLauncher()
        val context = TestToolEngineContext(launcher)
        val asset =
            AssetDescriptor(
                id = AssetId("asset:ui-scene"),
                name = "woolboy_hud",
                path = "ui/scenes/woolboy_hud.krui",
                category = AssetCategory.UI,
                type = AssetType.UiScene,
                extension = "krui",
                sizeBytes = 12L,
                modifiedAtMillis = 1L,
            )

        val tool = UiComposerAssetTool()

        assertTrue(tool.canOpen(asset))
        tool.open(asset, context)
        assertEquals("ui/scenes/woolboy_hud.krui", launcher.uiComposerPath)
    }

    @Test
    fun `default ui scene content writes provided skin path`() {
        val content = defaultUiSceneContent("inventory screen", "ui\\skins\\default_ui.json")
        val document = UiSceneSerializer().decode(content)

        assertEquals("inventory_screen", document.id)
        assertEquals("ui/skins/default_ui.json", document.skin)
    }

    @Test
    fun `default ui scene content keeps craftacular fallback`() {
        val content = defaultUiSceneContent("hud")
        val document = UiSceneSerializer().decode(content)

        assertEquals(DefaultUiSceneSkinPath, document.skin)
    }
}

private class RecordingEditorToolLauncher : EditorToolLauncher {
    var uiComposerPath: String? = null

    override fun launchModelViewer(modelPath: String) = error("not used")

    override fun launchAnimationViewer(modelPath: String) = error("not used")

    override fun launchTerrainEditor(terrainPath: String) = error("not used")

    override fun launchSceneEditorWithScene(scenePath: String) = error("not used")

    override fun launchUiComposer(uiScenePath: String) {
        uiComposerPath = uiScenePath
    }
}

private class TestToolEngineContext(
    override val editorToolLauncher: EditorToolLauncher,
) : EngineContext {
    override val scenes: SceneManager = SceneManager()
    override val assets: AssetService = NoOpAssetService
    override val assetRegistry: AssetRegistryService = NoOpAssetRegistryService()
    override val sceneFiles: SceneFileService = DefaultSceneFileService
    override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
    override val input: InputService = NoOpInputService
    override val ui: UiService = NoOpUiService()
    override val logger: Logger = EngineLogService()
    override val logs: LogService = logger as LogService
    override val runtimeUi: RuntimeUiService = newTestRuntimeUiService(logger)
    override val events: EventBus = EventBus()
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val profiler = FrameProfilerService()
    override val tasks: TaskService = UnusedTaskService
    override val viewport: RuntimeViewportService = RuntimeViewportService()
    override val window: WindowService = InMemoryWindowService()
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null

    override fun requestExit() = Unit
}

private object NoOpAssetService : AssetService {
    override fun queue(asset: AssetRef<*>) = Unit

    override fun update(budgetMs: Int): Float = 1f

    override fun isLoaded(asset: AssetRef<*>): Boolean = false

    override fun <T : Any> get(asset: AssetRef<T>): T = error("not used")

    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? = null

    override fun unload(asset: AssetRef<*>) = Unit
}

private object NoOpInputService : InputService {
    override fun beginFrame() = Unit

    override fun snapshot(): InputSnapshot = InputSnapshot()

    override fun endFrame() = Unit

    override fun setCursorCaptured(captured: Boolean) = Unit

    override fun isActionPressed(action: Action): Boolean = false

    override fun isActionJustPressed(action: Action): Boolean = false

    override fun axis(axis: Axis): Float = 0f
}

private object UnusedTaskService : TaskService {
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
