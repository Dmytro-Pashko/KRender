package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.*

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger

/**
 * Editor tool that knows how to open an [AssetDescriptor].
 */
interface AssetTool {
    val id: String
    val displayName: String
    val supportedCategories: Set<AssetCategory>
    val defaultAction: Boolean get() = true

    /** Returns whether this tool can open [asset]. */
    fun canOpen(asset: AssetDescriptor): Boolean = asset.category in supportedCategories

    /** Opens [asset] using engine [context]. */
    fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    )
}

/**
 * Registry of [AssetTool]s. Resolves tools without requiring `when(category)` in scenes.
 */
class AssetToolRegistry(
    private val logger: Logger,
) {
    private val tools = mutableListOf<AssetTool>()

    /** Registers [tool]. Last registration wins on conflict. */
    fun register(tool: AssetTool) {
        tools.removeAll { it.id == tool.id }
        tools += tool
        logger.info(TAG) { "Registered asset tool '${tool.id}' (${tool.displayName})" }
    }

    /** Returns every tool that can open [asset]. */
    fun toolsFor(asset: AssetDescriptor): List<AssetTool> = tools.filter { it.canOpen(asset) }

    /** Returns the default tool for [asset], or null when none can open it. */
    fun defaultToolFor(asset: AssetDescriptor): AssetTool? = toolsFor(asset).firstOrNull { it.defaultAction }

    /** Returns all registered tools. */
    fun all(): List<AssetTool> = tools.toList()

    companion object {
        private const val TAG = "AssetToolRegistry"
    }
}
