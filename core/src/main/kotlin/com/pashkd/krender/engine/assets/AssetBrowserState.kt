package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.assets.importing.AssetImportCollisionPolicy
import com.pashkd.krender.engine.assets.importing.AssetImportPlan

/**
 * Mutable state shared by the asset browser system and panels.
 */
data class AssetBrowserState(
    var searchQuery: String = "",
    var selectedCategory: AssetCategory? = null,
    var selectedAssetId: AssetId? = null,
    var sortMode: AssetSortMode = AssetSortMode.NameAsc,
    var assets: List<AssetDescriptor> = emptyList(),
    var filteredAssets: List<AssetDescriptor> = emptyList(),
    var statusMessage: String = "Asset Browser ready.",
    var errorMessage: String? = null,
    var refreshRequested: Boolean = false,
    var activationRequestedAssetId: AssetId? = null,
    var selectedModelStatus: String = "No model selected.",
    var selectedModelInfo: ModelAssetInfo? = null,
    var isScanning: Boolean = false,
    var scanErrorCount: Int = 0,
    var lastScanFinishedAtMillis: Long = 0L,
    var showCreateDialog: Boolean = false,
    var showImportDialog: Boolean = false,
    var showRenameDialog: Boolean = false,
    var showDeleteDialog: Boolean = false,
    var renameBuffer: String = "",
    var createDraft: CreateAssetDraft = CreateAssetDraft(),
    var importSourcePath: String = "",
    var importName: String = "",
    var importCollisionPolicy: AssetImportCollisionPolicy = AssetImportCollisionPolicy.Overwrite,
    var importPlan: AssetImportPlan? = null,
    var showImportOverwriteConfirmDialog: Boolean = false,
    var pendingImportPlan: AssetImportPlan? = null,
)

enum class AssetSortMode {
    NameAsc,
    NameDesc,
    TypeAsc,
    ModifiedDesc,
    SizeDesc,
}

enum class AssetBrowserMode {
    Full,
    Embedded,
    Picker,
}
