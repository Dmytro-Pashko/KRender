package com.pashkd.krender.engine.assets.importing

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

interface FileDialogService {
    fun openFile(filters: List<FileDialogFilter>): String?
}

data class FileDialogFilter(
    val label: String,
    val extensions: List<String>,
)

object NoOpFileDialogService : FileDialogService {
    override fun openFile(filters: List<FileDialogFilter>): String? = null
}

/**
 * Minimal desktop file picker implementation used by LWJGL/editor builds.
 */
class AwtFileDialogService : FileDialogService {
    override fun openFile(filters: List<FileDialogFilter>): String? =
        runCatching {
            val dialog = FileDialog(null as Frame?, "Import Asset", FileDialog.LOAD)
            val acceptedExtensions = filters.normalizedExtensions()
            if (acceptedExtensions.isNotEmpty()) {
                dialog.filenameFilter =
                    FilenameFilter { _, name ->
                        name.substringAfterLast('.', "").lowercase() in acceptedExtensions
                    }
            }
            dialog.isVisible = true
            val file = dialog.file ?: return null
            val directory = dialog.directory ?: ""
            File(directory, file).path
        }.getOrNull()
}

private fun List<FileDialogFilter>.normalizedExtensions(): Set<String> =
    flatMap(FileDialogFilter::extensions)
        .map { extension ->
            extension
                .trim()
                .trimStart('*')
                .trimStart('.')
                .lowercase()
        }.filter(String::isNotBlank)
        .toSet()

val AssetImportFileDialogFilters =
    listOf(
        FileDialogFilter("All supported assets", listOf("png", "bmp", "jpg", "jpeg", "ktx", "webp", "glb", "json")),
        FileDialogFilter("Textures", listOf("png", "bmp", "jpg", "jpeg", "ktx", "webp")),
        FileDialogFilter("Binary model", listOf("glb")),
        FileDialogFilter("Scene2D Skin", listOf("json")),
    )

val EnvironmentSourceFileDialogFilters =
    listOf(
        FileDialogFilter("HDR Environment Sources", listOf("exr", "hdr")),
        FileDialogFilter("OpenEXR", listOf("exr")),
        FileDialogFilter("Radiance HDR", listOf("hdr")),
    )
