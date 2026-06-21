package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserState
import com.pashkd.krender.engine.tools.common.TexturePreviewCatalog
import com.pashkd.krender.engine.ui.editor.UiService

data class AssetDetailsRenderContext(
    val state: AssetBrowserState,
    val assets: AssetService,
    val texturePreviews: TexturePreviewCatalog,
    val ui: UiService,
    val operations: AssetBrowserOperationsHandler,
    val panelId: String,
)

interface AssetDetailsRenderer {
    fun supports(asset: AssetDescriptor): Boolean

    fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    )
}
