package com.pashkd.krender.engine.assets

/**
 * UI/operation permissions for an indexed asset.
 *
 * Supported engine assets are managed and may have `.krmeta` sidecars. `Other` assets are visible-only:
 * they stay discoverable in Asset Browser, but they are not promoted into managed engine assets until an
 * explicit import/convert flow exists.
 */
data class AssetCapabilities(
    val canOpenWith: Boolean,
    val canReveal: Boolean,
    val canRename: Boolean,
    val canDuplicate: Boolean,
    val canDelete: Boolean,
    val canPreview: Boolean,
)

fun AssetDescriptor.isVisibleOnlyAsset(): Boolean = category == AssetCategory.Other || type == AssetType.Unknown

fun AssetDescriptor.isManagedAsset(): Boolean = !isVisibleOnlyAsset()

fun AssetDescriptor.canHaveMetadataSidecar(): Boolean = isManagedAsset()

fun AssetDescriptor.canOpenWithTools(): Boolean = isManagedAsset()

fun AssetDescriptor.assetCapabilities(): AssetCapabilities =
    if (isVisibleOnlyAsset()) {
        AssetCapabilities(
            canOpenWith = false,
            canReveal = true,
            canRename = false,
            canDuplicate = false,
            canDelete = false,
            canPreview = false,
        )
    } else if (type == AssetType.Scene2DSkin) {
        AssetCapabilities(
            canOpenWith = true,
            canReveal = true,
            canRename = false,
            canDuplicate = false,
            canDelete = true,
            canPreview = false,
        )
    } else {
        AssetCapabilities(
            canOpenWith = true,
            canReveal = true,
            canRename = true,
            canDuplicate = true,
            canDelete = true,
            canPreview = type == AssetType.Texture,
        )
    }
