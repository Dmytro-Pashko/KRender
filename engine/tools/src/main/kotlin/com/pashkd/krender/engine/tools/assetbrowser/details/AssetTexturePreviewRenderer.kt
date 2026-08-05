package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import com.pashkd.krender.engine.tools.common.TexturePreviewResult

internal fun drawAssetTexturePreview(
    asset: AssetDescriptor,
    context: AssetDetailsRenderContext,
    previewSize: Float = DefaultTexturePreviewSize,
) {
    when (val preview = context.texturePreviews.preview(asset.path)) {
        is TexturePreviewResult.Unavailable -> {
            assetBrowserTextLine("Preview unavailable.")
            return
        }

        is TexturePreviewResult.Available -> {
            val handle = preview.handle
            if (!context.ui.drawTexturePreview(handle, previewSize, previewSize)) {
                assetBrowserTextLine("Preview unavailable.")
                return
            }
            assetBrowserTextLine("Preview size: ${handle.width} x ${handle.height}")
        }
    }
}

private const val DefaultTexturePreviewSize = 250f
