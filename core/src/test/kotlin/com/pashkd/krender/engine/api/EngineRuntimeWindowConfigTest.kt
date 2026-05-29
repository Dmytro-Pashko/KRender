package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.NoOpUiService
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.viewport.RuntimeViewportConfig
import com.pashkd.krender.engine.viewport.UiScalePolicy
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.RuntimeWindowConfig
import com.pashkd.krender.engine.window.WindowResolution
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.engine.window.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineRuntimeWindowConfigTest {
    @Test
    fun `start applies initial scene window and viewport config immediately`() {
        val backend = FakeEngineBackend(initialWindowState = WindowState(pixelWidth = 800, pixelHeight = 600))
        val runtime = EngineRuntime(backend)
        val scene = TestScene(
            id = "asset_browser",
            config = SceneConfig(
                viewport = RuntimeViewportConfig(
                    designWidth = 1220f,
                    designHeight = 900f,
                    scalePolicy = UiScalePolicy.PixelPerfect,
                ),
                window = RuntimeWindowConfig(
                    resolution = WindowResolution(width = 1220, height = 900),
                ),
            ),
        )

        runtime.start(scene)

        assertEquals(1, scene.showCallCount)
        assertEquals(listOf(1220 to 900), scene.resizeCalls)
        assertEquals(1220, runtime.window.current.pixelWidth)
        assertEquals(900, runtime.window.current.pixelHeight)
        assertEquals(1220, runtime.viewport.current.pixelWidth)
        assertEquals(900, runtime.viewport.current.pixelHeight)
        assertEquals(1220f, runtime.viewport.current.logicalWidth)
        assertEquals(900f, runtime.viewport.current.logicalHeight)
    }

    @Test
    fun `pop restores previous scene window config`() {
        val backend = FakeEngineBackend(initialWindowState = WindowState(pixelWidth = 800, pixelHeight = 600))
        val runtime = EngineRuntime(backend)
        val runtimeScene = TestScene(id = "runtime_scene")
        val assetBrowserScene = TestScene(
            id = "asset_browser",
            config = SceneConfig(
                viewport = RuntimeViewportConfig(
                    designWidth = 1220f,
                    designHeight = 900f,
                    scalePolicy = UiScalePolicy.PixelPerfect,
                ),
                window = RuntimeWindowConfig(
                    resolution = WindowResolution(width = 1220, height = 900),
                ),
            ),
        )

        runtime.start(runtimeScene)
        runtime.scenes.push(assetBrowserScene)
        runtime.scenes.applyPendingTransitions(runtime)
        assertEquals(1220, runtime.window.current.pixelWidth)
        assertEquals(900, runtime.window.current.pixelHeight)

        runtime.scenes.pop()
        runtime.scenes.applyPendingTransitions(runtime)

        assertEquals(1920, runtime.window.current.pixelWidth)
        assertEquals(1080, runtime.window.current.pixelHeight)
        assertEquals(listOf(1920 to 1080, 1920 to 1080), runtimeScene.resizeCalls)
    }
}

private class TestScene(
    id: String,
    override val config: SceneConfig = SceneConfig(),
) : Scene(id) {
    var showCallCount: Int = 0
    val resizeCalls = mutableListOf<Pair<Int, Int>>()

    override fun show() {
        showCallCount += 1
    }

    override fun resize(width: Int, height: Int) {
        resizeCalls += width to height
    }
}

private class FakeEngineBackend(
    initialWindowState: WindowState = WindowState(),
) : EngineBackend {
    override val input: InputService = object : InputService {
        override fun beginFrame() = Unit
        override fun snapshot(): InputSnapshot = InputSnapshot()
        override fun endFrame() = Unit
        override fun setCursorCaptured(captured: Boolean) = Unit
        override fun isActionPressed(action: Action): Boolean = false
        override fun isActionJustPressed(action: Action): Boolean = false
        override fun axis(axis: Axis): Float = 0f
    }
    override val ui: UiService = NoOpUiService()
    override val assets: AssetService = object : AssetService {
        override fun queue(asset: AssetRef<*>) = Unit
        override fun update(budgetMs: Int): Float = 1f
        override fun isLoaded(asset: AssetRef<*>): Boolean = true
        override fun <T : Any> get(asset: AssetRef<T>): T = error("unused")
        override fun unload(asset: AssetRef<*>) = Unit
    }
    override val sceneFiles: SceneFileService = object : SceneFileService {
        override fun writeText(path: String, text: String) = Unit
        override fun readText(path: String): String = error("unused")
        override fun ensureDirectories(path: String) = Unit
        override fun exists(path: String): Boolean = false
    }
    override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
    override val editorToolLauncher: EditorToolLauncher = UnsupportedEditorToolLauncher
    override val logger: Logger = EngineLogService()
    override val logs: LogService = logger as LogService
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val profiler: ProfilerService = FrameProfilerService()
    override val tasks: TaskService = object : TaskService {
        override val inFlightJobs: Int = 0
        override fun launchBackground(name: String, block: suspend CoroutineScope.() -> Unit): Job = Job()
        override suspend fun <T> onBackground(block: suspend () -> T): T = block()
        override suspend fun <T> onIo(block: suspend () -> T): T = block()
        override suspend fun <T> onMain(block: suspend () -> T): T = block()
        override fun postToMain(block: () -> Unit) = block()
        override fun flushMainThreadQueue() = Unit
        override fun dispose() = Unit
    }
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null
    override val window: WindowService = InMemoryWindowService(initialWindowState)
    override val renderer: Renderer = object : Renderer {
        override fun render(context: RenderContext) = Unit
        override fun resize(width: Int, height: Int) = Unit
        override fun dispose() = Unit
    }

    override fun requestExit() = Unit
}
