package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System

/**
 * Keeps asset browser state synchronized with the registry and runtime asset service.
 */
class AssetBrowserSystem(
    private val registry: AssetRegistryService,
    private val assets: AssetService,
    private val logger: Logger,
    private val state: AssetBrowserState,
    private val onAssetActivated: (AssetDescriptor) -> Unit,
) : System() {
    private var scanned = false
    private var queuedModelPath: String? = null

    override fun update(world: SceneWorld, dt: Float) {
        if (!scanned) {
            scanned = true
            scanRegistry()
        }

        if (state.refreshRequested) {
            state.refreshRequested = false
            scanRegistry()
        }

        applyFilteringAndSorting()
        syncSelectedModelInfo()
        handleActivationRequest()
    }

    /**
     * Returns the current selected asset descriptor, if any.
     */
    fun selectedAsset(): AssetDescriptor? =
        state.selectedAssetId?.let(registry::findById)

    private fun scanRegistry() {
        try {
            registry.scan()
            state.assets = registry.all()
            state.errorMessage = null
            state.statusMessage = "Indexed ${state.assets.size} assets."
        } catch (error: Exception) {
            state.errorMessage = "Asset scan failed: ${error.message}"
            logger.error(TAG, error) { "Asset scan failed: ${error.message}" }
        }
    }

    private fun applyFilteringAndSorting() {
        val query = state.searchQuery.trim().lowercase()
        val selectedCategory = state.selectedCategory
        val filtered = state.assets
            .asSequence()
            .filter { asset -> selectedCategory == null || asset.category == selectedCategory }
            .filter { asset ->
                query.isBlank() ||
                    asset.name.lowercase().contains(query) ||
                    asset.path.lowercase().contains(query) ||
                    asset.type.name.lowercase().contains(query) ||
                    asset.category.displayName.lowercase().contains(query) ||
                    asset.tags.any { tag -> tag.lowercase().contains(query) }
            }
            .toList()

        state.filteredAssets = when (state.sortMode) {
            AssetSortMode.NameAsc -> filtered.sortedBy { it.name.lowercase() }
            AssetSortMode.NameDesc -> filtered.sortedByDescending { it.name.lowercase() }
            AssetSortMode.TypeAsc -> filtered.sortedWith(compareBy<AssetDescriptor> { it.type.name }.thenBy { it.name.lowercase() })
            AssetSortMode.ModifiedDesc -> filtered.sortedWith(compareByDescending<AssetDescriptor> { it.modifiedAtMillis }.thenBy { it.name.lowercase() })
            AssetSortMode.SizeDesc -> filtered.sortedWith(compareByDescending<AssetDescriptor> { it.sizeBytes }.thenBy { it.name.lowercase() })
        }

        val selectedAssetId = state.selectedAssetId
        if (selectedAssetId != null && state.assets.none { it.id == selectedAssetId }) {
            state.selectedAssetId = null
        }
    }

    private fun syncSelectedModelInfo() {
        val selected = selectedAsset()
        if (selected == null || selected.category != AssetCategory.Model) {
            queuedModelPath = null
            state.selectedModelInfo = null
            state.selectedModelStatus = if (selected == null) "No asset selected." else "Selected asset is not a model."
            return
        }

        val modelRef = AssetRef.model(selected.path)
        if (queuedModelPath != selected.path) {
            queuedModelPath = selected.path
            state.selectedModelInfo = null
            logger.info(TAG) { "Queueing selected model '${selected.path}' for browser metadata." }
            assets.queue(modelRef)
        }

        val loaded = assets.isLoaded(modelRef)
        state.selectedModelStatus = if (loaded) {
            "Loaded"
        } else {
            "Loading ${"%.0f".format(assets.progress() * 100f)}%"
        }
        state.selectedModelInfo = if (loaded) assets.modelInfo(modelRef) else null
    }

    private fun handleActivationRequest() {
        val requestedId = state.activationRequestedAssetId ?: return
        state.activationRequestedAssetId = null
        val asset = registry.findById(requestedId)
        if (asset == null) {
            state.errorMessage = "Selected asset no longer exists."
            logger.warn(TAG) { "Activation requested for missing asset id='${requestedId.value}'" }
            return
        }

        logger.info(TAG) { "Activating asset '${asset.path}' (${asset.type})" }
        onAssetActivated(asset)
    }

    companion object {
        private const val TAG = "AssetBrowserSystem"
    }
}

