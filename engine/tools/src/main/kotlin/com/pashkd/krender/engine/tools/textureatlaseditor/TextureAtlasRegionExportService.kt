package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.Logger
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Exports a selected atlas resource into a standalone PNG next to the atlas.
 *
 * Plain image/font regions are written as cropped `.png` files. Nine-patch
 * resources are reconstructed as `.9.png` images so their split/pad metadata
 * stays visible and reusable outside the atlas.
 */
internal class TextureAtlasRegionExportService(
    private val logger: Logger? = null,
) {
    fun exportResource(
        assetRoot: File,
        atlasFile: File?,
        resource: TextureAtlasResource,
        targetPath: String? = null,
    ): TextureAtlasEditorFileWriteResult {
        val targetFile =
            resolveTargetFile(
                assetRoot = assetRoot,
                atlasFile = atlasFile,
                resource = resource,
                targetPath = targetPath,
            )
                ?: return TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Cannot resolve export path for the selected resource.",
                )
        logger?.info(TAG) {
            "Texture Atlas Editor export requested resource='${resource.name}' type=${resource.type} target='${normalizePath(targetFile.path)}' atlas='${atlasFile?.let { normalizePath(it.path) } ?: "<none>"}'"
        }
        return runCatching {
            targetFile.parentFile?.mkdirs()
            when (resource) {
                is ImageAtlasResource -> exportImageLikeResource(resource, targetFile)
                is NinePatchAtlasResource -> exportNinePatchResource(resource, targetFile)
                is FontAtlasResource -> exportImageLikeResource(resource, targetFile)
            }
        }.onSuccess { result ->
            if (result.success) {
                logger?.info(TAG) {
                    "Texture Atlas Editor export completed resource='${resource.name}' writtenPaths=${result.writtenPaths.joinToString()} message='${result.message}'"
                }
            } else {
                logger?.warn(TAG) {
                    "Texture Atlas Editor export failed resource='${resource.name}' message='${result.message}'"
                }
            }
        }.getOrElse { error ->
            logger?.warn(TAG, error) {
                "Texture Atlas Editor export crashed resource='${resource.name}' target='${normalizePath(targetFile.path)}': ${error.message}"
            }
            TextureAtlasEditorFileWriteResult(
                success = false,
                message = "Failed to export resource: ${error.message ?: "unknown error"}.",
            )
        }
    }

    private fun exportImageLikeResource(
        resource: TextureAtlasResource,
        targetFile: File,
    ): TextureAtlasEditorFileWriteResult {
        val sourceFile =
            exportSourceFile(resource)
                ?: return TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Selected resource does not have an image source.",
                )
        val sourceImage =
            ImageIO.read(sourceFile)
                ?: return TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "PNG export supports readable raster textures only.",
                )
        val slice = sliceFor(resource, sourceImage)
        val exported = sourceImage.getSubimage(slice.x, slice.y, slice.width, slice.height)
        require(ImageIO.write(copyArgb(exported), "png", targetFile)) { "PNG writer is unavailable." }
        return TextureAtlasEditorFileWriteResult(
            success = true,
            message = "Exported '${resource.name}' to '${normalizePath(targetFile.path)}'.",
            writtenPaths = listOf(normalizePath(targetFile.path)),
        )
    }

    private fun exportSourceFile(resource: TextureAtlasResource): File? =
        when (resource) {
            is FontAtlasResource -> resource.atlasTexturePath?.let(::File) ?: resource.pageTexturePaths.firstOrNull()?.let(::File)
            is ImageAtlasResource -> File(resource.sourcePath)
            is NinePatchAtlasResource -> File(resource.sourcePath)
        }?.takeIf(File::isFile)

    private fun exportNinePatchResource(
        resource: NinePatchAtlasResource,
        targetFile: File,
    ): TextureAtlasEditorFileWriteResult {
        val sourceFile = File(resource.sourcePath)
        val sourceImage =
            ImageIO.read(sourceFile)
                ?: return TextureAtlasEditorFileWriteResult(
                    success = false,
                    message = "Nine-patch export supports readable raster textures only.",
                )
        val slice = sliceFor(resource, sourceImage)
        val cropped = copyArgb(sourceImage.getSubimage(slice.x, slice.y, slice.width, slice.height))
        val exported = BufferedImage(cropped.width + 2, cropped.height + 2, BufferedImage.TYPE_INT_ARGB)
        val graphics = exported.createGraphics()
        graphics.drawImage(cropped, 1, 1, null)
        graphics.dispose()
        val split = resource.split.takeIf { it.size == 4 }
        val pad = resource.pad.takeIf { it.size == 4 }
        split?.let {
            drawNinePatchHorizontalGuide(exported, cropped.width, it[0], it[1], y = 0)
            drawNinePatchVerticalGuide(exported, cropped.height, it[2], it[3], x = 0)
        }
        pad?.let {
            drawNinePatchHorizontalGuide(exported, cropped.width, it[0], it[1], y = exported.height - 1)
            drawNinePatchVerticalGuide(exported, cropped.height, it[2], it[3], x = exported.width - 1)
        }
        require(ImageIO.write(exported, "png", targetFile)) { "PNG writer is unavailable." }
        return TextureAtlasEditorFileWriteResult(
            success = true,
            message = "Exported Nine-patch '${resource.name}' to '${normalizePath(targetFile.path)}'.",
            writtenPaths = listOf(normalizePath(targetFile.path)),
        )
    }

    private fun resolveTargetFile(
        assetRoot: File,
        atlasFile: File?,
        resource: TextureAtlasResource,
        targetPath: String?,
    ): File? {
        val trimmed = targetPath?.trim()?.replace('\\', '/').orEmpty()
        if (trimmed.isNotBlank()) {
            return TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, trimmed)
        }
        val exportDirectory =
            atlasFile?.parentFile?.resolve("export")
                ?: resource.sourcePathOrNull()?.let(::File)?.parentFile?.resolve("export")
                ?: return null
        return TextureAtlasEditorPathValidator.resolveAssetPath(
            assetRoot,
            normalizePath(File(exportDirectory, defaultExportFileName(resource)).path),
        )
    }

    private fun defaultExportFileName(resource: TextureAtlasResource): String =
        when (resource) {
            is NinePatchAtlasResource -> "${safeFileStem(resource.name)}.9.png"
            else -> "${safeFileStem(resource.name)}.png"
        }

    private fun sliceFor(
        resource: TextureAtlasResource,
        sourceImage: BufferedImage,
    ): ExportSlice {
        val rawX =
            when (resource) {
                is ImageAtlasResource -> resource.sourceX
                is NinePatchAtlasResource -> resource.sourceX
                is FontAtlasResource -> resource.sourceX
            }
        val rawY =
            when (resource) {
                is ImageAtlasResource -> resource.sourceY
                is NinePatchAtlasResource -> resource.sourceY
                is FontAtlasResource -> resource.sourceY
            }
        val rawWidth =
            when (resource) {
                is ImageAtlasResource -> resource.sourceWidth
                is NinePatchAtlasResource -> resource.sourceWidth
                is FontAtlasResource -> resource.sourceWidth
            } ?: (sourceImage.width - rawX)
        val rawHeight =
            when (resource) {
                is ImageAtlasResource -> resource.sourceHeight
                is NinePatchAtlasResource -> resource.sourceHeight
                is FontAtlasResource -> resource.sourceHeight
            } ?: (sourceImage.height - rawY)

        val x = rawX.coerceIn(0, sourceImage.width.coerceAtLeast(1) - 1)
        val y = rawY.coerceIn(0, sourceImage.height.coerceAtLeast(1) - 1)
        val width = rawWidth.coerceIn(1, sourceImage.width - x)
        val height = rawHeight.coerceIn(1, sourceImage.height - y)
        return ExportSlice(x = x, y = y, width = width, height = height)
    }

    private fun copyArgb(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(source, 0, 0, null)
        graphics.dispose()
        return copy
    }

    private fun drawNinePatchHorizontalGuide(
        image: BufferedImage,
        contentWidth: Int,
        leftInset: Int,
        rightInset: Int,
        y: Int,
    ) {
        val left = leftInset.coerceAtLeast(0)
        val right = rightInset.coerceAtLeast(0)
        val black = 0xFF000000.toInt()
        val start = 1 + left.coerceAtMost(contentWidth)
        val endExclusive = 1 + (contentWidth - right).coerceIn(0, contentWidth)
        for (x in start until endExclusive) {
            image.setRGB(x, y, black)
        }
    }

    private fun drawNinePatchVerticalGuide(
        image: BufferedImage,
        contentHeight: Int,
        topInset: Int,
        bottomInset: Int,
        x: Int,
    ) {
        val top = topInset.coerceAtLeast(0)
        val bottom = bottomInset.coerceAtLeast(0)
        val black = 0xFF000000.toInt()
        val start = 1 + top.coerceAtMost(contentHeight)
        val endExclusive = 1 + (contentHeight - bottom).coerceIn(0, contentHeight)
        for (y in start until endExclusive) {
            image.setRGB(x, y, black)
        }
    }

    private fun safeFileStem(name: String): String =
        name
            .trim()
            .replace(InvalidFileNameChars, "_")
            .ifBlank { "region" }

    private data class ExportSlice(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    companion object {
        private const val TAG = "TextureAtlasRegionExport"
        private val InvalidFileNameChars = Regex("[\\\\/:*?\"<>|]+")
    }
}
