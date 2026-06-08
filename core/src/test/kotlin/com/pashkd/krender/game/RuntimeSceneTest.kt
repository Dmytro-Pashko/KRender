package com.pashkd.krender.game

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.ui.editor.NoOpUiService
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.test.newTestRuntimeUiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeSceneTest {
    @Test
    fun `runtime scene with terrain and skybox does not create runtime sun or ambient light`() {
        val assets = TestAssetService()
        val scene = RuntimeScene("scenes/runtime.krscene")
        val context = TestEngineContext(
            assets = assets,
            files = runtimeFiles(sceneDescriptor()),
        )

        context.scenes.replace(scene)
        context.scenes.applyPendingTransitions(context)

        assertTrue(scene.world.all().none { entity -> entity.get<LightComponent>() != null })
        assertTrue(assets.queued.any { asset -> asset.type == TextureAsset::class && asset.path == "textures/runtime_skybox.png" })
    }

    @Test
    fun `runtime scene starts without terrain when active terrain is missing`() {
        val assets = TestAssetService()
        val scene = RuntimeScene("scenes/runtime_no_terrain.krscene")
        val context = TestEngineContext(
            assets = assets,
            files = runtimeFiles(
                sceneDescriptor(
                    scenePath = "scenes/runtime_no_terrain.krscene",
                    activeTerrainEntityId = null,
                ),
            ),
        )

        context.scenes.replace(scene)
        context.scenes.applyPendingTransitions(context)

        assertTrue(assets.queued.none { asset -> asset.path.startsWith("terrains/") })
        assertTrue(scene.world.all().none { entity -> entity.get<LightComponent>() != null })
    }

    @Test
    fun `runtime scene starts without skybox when skybox is disabled`() {
        val assets = TestAssetService()
        val scene = RuntimeScene("scenes/runtime_no_skybox.krscene")
        val context = TestEngineContext(
            assets = assets,
            files = runtimeFiles(
                sceneDescriptor(
                    scenePath = "scenes/runtime_no_skybox.krscene",
                    showSkybox = false,
                    skyboxAssetPath = null,
                ),
            ),
        )

        context.scenes.replace(scene)
        context.scenes.applyPendingTransitions(context)

        assertTrue(assets.queued.none { asset -> asset.type == TextureAsset::class && asset.path == "textures/runtime_skybox.png" })
    }

    private fun runtimeFiles(descriptor: SceneDescriptor): Map<String, String> {
        val terrain = TerrainData(width = 2, height = 2, vertexSpacing = 1f).also { data ->
            val layer = data.addLayer(
                name = "Base",
                materialId = "terrain/grass",
                color = TerrainLayerColorDescriptor(0.2f, 0.6f, 0.3f, 1f),
                visible = true,
            )
            for (y in 0 until data.height) {
                for (x in 0 until data.width) {
                    data.setLayerWeight(layer.id, x, y, 1f)
                }
            }
        }
        return buildMap {
            put(descriptorPath(descriptor), SceneSerializer.encode(descriptor))
            put(
                "skyboxes/runtime.krskybox",
                SkyboxAssetSerializer.encode(
                    SkyboxAssetDescriptor(
                        id = "skybox:runtime",
                        name = "Runtime Skybox",
                        texturePath = "textures/runtime_skybox.png",
                        intensity = 1f,
                    ),
                ),
            )
            put("terrains/runtime_terrain.json", TerrainPersistence().encode(terrain, "Runtime Terrain"))
            put(
                DefaultTerrainMaterialLibraryPath,
                """
                    {
                      "formatVersion": 1,
                      "materials": [
                        {
                          "id": "terrain/grass",
                          "name": "Grass",
                          "albedoTexture": "textures/grass.png",
                          "fallbackColor": { "r": 0.2, "g": 0.6, "b": 0.3, "a": 1.0 },
                          "defaultTiling": 8.0
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }
    }

    private fun sceneDescriptor(
        scenePath: String = "scenes/runtime.krscene",
        activeTerrainEntityId: Long? = 2L,
        showSkybox: Boolean = true,
        skyboxAssetPath: String? = "skyboxes/runtime.krskybox",
    ): SceneDescriptor =
        SceneDescriptor(
            id = "scene:${scenePath.substringAfterLast('/').substringBeforeLast('.')}",
            name = "Runtime",
            entities = buildList {
                add(
                    EntityDescriptor(
                        id = 1L,
                        name = "Camera",
                        components = listOf(
                            ComponentDescriptor(
                                type = SceneComponentTypes.Transform,
                                properties = mapOf(
                                    "position" to "0,1,6",
                                    "rotation" to "0,180,0",
                                    "scale" to "1,1,1",
                                ),
                            ),
                            ComponentDescriptor(
                                type = SceneComponentTypes.Camera,
                                properties = mapOf(
                                    "fieldOfViewDegrees" to "60",
                                    "near" to "0.1",
                                    "far" to "500",
                                ),
                            ),
                        ),
                    ),
                )
                if (activeTerrainEntityId != null) {
                    add(
                        EntityDescriptor(
                            id = activeTerrainEntityId,
                            name = "Terrain",
                            components = listOf(
                                ComponentDescriptor(
                                    type = SceneComponentTypes.Terrain,
                                    properties = mapOf(
                                        "terrain" to "terrains/runtime_terrain.json",
                                        "visible" to "true",
                                        "previewMode" to "LayerColor",
                                        "bakedTextureResolution" to "512",
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            },
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = 1L,
                activeTerrainEntityId = activeTerrainEntityId,
                environment = SceneEnvironmentDescriptor(
                    skyboxAssetPath = skyboxAssetPath,
                    showSkybox = showSkybox,
                    environmentIntensity = 1f,
                ),
            ),
        )

    private fun descriptorPath(descriptor: SceneDescriptor): String =
        "scenes/${descriptor.id.substringAfter(':')}.krscene"
}

private class TestEngineContext(
    files: Map<String, String>,
    assets: TestAssetService = TestAssetService(),
) : EngineContext {
    override val scenes: SceneManager = SceneManager()
    override val assets: AssetService = assets
    override val sceneFiles: SceneFileService = TestSceneFileService(files)
    override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
    override val editorToolLauncher: EditorToolLauncher = UnsupportedEditorToolLauncher
    override val logger: Logger = EngineLogService()
    override val logs: LogService = logger as LogService
    override val input: InputService = TestInputService
    override val ui: UiService = NoOpUiService()
    override val runtimeUi = newTestRuntimeUiService(logger)
    override val events: EventBus = EventBus()
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val profiler: ProfilerService = FrameProfilerService()
    override val tasks: TaskService = TestTaskService
    override val viewport: RuntimeViewportService = RuntimeViewportService()
    override val window: WindowService = InMemoryWindowService()
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null

    override fun requestExit() = Unit
}

private class TestAssetService : AssetService {
    val queued = mutableListOf<AssetRef<*>>()

    override fun queue(asset: AssetRef<*>) {
        queued += asset
    }

    override fun update(budgetMs: Int): Float = 1f

    override fun isLoaded(asset: AssetRef<*>): Boolean = true

    override fun <T : Any> get(asset: AssetRef<T>): T =
        error("Test asset service does not resolve assets")

    override fun unload(asset: AssetRef<*>) = Unit
}

private class TestSceneFileService(
    private val files: Map<String, String>,
) : SceneFileService {
    override fun writeText(path: String, text: String) = error("Test scene files are read-only")

    override fun readText(path: String): String =
        files[path] ?: error("Missing test scene file '$path'")

    override fun ensureDirectories(path: String) = Unit

    override fun exists(path: String): Boolean = files.containsKey(path)

    override fun describeReadableSource(path: String): String = if (exists(path)) "test" else "missing"
}

private object TestInputService : InputService {
    override fun beginFrame() = Unit
    override fun snapshot(): InputSnapshot = InputSnapshot()
    override fun endFrame() = Unit
    override fun setCursorCaptured(captured: Boolean) = Unit
    override fun isActionPressed(action: Action): Boolean = false
    override fun isActionJustPressed(action: Action): Boolean = false
    override fun axis(axis: Axis): Float = 0f
}

private object TestTaskService : TaskService {
    override val inFlightJobs: Int = 0

    override fun launchBackground(name: String, block: suspend CoroutineScope.() -> Unit): Job = Job()

    override suspend fun <T> onBackground(block: suspend () -> T): T = block()

    override suspend fun <T> onIo(block: suspend () -> T): T = block()

    override suspend fun <T> onMain(block: suspend () -> T): T = block()

    override fun postToMain(block: () -> Unit) = block()

    override fun flushMainThreadQueue() = Unit

    override fun dispose() = Unit
}
