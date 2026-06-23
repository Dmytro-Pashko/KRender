package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.SceneFileService
import java.io.File

class TextureAtlasDescriptorExporter(
    private val logger: Logger,
    private val sceneFiles: SceneFileService,
) {
    fun exportDescriptorDraft(
        assetRoot: File,
        exportDirectory: String,
        exportBaseName: String,
        overwrite: Boolean,
        plan: TextureAtlasPackingPlan,
    ): TextureManagerFileWriteResult {
        val normalizedDirectory = exportDirectory.trim()
        val normalizedBaseName = exportBaseName.trim()
        logger.info(TAG) {
            "Texture Manager descriptor export requested dir='${normalizedDirectory.ifBlank { "<blank>" }}' base='${normalizedBaseName.ifBlank { "<blank>" }}' overwrite=$overwrite pages=${plan.pages.size}"
        }
        if (plan.packedRegionCount <= 0 || plan.pages.isEmpty()) {
            return failure("A packing dry-run with packed regions is required before export.")
        }
        if (normalizedBaseName.isBlank() || normalizedBaseName.contains('/') || normalizedBaseName.contains('\\')) {
            return failure("Export base name is invalid.")
        }
        if (plan.pages.flatMap { page -> page.regions }.any { region -> !File(region.sourcePath).isFile }) {
            return failure("One or more source textures no longer exist.")
        }
        val targetDirectoryFile =
            TextureManagerPathValidator.resolveAssetDirectory(assetRoot, normalizedDirectory)
                ?: return failure("Export directory must stay inside the asset root.")
        val targetAtlasFile = File(targetDirectoryFile, "$normalizedBaseName.atlas")
        if (targetAtlasFile.exists() && !overwrite) {
            return failure("Atlas descriptor target already exists and overwrite is disabled.")
        }

        val atlasPath = normalizePath(targetAtlasFile.path)
        return runCatching {
            sceneFiles.ensureDirectories(atlasPath)
            sceneFiles.writeText(atlasPath, buildDescriptor(plan, normalizedBaseName))
            logger.info(TAG) { "Texture Manager descriptor export succeeded path='$atlasPath'" }
            TextureManagerFileWriteResult(
                success = true,
                message = "Atlas descriptor draft exported. Packed page image generation is deferred.",
                writtenPaths = listOf(atlasPath),
            )
        }.getOrElse { error ->
            logger.error(TAG, error) { "Texture Manager descriptor export failed path='$atlasPath': ${error.message}" }
            failure("Atlas descriptor export failed: ${error.message ?: "unknown error"}.")
        }
    }

    private fun buildDescriptor(
        plan: TextureAtlasPackingPlan,
        baseName: String,
    ): String {
        val usedNames = mutableMapOf<String, Int>()
        return buildString {
            plan.pages.forEachIndexed { pageIndex, page ->
                if (pageIndex > 0) {
                    appendLine()
                }
                appendLine("${baseName}_${page.index + 1}.png")
                appendLine("size: ${page.width}, ${page.height}")
                appendLine("format: RGBA8888")
                appendLine("filter: Nearest, Nearest")
                appendLine("repeat: none")
                page.regions.forEach { region ->
                    val regionName = uniqueRegionName(region.sourcePath, usedNames)
                    appendLine(regionName)
                    appendLine("  rotate: false")
                    appendLine("  xy: ${region.x}, ${region.y}")
                    appendLine("  size: ${region.width}, ${region.height}")
                    appendLine("  orig: ${region.width}, ${region.height}")
                    appendLine("  offset: 0, 0")
                    appendLine("  index: -1")
                }
            }
        }
    }

    private fun uniqueRegionName(
        sourcePath: String,
        usedNames: MutableMap<String, Int>,
    ): String {
        val baseName = File(sourcePath).nameWithoutExtension.ifBlank { "region" }
        val count = (usedNames[baseName] ?: 0) + 1
        usedNames[baseName] = count
        return if (count == 1) baseName else "${baseName}_$count"
    }

    private fun failure(message: String): TextureManagerFileWriteResult = TextureManagerFileWriteResult(success = false, message = message)

    companion object {
        private const val TAG = "AtlasDescriptorExport"
    }
}
