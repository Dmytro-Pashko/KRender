package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.common.EditorAssetPickerCatalog
import com.pashkd.krender.engine.tools.common.EditorAssetPickerOption

class SceneEditorAssetCatalog(
    private val registry: AssetRegistryService,
    private val tasks: TaskService,
    private val logger: Logger,
) {
    private val picker = EditorAssetPickerCatalog(registry)
    private var requested = false
    var error: String? = null
        private set

    fun initialize() {
        if (requested) return
        requested = true
        tasks.launchBackground("scene-editor-asset-scan") {
            try {
                val snapshot = registry.scanSnapshot()
                tasks.postToMain {
                    registry.applySnapshot(snapshot)
                    error = snapshot.errors.firstOrNull()?.message
                }
            } catch (failure: Exception) {
                tasks.postToMain { error = failure.message }
                logger.error(TAG, failure) { "Scene Editor asset scan failed: ${failure.message}" }
            }
        }
    }

    fun models(): List<EditorAssetPickerOption> = picker.listAssets(AssetCategory.Model)
    fun terrains(): List<EditorAssetPickerOption> = picker.listAssets(AssetCategory.Terrain, AssetType.Terrain)
    fun environments(): List<EditorAssetPickerOption> = picker.listAssets(AssetCategory.Environment, AssetType.Environment)

    companion object { private const val TAG = "SceneEditorAssetCatalog" }
}

class SceneEditorAssetCatalogSystem(private val catalog: SceneEditorAssetCatalog) : System() {
    override fun update(world: SceneWorld, dt: Float) = catalog.initialize()
}
