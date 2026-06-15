package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.*

/**
 * Identifier + display label for a tool exposed in the "Open With" menu.
 */
data class AssetToolDescriptor(
    val id: String,
    val label: String,
)

/**
 * UI-facing handler for asset operations and tool resolution.
 *
 * Implementations bridge Asset Browser panels to the asset operations service and tool registry
 * without requiring panels to perform IO directly.
 */
interface AssetBrowserOperationsHandler {
    fun create(draft: CreateAssetDraft)

    fun rename(
        asset: AssetDescriptor,
        newName: String,
    )

    fun duplicate(
        asset: AssetDescriptor,
        targetName: String,
    )

    fun delete(asset: AssetDescriptor)

    fun reveal(asset: AssetDescriptor)

    fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor>

    fun openWith(
        asset: AssetDescriptor,
        toolId: String,
    )

    companion object {
        /** Default no-op handler used when the panel runs without operations support (e.g. picker mode). */
        val NoOp: AssetBrowserOperationsHandler =
            object : AssetBrowserOperationsHandler {
                override fun create(draft: CreateAssetDraft) = Unit

                override fun rename(
                    asset: AssetDescriptor,
                    newName: String,
                ) = Unit

                override fun duplicate(
                    asset: AssetDescriptor,
                    targetName: String,
                ) = Unit

                override fun delete(asset: AssetDescriptor) = Unit

                override fun reveal(asset: AssetDescriptor) = Unit

                override fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor> = emptyList()

                override fun openWith(
                    asset: AssetDescriptor,
                    toolId: String,
                ) = Unit
            }
    }
}
