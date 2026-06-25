package com.pashkd.krender.engine.assets.importing

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
            val frameClass = Class.forName("java.awt.Frame")
            val fileDialogClass = Class.forName("java.awt.FileDialog")
            val loadMode = fileDialogClass.getField("LOAD").getInt(null)
            val dialog =
                fileDialogClass
                    .getConstructor(frameClass, String::class.java, Int::class.javaPrimitiveType)
                    .newInstance(null, "Import Asset", loadMode)
            val acceptedExtensions =
                filters
                    .flatMap(FileDialogFilter::extensions)
                    .map { extension ->
                        extension
                            .trim()
                            .trimStart('*')
                            .trimStart('.')
                            .lowercase()
                    }.filter(String::isNotBlank)
                    .toSet()
            if (acceptedExtensions.isNotEmpty()) {
                val filenameFilter =
                    FilenameFilter { _, name ->
                        name.substringAfterLast('.', "").lowercase() in acceptedExtensions
                    }
                fileDialogClass
                    .getMethod("setFilenameFilter", FilenameFilter::class.java)
                    .invoke(dialog, filenameFilter)
            }
            fileDialogClass
                .getMethod("setVisible", Boolean::class.javaPrimitiveType)
                .invoke(dialog, true)
            val file = fileDialogClass.getMethod("getFile").invoke(dialog) as? String ?: return null
            val directory = fileDialogClass.getMethod("getDirectory").invoke(dialog) as? String ?: ""
            File(directory, file).path
        }.getOrNull()
}

val AssetImportFileDialogFilters =
    listOf(
        FileDialogFilter("All supported assets", listOf("png", "bmp", "jpg", "jpeg", "ktx", "webp", "glb", "json")),
        FileDialogFilter("Textures", listOf("png", "bmp", "jpg", "jpeg", "ktx", "webp")),
        FileDialogFilter("Binary model", listOf("glb")),
        FileDialogFilter("Scene2D Skin", listOf("json")),
    )
