package com.pashkd.krender.engine.tools.textureatlaseditor

typealias TextureAtlasNinePatchPreviewType = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchPreviewType
typealias TextureAtlasNinePatchStretchPreset = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchStretchPreset
typealias TextureAtlasNinePatchStretchPreview = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchStretchPreview
typealias TextureAtlasNinePatchStretchRect = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchStretchRect
typealias TextureAtlasNinePatchStretchSlice = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchStretchSlice
typealias TextureAtlasNinePatchStretchState = com.pashkd.krender.engine.tools.common.ninepatch.NinePatchStretchState

internal fun resolveNinePatchStretchTargetSize(
    stretch: TextureAtlasNinePatchStretchState,
    draft: NinePatchDraft,
): Pair<Int, Int> =
    com.pashkd.krender.engine.tools.common.ninepatch.resolveNinePatchStretchTargetSize(stretch, draft)

internal fun buildNinePatchStretchPreview(
    draft: NinePatchDraft?,
    stretch: TextureAtlasNinePatchStretchState,
): TextureAtlasNinePatchStretchPreview? =
    com.pashkd.krender.engine.tools.common.ninepatch.buildNinePatchStretchPreview(draft, stretch)
