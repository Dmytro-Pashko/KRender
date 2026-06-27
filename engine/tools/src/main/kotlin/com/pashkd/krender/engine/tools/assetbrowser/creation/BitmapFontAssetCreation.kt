package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetOperationResult
import com.pashkd.krender.engine.tools.assetbrowser.CreateAssetDraft
import com.pashkd.krender.engine.tools.assetbrowser.createAssetBaseName
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadata
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorMetadataCodec
import java.io.File

fun createBitmapFontAsset(
    draft: CreateAssetDraft,
    engine: EngineContext,
    logger: Logger,
): AssetOperationResult {
    val baseName = createAssetBaseName(draft)
    val assetRoot = engine.assetRegistry.baseDir()
    val targetDir = File(assetRoot, draft.kind.targetDirectory)
    val targetFile = File(targetDir, "$baseName.${BitmapFontEditorMetadata.EXTENSION}")
    if (targetFile.exists()) {
        return AssetOperationResult.Failure("Bitmap font '${targetFile.name}' already exists.")
    }
    return runCatching {
        val metadata =
            BitmapFontEditorMetadata(
                sourceFont = "",
                outputFnt = "${draft.kind.targetDirectory}/$baseName.fnt",
                outputPages = listOf("${draft.kind.targetDirectory}/$baseName.png"),
            )
        BitmapFontEditorMetadataCodec.save(targetFile, metadata)
        val relativePath =
            targetFile.path
                .removePrefix(assetRoot.path)
                .removePrefix("/")
                .removePrefix("\\")
                .replace('\\', '/')
        logger.info(TAG) { "Created bitmap font asset path='$relativePath'" }
        engine.editorToolLauncher.launchBitmapFontEditor(relativePath)
        AssetOperationResult.Success(path = relativePath, message = "Created bitmap font asset '$baseName'.")
    }.getOrElse { error ->
        logger.warn(TAG) { "Bitmap font asset creation failed: ${error.message}" }
        AssetOperationResult.Failure("Failed to create bitmap font: ${error.message ?: "unknown error"}")
    }
}

private const val TAG = "BitmapFontAssetCreation"
