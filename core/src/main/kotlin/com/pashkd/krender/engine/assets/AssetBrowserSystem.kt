package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.*

/**
 * Keeps asset browser state synchronized with the registry and runtime asset service.
 *
 * Scans run on a background coroutine; results are applied on the main thread via [TaskService.postToMain].
 */
class AssetBrowserSystem(
    private val registry: AssetRegistryService,
    private val assets: AssetService,
    private val tasks: TaskService,
    private val logger: Logger,
    private val state: AssetBrowserState,
    private val onAssetActivated: (AssetDescriptor) -> Unit,
) : System() {
    private var initialScanRequested = false
    private var scanInFlight = false
    private var queuedModelPath: String? = null
    private var queuedTexturePath: String? = null

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        if (!initialScanRequested) {
            initialScanRequested = true
            requestScan(reason = "initial")
        }

        if (state.refreshRequested) {
            state.refreshRequested = false
            requestScan(reason = "refresh")
        }

        applyFilteringAndSorting()
        syncSelectedModelInfo()
        syncSelectedTexturePreview()
        handleActivationRequest()
    }

    /** Returns the currently selected asset descriptor, if any. */
    fun selectedAsset(): AssetDescriptor? = state.selectedAssetId?.let(registry::findById)

    private fun requestScan(reason: String) {
        if (scanInFlight) {
            logger.info(TAG) { "Scan already in flight, ignoring '$reason' trigger" }
            return
        }
        scanInFlight = true
        state.isScanning = true
        state.statusMessage = "Scanning assets ($reason)..."
        tasks.launchBackground("asset-browser-scan") {
            val snapshot =
                try {
                    registry.scanSnapshot()
                } catch (error: Exception) {
                    logger.error(TAG, error) { "Background scan failed: ${error.message}" }
                    tasks.postToMain {
                        scanInFlight = false
                        state.isScanning = false
                        state.errorMessage = "Asset scan failed: ${error.message}"
                    }
                    return@launchBackground
                }
            tasks.postToMain {
                applyScanResult(snapshot)
            }
        }
    }

    private fun applyScanResult(snapshot: AssetRegistrySnapshot) {
        registry.applySnapshot(snapshot)
        state.assets = snapshot.assets
        state.scanErrorCount = snapshot.errors.size
        state.lastScanFinishedAtMillis = snapshot.scannedAtMillis
        state.errorMessage = snapshot.errors.firstOrNull()?.let { "Scan error: ${it.path} (${it.message})" }
        state.statusMessage = "Indexed ${snapshot.assets.size} assets in ${snapshot.durationMillis} ms."
        state.isScanning = false
        scanInFlight = false
    }

    private fun applyFilteringAndSorting() {
        val query = state.searchQuery.trim().lowercase()
        val selectedCategory = state.selectedCategory
        val filtered =
            state.assets
                .asSequence()
                .filter { asset -> selectedCategory == null || asset.category == selectedCategory }
                .filter { asset ->
                    query.isBlank() ||
                        asset.name.lowercase().contains(query) ||
                        asset.path.lowercase().contains(query) ||
                        asset.type.name
                            .lowercase()
                            .contains(query) ||
                        asset.category.displayName
                            .lowercase()
                            .contains(query) ||
                        asset.tags.any { tag -> tag.lowercase().contains(query) }
                }.toList()

        state.filteredAssets =
            when (state.sortMode) {
                AssetSortMode.NameAsc -> filtered.sortedBy { it.name.lowercase() }
                AssetSortMode.NameDesc -> filtered.sortedByDescending { it.name.lowercase() }
                AssetSortMode.TypeAsc -> filtered.sortedWith(compareBy<AssetDescriptor> { it.type.name }.thenBy { it.name.lowercase() })
                AssetSortMode.ModifiedDesc ->
                    filtered.sortedWith(
                        compareByDescending<AssetDescriptor> {
                            it.modifiedAtMillis
                        }.thenBy { it.name.lowercase() },
                    )

                AssetSortMode.SizeDesc ->
                    filtered.sortedWith(
                        compareByDescending<AssetDescriptor> { it.sizeBytes }.thenBy { it.name.lowercase() },
                    )
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
        state.selectedModelStatus =
            if (loaded) {
                "Loaded"
            } else {
                "Loading ${"%.0f".format(assets.progress() * 100f)}%"
            }
        state.selectedModelInfo = if (loaded) assets.modelInfo(modelRef) else null
    }

    private fun syncSelectedTexturePreview() {
        val selected = selectedAsset()
        if (selected == null || selected.type != AssetType.Texture) {
            queuedTexturePath = null
            return
        }

        if (queuedTexturePath != selected.path) {
            queuedTexturePath = selected.path
            logger.info(TAG) { "Queueing selected texture '${selected.path}' for browser preview." }
            assets.queue(AssetRef.texture(selected.path))
        }
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
