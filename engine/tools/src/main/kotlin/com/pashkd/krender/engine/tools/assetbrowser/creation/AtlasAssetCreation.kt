package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetOperationResult
import com.pashkd.krender.engine.tools.assetbrowser.CreateAssetDraft
import com.pashkd.krender.engine.tools.assetbrowser.createAssetBaseName
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserNormalizePath
import java.io.File

internal fun createAtlasAsset(
    draft: CreateAssetDraft,
    assetRoot: File,
    logger: Logger,
): AssetOperationResult {
    val baseName = createAssetBaseName(draft)
    val relativePath = assetBrowserNormalizePath("atlases/$baseName.atlas")
    val target = File(assetRoot, relativePath).canonicalFile
    val canonicalRoot = assetRoot.canonicalFile
    if (!target.toPath().normalize().startsWith(canonicalRoot.toPath().normalize())) {
        return AssetOperationResult.Failure("Atlas target path escaped asset root.")
    }
    if (target.exists()) {
        return AssetOperationResult.Failure("'$relativePath' already exists.")
    }
    return runCatching {
        target.parentFile?.mkdirs()
        target.writeText(defaultAtlasContent(baseName, draft.atlasWidth, draft.atlasHeight), Charsets.UTF_8)
        logger.info(TAG) { "Created atlas asset '$relativePath' size=${draft.atlasWidth}x${draft.atlasHeight}" }
        AssetOperationResult.Success(relativePath, "Created '$relativePath'")
    }.getOrElse { error ->
        logger.error(TAG, error) { "Failed to create atlas asset '$relativePath': ${error.message}" }
        AssetOperationResult.Failure("Create failed: ${error.message}")
    }
}

internal fun defaultAtlasContent(
    atlasName: String,
    width: Int,
    height: Int,
): String =
    """
    ${atlasName}_1.png
    size: $width, $height
    format: RGBA8888
    filter: Nearest, Nearest
    repeat: none
    """.trimIndent() + "\n"

private const val TAG = "AtlasAssetCreation"
