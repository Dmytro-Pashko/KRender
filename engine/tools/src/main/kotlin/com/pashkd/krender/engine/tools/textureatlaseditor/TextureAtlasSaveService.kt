package com.pashkd.krender.engine.tools.textureatlaseditor

import java.io.File

interface TextureAtlasSaveService {
    fun savePackedAtlas(
        assetRoot: File,
        targetPath: String,
        overwrite: Boolean,
        plan: TextureAtlasPackingPlan,
        ninePatchDocuments: Map<String, NinePatchDocument>,
    ): TextureAtlasEditorFileWriteResult
}

object NoOpTextureAtlasSaveService : TextureAtlasSaveService {
    override fun savePackedAtlas(
        assetRoot: File,
        targetPath: String,
        overwrite: Boolean,
        plan: TextureAtlasPackingPlan,
        ninePatchDocuments: Map<String, NinePatchDocument>,
    ): TextureAtlasEditorFileWriteResult =
        TextureAtlasEditorFileWriteResult(
            success = false,
            message = "Atlas save service is unavailable in this environment.",
        )
}
