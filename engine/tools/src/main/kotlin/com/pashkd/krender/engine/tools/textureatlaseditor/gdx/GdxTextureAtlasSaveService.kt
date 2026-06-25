package com.pashkd.krender.engine.tools.textureatlaseditor.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchDocument
import com.pashkd.krender.engine.tools.textureatlaseditor.NinePatchSegment
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPage
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingPlan
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasPackingRegion
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasSaveService
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorFileWriteResult
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorPathValidator
import com.pashkd.krender.engine.tools.textureatlaseditor.normalizePath
import java.io.File

class GdxTextureAtlasSaveService(
    private val logger: Logger,
) : TextureAtlasSaveService {
    override fun savePackedAtlas(
        assetRoot: File,
        targetPath: String,
        overwrite: Boolean,
        plan: TextureAtlasPackingPlan,
        ninePatchDocuments: Map<String, NinePatchDocument>,
    ): TextureAtlasEditorFileWriteResult {
        val atlasFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, targetPath)
                ?: return failure("Atlas target path must stay inside the asset root.")
        if (!atlasFile.name.endsWith(".atlas", ignoreCase = true)) {
            return failure("Atlas target path must end with '.atlas'.")
        }
        if (atlasFile.exists() && !overwrite) {
            return failure("Atlas target already exists and overwrite is disabled.")
        }
        atlasFile.parentFile?.mkdirs()
        val baseName = atlasFile.nameWithoutExtension.ifBlank { "packed" }
        val outputDirectory = atlasFile.parentFile ?: return failure("Atlas target directory is unavailable.")
        val pageFiles =
            plan.pages.map { page ->
                File(outputDirectory, "${baseName}_${page.index + 1}.png")
            }
        if (!overwrite) {
            pageFiles.firstOrNull(File::exists)?.let { file ->
                return failure("Atlas page target already exists and overwrite is disabled: '${normalizePath(file.path)}'.")
            }
        }

        return runCatching {
            val writtenPaths = mutableListOf<String>()
            val pageNames = mutableListOf<String>()
            plan.pages.forEachIndexed { index, page ->
                val pageFile = pageFiles[index]
                val pageFailures = writePage(pageFile, page, ninePatchDocuments)
                if (pageFailures.isNotEmpty()) {
                    return@runCatching failure("Atlas page ${page.index + 1} could not be written: ${pageFailures.joinToString(" ")}")
                }
                writtenPaths += normalizePath(pageFile.path)
                pageNames += pageFile.name
                logger.info(TAG) {
                    "Texture Atlas Editor wrote packed atlas page index=${page.index} file='${normalizePath(pageFile.path)}' size=${page.width}x${page.height} regions=${page.regions.size}"
                }
            }
            val descriptor = buildDescriptor(plan, pageNames, ninePatchDocuments)
            atlasFile.writeText(descriptor, Charsets.UTF_8)
            writtenPaths += normalizePath(atlasFile.path)
            logger.info(TAG) {
                "Saved packed Texture Atlas Editor atlas path='${normalizePath(atlasFile.path)}' pages=${plan.pages.size} regions=${plan.packedRegionCount} pageFiles=${pageNames.joinToString()} descriptorBytes=${descriptor.toByteArray(Charsets.UTF_8).size}"
            }
            TextureAtlasEditorFileWriteResult(
                success = true,
                message = "Saved atlas '${normalizePath(atlasFile.path)}' with ${plan.pages.size} page(s).",
                writtenPaths = writtenPaths,
            )
        }.getOrElse { error ->
            logger.error(TAG, error) { "Failed to save packed atlas path='${normalizePath(atlasFile.path)}': ${error.message}" }
            failure("Atlas save failed: ${error.message ?: "unknown error"}.")
        }
    }

    private fun writePage(
        file: File,
        page: TextureAtlasPackingPage,
        ninePatchDocuments: Map<String, NinePatchDocument>,
    ): List<String> {
        val failures = mutableListOf<String>()
        page.regions.forEach { region ->
            val sourceFile = File(region.sourcePath)
            if (!sourceFile.isFile) {
                failures += "Region '${region.displayName}' source is missing: '${normalizePath(region.sourcePath)}'."
                logger.warn(TAG) { "Atlas save pre-check failed: missing source path='${region.sourcePath}' region='${region.displayName}'" }
            }
        }
        if (failures.isNotEmpty()) return failures

        val output = Pixmap(page.width, page.height, Pixmap.Format.RGBA8888)
        try {
            output.setColor(0f, 0f, 0f, 0f)
            output.fill()
            page.regions.forEach { region ->
                runCatching {
                    Pixmap(Gdx.files.absolute(region.sourcePath))
                }.onSuccess { source ->
                    try {
                        output.drawPixmap(
                            source,
                            region.sourceX,
                            region.sourceY,
                            region.sourceWidth,
                            region.sourceHeight,
                            region.x,
                            region.y,
                            region.width,
                            region.height,
                        )
                    } finally {
                        source.dispose()
                    }
                }.onFailure { error ->
                    failures += "Region '${region.displayName}' source could not be read: ${error.message ?: "unknown error"}."
                    logger.warn(TAG, error) { "Atlas save region load failed path='${region.sourcePath}' region='${region.displayName}': ${error.message}" }
                }
            }
            if (failures.isNotEmpty()) return failures
            PixmapIO.writePNG(Gdx.files.absolute(file.absolutePath), output)
        } finally {
            output.dispose()
        }
        return failures
    }

    private fun buildDescriptor(
        plan: TextureAtlasPackingPlan,
        pageNames: List<String>,
        ninePatchDocuments: Map<String, NinePatchDocument>,
    ): String {
        val usedNames = mutableMapOf<String, Int>()
        return buildString {
            plan.pages.forEachIndexed { pageIndex, page ->
                if (pageIndex > 0) {
                    appendLine()
                }
                appendLine(pageNames[pageIndex])
                appendLine("size: ${page.width}, ${page.height}")
                appendLine("format: RGBA8888")
                appendLine("filter: Nearest, Nearest")
                appendLine("repeat: none")
                page.regions.forEach { region ->
                    val regionName = uniqueRegionName(region, usedNames)
                    appendLine(regionName)
                    appendLine("  rotate: false")
                    appendLine("  xy: ${region.x}, ${region.y}")
                    appendLine("  size: ${region.width}, ${region.height}")
                    appendLine("  orig: ${region.width}, ${region.height}")
                    appendLine("  offset: 0, 0")
                    val ninePatch = ninePatchDocuments[region.sourcePath]
                    val split = splitValue(ninePatch) ?: region.split.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    val pad = padValue(ninePatch) ?: region.pad.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    split?.let { appendLine("  split: $it") }
                    pad?.let { appendLine("  pad: $it") }
                    appendLine("  index: ${region.index ?: -1}")
                }
            }
        }
    }

    private fun uniqueRegionName(
        region: TextureAtlasPackingRegion,
        usedNames: MutableMap<String, Int>,
    ): String {
        val rawName = File(region.sourcePath).name
        val baseName =
            region.regionName
                .ifBlank { rawName.removeSuffix(".9.png").substringBeforeLast('.', rawName) }
                .ifBlank { "region" }
        val count = (usedNames[baseName] ?: 0) + 1
        usedNames[baseName] = count
        return if (count == 1) baseName else "${baseName}_$count"
    }

    private fun splitValue(document: NinePatchDocument?): String? {
        if (document == null) return null
        val x = document.stretchX.firstOrNull() ?: return null
        val y = document.stretchY.firstOrNull() ?: return null
        return listOf(
            x.start,
            document.contentWidth - (x.start + x.length),
            y.start,
            document.contentHeight - (y.start + y.length),
        ).joinToString(", ")
    }

    private fun padValue(document: NinePatchDocument?): String? {
        if (document == null) return null
        val x = document.paddingX ?: return null
        val y = document.paddingY ?: return null
        return listOf(
            x.start,
            document.contentWidth - (x.start + x.length),
            y.start,
            document.contentHeight - (y.start + y.length),
        ).joinToString(", ")
    }

    private fun failure(message: String): TextureAtlasEditorFileWriteResult = TextureAtlasEditorFileWriteResult(success = false, message = message)

    companion object {
        private const val TAG = "TextureAtlasSave"
    }
}
