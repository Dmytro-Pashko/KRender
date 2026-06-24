package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TextureAtlasEditorImportService(
    private val logger: Logger,
) {
    fun importTexture(
        assetRoot: File,
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): TextureAtlasEditorFileWriteResult {
        val normalizedSource = sourcePath.trim().replace('\\', '/')
        logger.info(TAG) {
            "Texture Atlas Editor import requested source='${normalizedSource.ifBlank { "<blank>" }}' targetPath='${targetPath.trim()}' overwrite=$overwrite"
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
            logger.warn(TAG) { "Texture Atlas Editor import rejected unsupported source='${normalizePath(sourceFile.path)}'" }
            return failure("Unsupported texture import extension '${sourceFile.extension}'.")
        }
        val normalizedTargetPath = targetPath.trim().ifBlank { defaultTextureTargetPath(sourceFile) }
        val targetFile =
            TextureAtlasEditorPathValidator.resolveAssetPath(assetRoot, normalizedTargetPath)
                ?: return failure("Import target path must stay inside the asset root.")
        if (targetFile.isDirectory) {
            return failure("Import target path must point to a file.")
        }
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
            logger.info(TAG) { "Texture Atlas Editor import succeeded source='${normalizePath(sourceFile.path)}' target='$writtenPath'" }
            TextureAtlasEditorFileWriteResult(
                success = true,
                message = "Imported texture to '$writtenPath'.",
                writtenPaths = listOf(writtenPath),
            )
        }.getOrElse { error ->
            logger.error(TAG, error) {
                "Texture Atlas Editor import failed source='${normalizePath(sourceFile.path)}': ${error.message}"
            }
            failure("Import failed: ${error.message ?: "unknown error"}.")
        }
    }

    private fun failure(message: String): TextureAtlasEditorFileWriteResult = TextureAtlasEditorFileWriteResult(success = false, message = message)

    private fun copyOptions(overwrite: Boolean): Array<StandardCopyOption> =
        if (overwrite) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }

    companion object {
        private const val TAG = "TextureImportService"

        private fun isSupportedImportTexture(file: File): Boolean = isSupportedTextureImportFile(file)

        private fun defaultTextureTargetPath(sourceFile: File): String = normalizePath("textures/${sourceFile.name}")
    }
}
