package com.pashkd.krender.engine.tools.assetbrowser.creation

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetOperationResult
import com.pashkd.krender.engine.tools.assetbrowser.CreateAssetDraft
import com.pashkd.krender.engine.tools.assetbrowser.createAssetBaseName
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserNormalizePath
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

internal fun createAtlasAsset(
    draft: CreateAssetDraft,
    assetRoot: File,
    logger: Logger,
): AssetOperationResult {
    val baseName = createAssetBaseName(draft)
    val atlasRelativePath = assetBrowserNormalizePath("atlases/$baseName.atlas")
    val pageRelativePath = assetBrowserNormalizePath("atlases/$baseName.png")
    val target = File(assetRoot, atlasRelativePath).canonicalFile
    val pageTarget = File(assetRoot, pageRelativePath).canonicalFile
    val canonicalRoot = assetRoot.canonicalFile
    if (!target.toPath().normalize().startsWith(canonicalRoot.toPath().normalize())) {
        return AssetOperationResult.Failure("Atlas target path escaped asset root.")
    }
    if (!pageTarget.toPath().normalize().startsWith(canonicalRoot.toPath().normalize())) {
        return AssetOperationResult.Failure("Atlas page target path escaped asset root.")
    }
    if (target.exists()) {
        return AssetOperationResult.Failure("'$atlasRelativePath' already exists.")
    }
    if (pageTarget.exists()) {
        return AssetOperationResult.Failure("'$pageRelativePath' already exists.")
    }
    return runCatching {
        target.parentFile?.mkdirs()
        target.writeText(
            defaultAtlasContent(
                pageFileName = pageTarget.name,
                width = draft.atlasWidth,
                height = draft.atlasHeight,
            ),
            Charsets.UTF_8,
        )
        writeDefaultAtlasPageTexture(pageTarget, draft.atlasWidth, draft.atlasHeight)
        logger.info(TAG) {
            "Created atlas asset '$atlasRelativePath' with default page '$pageRelativePath' size=${draft.atlasWidth}x${draft.atlasHeight}"
        }
        AssetOperationResult.Success(atlasRelativePath, "Created '$atlasRelativePath' and '$pageRelativePath'")
    }.getOrElse { error ->
        target.delete()
        pageTarget.delete()
        logger.error(TAG, error) { "Failed to create atlas asset '$atlasRelativePath': ${error.message}" }
        AssetOperationResult.Failure("Create failed: ${error.message}")
    }
}

internal fun defaultAtlasContent(
    pageFileName: String,
    width: Int,
    height: Int,
): String =
    """
    $pageFileName
    size: $width, $height
    format: RGBA8888
    filter: Linear, Linear
    repeat: none
    """.trimIndent() + "\n"

private fun writeDefaultAtlasPageTexture(
    target: File,
    width: Int,
    height: Int,
) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    require(ImageIO.write(image, "png", target)) { "PNG writer is unavailable." }
}

private const val TAG = "AtlasAssetCreation"
