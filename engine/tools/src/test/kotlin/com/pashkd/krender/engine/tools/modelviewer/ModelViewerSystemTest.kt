package com.pashkd.krender.engine.tools.modelviewer

import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ModelViewerSystemTest {
    @Test
    fun `reload request unloads and requeues the model`() {
        val model = AssetRef.model("models/test.glb")
        val assets = FakeAssetService(loaded = true, modelInfo = modelInfo(model.path))
        val state =
            ModelViewerState(model = model).apply {
                reloadRequested = true
                selectedMeshPartIndex = 2
                selectedMaterialIndex = 1
                selectedTextureChannel = "normal"
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(ModelViewerSystem(NoOpInputService, assets, NoopLogger, state, onExitRequested = {}))

        world.update(0.016f)

        assertEquals(listOf(model.path), assets.unloadedPaths)
        assertEquals(listOf(model.path), assets.queuedPaths)
        assertFalse(state.reloadRequested)
        assertFalse(state.assetLoaded)
        assertEquals("Reloading", state.loadingStatus)
        assertEquals("Reloading model from disk...", state.statusMessage)
        assertNull(state.modelInfo)
        assertNull(state.selectedMeshPartIndex)
        assertNull(state.selectedMaterialIndex)
        assertNull(state.selectedTextureChannel)
    }

    @Test
    fun `ambient light sync applies Legacy ambient intensity`() {
        val state =
            ModelViewerState(model = AssetRef.model("models/test.glb")).apply {
                ambientLightIntensity = 0.5f
                rendererMode = ModelViewerRendererMode.LibGdx
            }
        val world = SceneWorld()
        val lightEntity = world.createEntity("Ambient")
        lightEntity.add(LightComponent(type = LightType.Ambient, intensity = 1f))
        state.ambientLightEntityId = lightEntity.id
        world.systems.add(ModelViewerSystem(NoOpInputService, FakeAssetService(), NoopLogger, state, onExitRequested = {}))

        world.update(0.016f)

        assertEquals(0.5f, lightEntity.get<LightComponent>()?.intensity)
    }

    @Test
    fun `load failure is surfaced in viewer state`() {
        val model = AssetRef.model("models/broken.glb")
        val state = ModelViewerState(model = model)
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            ModelViewerSystem(
                NoOpInputService,
                FakeAssetService(loadFailure = "Broken glTF animation channel."),
                NoopLogger,
                state,
                onExitRequested = {},
            ),
        )

        world.update(0.016f)

        assertFalse(state.assetLoaded)
        assertEquals("Failed", state.loadingStatus)
        assertEquals("Broken glTF animation channel.", state.errorMessage)
    }

    private fun modelInfo(path: String): ModelAssetInfo =
        ModelAssetInfo(
            path = path,
            format = "glTF",
            nodeCount = 1,
            meshCount = 1,
            meshPartCount = 1,
            materialCount = 1,
            vertexCount = 3,
            triangleCount = 1,
            size = null,
            vertexChannels = emptyList(),
            uvChannels = emptyList(),
            textureChannels = emptyList(),
            textureCount = 0,
            textureSlotCount = 0,
            hasSkeleton = false,
            boneCount = 0,
            boneWeightChannelCount = 0,
        )
}

private class FakeAssetService(
    private val loaded: Boolean = false,
    private val loadFailure: String? = null,
    private val modelInfo: ModelAssetInfo? = null,
) : AssetService {
    val queuedPaths = mutableListOf<String>()
    val unloadedPaths = mutableListOf<String>()

    override fun queue(asset: AssetRef<*>) {
        queuedPaths += asset.path
    }

    override fun update(budgetMs: Int): Float = if (loaded) 1f else 0f

    override fun progress(): Float = if (loaded) 1f else 0f

    override fun isLoaded(asset: AssetRef<*>): Boolean = loaded

    override fun loadFailure(asset: AssetRef<*>): String? = loadFailure

    override fun <T : Any> get(asset: AssetRef<T>): T = error("Not used in test")

    override fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo? = modelInfo

    override fun unload(asset: AssetRef<*>) {
        unloadedPaths += asset.path
    }
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

private object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) = Unit
}
