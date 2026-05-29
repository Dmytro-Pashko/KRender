package com.pashkd.krender.game

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.NoOpUiService
import com.pashkd.krender.engine.ui.UiService
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.WindowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class RuntimeSceneBuilderTest {
    @Test
    fun `builds camera-only runtime scene without terrain or skybox`() {
        val descriptor = SceneDescriptor(
            id = "scene:builder",
            name = "Builder",
            entities = listOf(
                EntityDescriptor(
                    id = 1L,
                    name = "Camera",
                    components = listOf(
                        ComponentDescriptor(SceneComponentTypes.Transform),
                        ComponentDescriptor(SceneComponentTypes.Camera),
                    ),
                ),
            ),
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = 1L,
                activeTerrainEntityId = null,
                environment = SceneEnvironmentDescriptor(
                    skyboxAssetPath = null,
                    showSkybox = false,
                ),
            ),
        )
        val world = SceneWorld()

        val result = RuntimeSceneBuilder(BuilderTestEngineContext()).build(
            world = world,
            request = RuntimeSceneBuildRequest(
                scenePath = "scenes/builder.krscene",
                descriptor = descriptor,
                skybox = null,
            ),
        )

        assertEquals(1L, result.activeCameraEntityId)
        assertFalse(result.terrainPrepared)
        assertFalse(result.skyboxEnabled)
        assertEquals(0, result.validationReport.errors.size)
        assertNotNull(world.getEntity(1L)?.get<ActiveCameraComponent>())
    }
}

private class BuilderTestEngineContext : EngineContext {
    override val scenes: SceneManager = SceneManager()
    override val assets: AssetService = object : AssetService {
        override fun queue(asset: com.pashkd.krender.engine.api.AssetRef<*>) = Unit
        override fun update(budgetMs: Int): Float = 1f
        override fun isLoaded(asset: com.pashkd.krender.engine.api.AssetRef<*>): Boolean = true
        override fun <T : Any> get(asset: com.pashkd.krender.engine.api.AssetRef<T>): T = error("unused")
        override fun unload(asset: com.pashkd.krender.engine.api.AssetRef<*>) = Unit
    }
    override val sceneFiles: SceneFileService = object : SceneFileService {
        override fun writeText(path: String, text: String) = Unit
        override fun readText(path: String): String = error("unused")
        override fun ensureDirectories(path: String) = Unit
        override fun exists(path: String): Boolean = false
    }
    override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
    override val editorToolLauncher: EditorToolLauncher = UnsupportedEditorToolLauncher
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
    override val events: EventBus = EventBus()
    override val logger = EngineLogService()
    override val logs: LogService = logger
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
    override val viewport: RuntimeViewportService = RuntimeViewportService()
    override val window: WindowService = InMemoryWindowService()
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null

    override fun requestExit() = Unit
}
