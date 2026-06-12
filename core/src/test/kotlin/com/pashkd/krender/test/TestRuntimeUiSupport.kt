package com.pashkd.krender.test

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetId
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetRegistrySnapshot
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.ui.runtime.RuntimeUiLayerState
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService
import java.io.File

object NoOpTestLogger : Logger {
    override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
}

/** Empty in-memory [AssetRegistryService] for tests that only need the dependency present. */
class NoOpAssetRegistryService : AssetRegistryService {
    override val assets: List<AssetDescriptor> = emptyList()
    override fun scanSnapshot(): AssetRegistrySnapshot =
        AssetRegistrySnapshot(assets = emptyList(), scannedAtMillis = 0L, durationMillis = 0L, errors = emptyList())
    override fun applySnapshot(snapshot: AssetRegistrySnapshot) = Unit
    override fun findById(id: AssetId): AssetDescriptor? = null
    override fun findByPath(path: String): AssetDescriptor? = null
    override fun byCategory(category: AssetCategory): List<AssetDescriptor> = emptyList()
    override fun baseDir(): File = File(".")
}

class NoOpRuntimeUiBackend : RuntimeUiBackend {
    override fun setActionHandler(handler: RuntimeUiActionHandler?) = Unit

    override fun syncLayers(layers: List<RuntimeUiLayerState>) = Unit

    override fun update(dt: Float) = Unit

    override fun render() = Unit

    override fun resize(width: Int, height: Int) = Unit

    override fun clear() = Unit

    override fun dispose() = Unit
}

fun newTestRuntimeUiService(logger: Logger = NoOpTestLogger): RuntimeUiService =
    RuntimeUiService(NoOpRuntimeUiBackend(), logger)
