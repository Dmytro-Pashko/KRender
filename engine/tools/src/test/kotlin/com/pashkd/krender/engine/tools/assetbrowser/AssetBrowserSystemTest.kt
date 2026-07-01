package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetRegistryError
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetRegistrySnapshot
import com.pashkd.krender.engine.assets.AssetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetBrowserSystemTest {
    @Test
    fun `selected model status shows load failure`() {
        val descriptor =
            AssetDescriptor(
                id = AssetId("asset:model:test"),
                name = "broken",
                path = "model/tests/broken.glb",
                category = AssetCategory.Model,
                type = AssetType.GltfModel,
                extension = "glb",
                sizeBytes = 1L,
                modifiedAtMillis = 1L,
            )
        val registry = FakeAssetRegistryService(listOf(descriptor))
        val state =
            AssetBrowserState(
                assets = listOf(descriptor),
                selectedAssetId = descriptor.id,
            )
        val system =
            AssetBrowserSystem(
                registry = registry,
                assets = FakeAssetService(loadFailure = "Unsupported glTF extension."),
                tasks = ImmediateTaskService,
                logger = NoopLogger,
                state = state,
                onAssetActivated = {},
            )
        val world = SceneWorld()
        world.systems.add(system)

        world.update(0.016f)

        assertEquals("Failed: Unsupported glTF extension.", state.selectedModelStatus)
        assertEquals(null, state.selectedModelInfo)
    }
}

private class FakeAssetRegistryService(
    private val initialAssets: List<AssetDescriptor>,
) : AssetRegistryService {
    override var assets: List<AssetDescriptor> = initialAssets

    override fun scanSnapshot(): AssetRegistrySnapshot =
        AssetRegistrySnapshot(
            assets = initialAssets,
            scannedAtMillis = 1L,
            durationMillis = 0L,
            errors = emptyList<AssetRegistryError>(),
        )

    override fun applySnapshot(snapshot: AssetRegistrySnapshot) {
        assets = snapshot.assets
    }

    override fun findById(id: AssetId): AssetDescriptor? = assets.firstOrNull { it.id == id }

    override fun findByPath(path: String): AssetDescriptor? = assets.firstOrNull { it.path == path }

    override fun byCategory(category: AssetCategory): List<AssetDescriptor> = assets.filter { it.category == category }

    override fun baseDir(): File = File(".")
}

private class FakeAssetService(
    private val loadFailure: String? = null,
) : AssetService {
    override fun queue(asset: AssetRef<*>) = Unit

    override fun update(budgetMs: Int): Float = 0f

    override fun progress(): Float = 0f

    override fun isLoaded(asset: AssetRef<*>): Boolean = false

    override fun loadFailure(asset: AssetRef<*>): String? = loadFailure

    override fun <T : Any> get(asset: AssetRef<T>): T = error("Not used in test")

    override fun modelInfo(asset: AssetRef<ModelAsset>) = null

    override fun unload(asset: AssetRef<*>) = Unit
}

private object ImmediateTaskService : TaskService {
    override val inFlightJobs: Int = 0

    override fun launchBackground(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = Job()

    override suspend fun <T> onBackground(block: suspend () -> T): T = block()

    override suspend fun <T> onIo(block: suspend () -> T): T = block()

    override suspend fun <T> onMain(block: suspend () -> T): T = block()

    override fun postToMain(block: () -> Unit) = block()

    override fun flushMainThreadQueue() = Unit

    override fun dispose() = Unit
}

private object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) = Unit
}
