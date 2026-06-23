package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TextureManagerImportService(
    private val logger: Logger,
) {
    fun importTexture(
        assetRoot: File,
        sourcePath: String,
        targetDirectory: String,
        overwrite: Boolean,
    ): TextureManagerFileWriteResult {
        val normalizedSource = sourcePath.trim().replace('\\', '/')
        logger.info(TAG) {
            "Texture Manager import requested source='${normalizedSource.ifBlank { "<blank>" }}' targetDir='${targetDirectory.trim()}' overwrite=$overwrite"
        }
        if (normalizedSource.isBlank()) {
            return failure("Import source path is required.")
        }
        val sourceFile = File(normalizedSource)
        if (!sourceFile.exists()) {
            return failure("Import source file was not found.")
        }
        if (!sourceFile.isFile) {
            return failure("Import source path must point to a file.")
        }
        if (!isSupportedImportTexture(sourceFile)) {
            logger.warn(TAG) { "Texture Manager import rejected unsupported source='${normalizePath(sourceFile.path)}'" }
            return failure("Unsupported texture import extension '${sourceFile.extension}'.")
        }
        val targetFile =
            TextureManagerPathValidator.resolveAssetFile(assetRoot, targetDirectory, sourceFile.name)
                ?: return failure("Import target directory must stay inside the asset root.")
        if (targetFile.exists() && !overwrite) {
            return failure("Import target already exists and overwrite is disabled.")
        }

        return runCatching {
            targetFile.parentFile?.mkdirs()
            Files.copy(
                sourceFile.toPath(),
                targetFile.toPath(),
                *copyOptions(overwrite),
            )
            val writtenPath = normalizePath(targetFile.path)
            logger.info(TAG) { "Texture Manager import succeeded source='${normalizePath(sourceFile.path)}' target='$writtenPath'" }
            TextureManagerFileWriteResult(
                success = true,
                message = "Imported texture to '$writtenPath'.",
                writtenPaths = listOf(writtenPath),
            )
        }.getOrElse { error ->
            logger.error(TAG, error) {
                "Texture Manager import failed source='${normalizePath(sourceFile.path)}': ${error.message}"
            }
            failure("Import failed: ${error.message ?: "unknown error"}.")
        }
    }

    private fun failure(message: String): TextureManagerFileWriteResult = TextureManagerFileWriteResult(success = false, message = message)

    private fun copyOptions(overwrite: Boolean): Array<StandardCopyOption> =
        if (overwrite) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }

    companion object {
        private const val TAG = "TextureImportService"

        private fun isSupportedImportTexture(file: File): Boolean =
            file.isFile &&
                (
                    file.extension.lowercase() in setOf("png", "jpg", "jpeg", "ktx", "webp") ||
                        isNinePatchTexturePath(file.name)
                )
    }
}
