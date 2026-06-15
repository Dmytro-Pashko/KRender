package com.pashkd.krender.engine.assets.importing

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Local filesystem asset import service.
 *
 * All import file IO lives here so editor panels only collect input and invoke plans.
 */
class LocalAssetImportService(
    private val registry: AssetRegistryService,
    private val importers: AssetImporterRegistry,
    private val logger: Logger,
    private val onChanged: () -> Unit = {},
) : AssetImportService {
    override fun planImport(
        sourcePath: String,
        collisionPolicy: AssetImportCollisionPolicy,
        importName: String?,
    ): AssetImportPlan {
        val source = File(normalizeSourcePath(sourcePath))
        val entry = planEntry(source, collisionPolicy, importName)
        return AssetImportPlan(listOf(entry), collisionPolicy)
    }

    override fun importAssets(plan: AssetImportPlan): AssetImportResult {
        val imported = mutableListOf<AssetImportEntry>()
        val skipped = mutableListOf<AssetImportEntry>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        plan.entries.forEach { entry ->
            if (!entry.supported || entry.targetPath == null) {
                skipped += entry
                return@forEach
            }

            val source = File(entry.sourcePath)
            val target = resolveTarget(entry.targetPath)
            if (!source.isFile) {
                val message = "Source file does not exist: ${entry.sourcePath}"
                errors += message
                skipped += entry.copy(status = message)
                return@forEach
            }
            if (target == null) {
                val message = "Target path is outside asset root: ${entry.targetPath}"
                errors += message
                skipped += entry.copy(status = message)
                return@forEach
            }
            if (target.exists() && plan.collisionPolicy == AssetImportCollisionPolicy.Skip) {
                skipped += entry.copy(status = "Skipped because target exists.")
                return@forEach
            }

            runCatching {
                target.parentFile?.mkdirs()
                Files.copy(
                    source.toPath(),
                    target.toPath(),
                    *mainCopyOptions(plan.collisionPolicy),
                )
                writeMetadata(target, entry)
                warnings += copyDependencies(entry)
                imported += entry.copy(status = "Imported.")
                logger.info(TAG) { "Imported asset '${source.path}' -> '${entry.targetPath}'" }
            }.getOrElse { error ->
                val message = "Import failed for '${source.path}': ${error.message}"
                logger.error(TAG, error) { message }
                errors += message
                skipped += entry.copy(status = message)
            }
        }

        if (imported.isNotEmpty()) {
            runCatching {
                registry.applySnapshot(registry.scanSnapshot())
            }.onFailure { error ->
                val message = "Registry refresh failed after import: ${error.message}"
                logger.error(TAG, error) { message }
                errors += message
            }
            onChanged()
        }

        return AssetImportResult(
            imported = imported,
            skipped = skipped,
            warnings = warnings,
            errors = errors,
        )
    }

    private fun planEntry(
        source: File,
        collisionPolicy: AssetImportCollisionPolicy,
        importName: String?,
    ): AssetImportEntry {
        if (!source.isFile) {
            return unsupported(source, "Source file does not exist.")
        }

        val spec =
            importSpecFor(source, importName)
                ?: return unsupported(source, unsupportedStatus(source))

        val targetInfo = targetPathFor(source, spec.targetDirectory, collisionPolicy)
        if (targetInfo == null) {
            val skippedPath = normalizePath("${spec.targetDirectory}/${sanitizeFileName(source.name)}")
            return AssetImportEntry(
                sourcePath = source.path,
                sourceName = source.name,
                supported = false,
                type = spec.type,
                category = spec.category,
                importerId = spec.importerId,
                targetDirectory = spec.targetDirectory,
                targetPath = skippedPath,
                mainTargetExists = true,
                status = "Skipped because target exists.",
            )
        }

        val dependencies =
            if (spec.type == AssetType.Scene2DSkin) {
                resolveSkinDependencies(source, spec.targetDirectory)
            } else {
                emptyList()
            }
        return AssetImportEntry(
            sourcePath = source.path,
            sourceName = source.name,
            supported = true,
            type = spec.type,
            category = spec.category,
            importerId = spec.importerId,
            targetDirectory = spec.targetDirectory,
            targetPath = targetInfo.path,
            mainTargetExists = targetInfo.exists,
            dependencies = dependencies,
            warnings = dependencyWarnings(dependencies),
            status =
                when {
                    collisionPolicy == AssetImportCollisionPolicy.Overwrite && targetInfo.exists ->
                        "Ready to overwrite existing asset."

                    targetInfo.exists -> "Ready to import with collision handling."
                    else -> "Ready to import."
                },
        )
    }

    private fun importSpecFor(
        source: File,
        importName: String?,
    ): ImportSpec? {
        val extension = source.extension.lowercase()
        val directory =
            when {
                extension in TextureExtensions -> "textures"
                extension == "glb" -> "model"
                extension == "json" && isScene2DSkin(source) ->
                    normalizePath("ui/skins/${skinDirectoryName(source, importName)}")

                else -> return null
            }
        val targetPath = normalizePath("$directory/${sanitizeFileName(source.name)}")
        val importer = importers.resolve(targetPath)
        val fallbackType =
            when (extension) {
                in TextureExtensions -> AssetType.Texture
                "glb" -> AssetType.GltfModel
                "json" -> AssetType.Scene2DSkin
                else -> AssetType.Unknown
            }
        val fallbackCategory =
            when (fallbackType) {
                AssetType.Texture -> AssetCategory.Texture
                AssetType.GltfModel -> AssetCategory.Model
                AssetType.Scene2DSkin -> AssetCategory.UI
                else -> AssetCategory.Other
            }
        return ImportSpec(
            targetDirectory = directory,
            type = importer?.outputType ?: fallbackType,
            category = importer?.outputCategory ?: fallbackCategory,
            importerId = importer?.id,
        )
    }

    private fun isScene2DSkin(source: File): Boolean {
        val metadata = Scene2DSkinAssetMetadataReader.read(source)
        if (metadata.status == "ok" &&
            (
                metadata.colorCount > 0 ||
                    metadata.drawableCount > 0 ||
                    metadata.textureRegionCount > 0 ||
                    metadata.labelStyleCount > 0 ||
                    metadata.textButtonStyleCount > 0 ||
                    metadata.progressBarStyleCount > 0 ||
                    metadata.imageButtonStyleCount > 0 ||
                    metadata.checkBoxStyleCount > 0 ||
                    metadata.textFieldStyleCount > 0 ||
                    metadata.scrollPaneStyleCount > 0 ||
                    metadata.selectBoxStyleCount > 0 ||
                    metadata.windowStyleCount > 0
            )
        ) {
            return true
        }
        return looksLikeScene2DSkin(source)
    }

    private fun looksLikeScene2DSkin(source: File): Boolean {
        val text = runCatching { source.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return false
        return Scene2DSkinMarkers.any { marker -> text.contains(marker, ignoreCase = true) }
    }

    private fun resolveSkinDependencies(
        source: File,
        targetDirectory: String,
    ): List<AssetImportDependency> {
        val parent = source.parentFile ?: return emptyList()
        val stem = source.nameWithoutExtension
        val dependencies = mutableListOf<File>()
        SameBasenameDependencyExtensions.forEach { extension ->
            dependencies += File(parent, "$stem.$extension").takeIf(File::isFile) ?: return@forEach
        }
        dependencies += referencedDependencyFiles(source, parent)
        return dependencies
            .distinctBy { file -> file.toPath().toAbsolutePath().normalize() }
            .mapNotNull { file ->
                val targetPath = normalizePath("$targetDirectory/${sanitizeFileName(file.name)}")
                val target = resolveTarget(targetPath) ?: return@mapNotNull null
                AssetImportDependency(
                    sourcePath = file.path,
                    targetPath = targetPath,
                    kind = dependencyKind(file.extension.lowercase()),
                    overwriteExisting = true,
                    targetExists = target.exists(),
                )
            }
    }

    private fun referencedDependencyFiles(
        source: File,
        parent: File,
    ): List<File> {
        val text = runCatching { source.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return emptyList()
        return DependencyFileReferenceRegex
            .findAll(text)
            .mapNotNull { match -> match.groups[1]?.value ?: match.groups[2]?.value }
            .map { name -> sanitizeFileName(name) }
            .filter { name -> name.isNotBlank() && !name.equals(source.name, ignoreCase = true) }
            .map { name -> File(parent, name) }
            .filter(File::isFile)
            .toList()
    }

    private fun copyDependencies(entry: AssetImportEntry): List<String> {
        val warnings = mutableListOf<String>()
        entry.dependencies.forEach { dependency ->
            val source = File(dependency.sourcePath)
            val target = resolveTarget(dependency.targetPath)
            if (target == null) {
                warnings += "Skipped dependency outside asset root: ${dependency.targetPath}"
                return@forEach
            }
            runCatching {
                target.parentFile?.mkdirs()
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.onFailure { error ->
                val message = "Failed to copy dependency '${dependency.sourcePath}': ${error.message}"
                logger.warn(TAG, error) { message }
                warnings += message
            }
        }
        return warnings
    }

    private fun dependencyWarnings(dependencies: List<AssetImportDependency>): List<String> =
        dependencies
            .filter(AssetImportDependency::targetExists)
            .map { dependency -> "Dependency will be overwritten: ${dependency.targetPath}" }

    private fun targetPathFor(
        source: File,
        targetDirectory: String,
        collisionPolicy: AssetImportCollisionPolicy,
    ): TargetPath? {
        val dir = File(registry.baseDir(), targetDirectory)
        val baseName = sanitizeFileName(source.nameWithoutExtension).ifBlank { "asset" }
        val extension = source.extension.lowercase()
        val initial = File(dir, "$baseName.$extension")
        val initialExists = initial.exists()
        return when (collisionPolicy) {
            AssetImportCollisionPolicy.Overwrite -> TargetPath(relativePath(initial), initialExists)
            AssetImportCollisionPolicy.Skip -> if (initialExists) null else TargetPath(relativePath(initial), false)
            AssetImportCollisionPolicy.Rename -> {
                val file = uniqueFile(dir, baseName, extension)
                TargetPath(relativePath(file), initialExists)
            }
        }
    }

    private fun uniqueFile(
        directory: File,
        baseName: String,
        extension: String,
    ): File {
        fun candidate(name: String): File = File(directory, "$name.$extension")
        if (!candidate(baseName).exists()) return candidate(baseName)
        var counter = 2
        while (true) {
            val file = candidate("${baseName}_$counter")
            if (!file.exists()) return file
            counter += 1
        }
    }

    private fun writeMetadata(
        file: File,
        entry: AssetImportEntry,
    ) {
        val metadataFile = File(file.parentFile, "${file.name}.krmeta")
        val existing = readExistingMetadata(metadataFile)
        val document =
            AssetMetadataDocument(
                id = existing?.id ?: "asset:${UUID.randomUUID()}",
                type = entry.type.name,
                category = entry.category.name,
                displayName = file.nameWithoutExtension,
                tags = existing?.tags ?: emptyList(),
                importerId = entry.importerId,
                importSettings =
                    mapOf(
                        "sourcePath" to entry.sourcePath,
                        "importedAtMillis" to System.currentTimeMillis(),
                    ),
            )
        metadataFile.writeText(AssetMetadataCodec.encode(document), StandardCharsets.UTF_8)
    }

    private fun readExistingMetadata(metadataFile: File): AssetMetadataDocument? =
        if (!metadataFile.isFile) {
            null
        } else {
            runCatching { AssetMetadataCodec.decode(metadataFile.readText(StandardCharsets.UTF_8)) }.getOrNull()
        }

    private fun resolveTarget(relativePath: String): File? {
        val base =
            registry
                .baseDir()
                .toPath()
                .toAbsolutePath()
                .normalize()
        val target = base.resolve(normalizePath(relativePath)).normalize()
        return if (target.startsWith(base)) target.toFile() else null
    }

    private fun mainCopyOptions(policy: AssetImportCollisionPolicy): Array<StandardCopyOption> =
        if (policy == AssetImportCollisionPolicy.Overwrite) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }

    private fun unsupportedStatus(source: File): String =
        when (source.extension.lowercase()) {
            "gltf" -> "GLTF text format is not supported by import yet. Use binary .glb."
            "obj" -> "OBJ import is not supported. Use .glb."
            else -> "Unsupported import type."
        }

    private fun unsupported(
        source: File,
        status: String,
    ): AssetImportEntry =
        AssetImportEntry(
            sourcePath = source.path,
            sourceName = source.name,
            supported = false,
            status = status,
        )

    private fun sanitizeFileName(name: String): String =
        name
            .trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun skinDirectoryName(
        source: File,
        importName: String?,
    ): String {
        val requested = importName?.trim().orEmpty()
        if (requested.isNotBlank()) return sanitizePathSegment(requested).ifBlank { "Skin" }
        val parent = source.parentFile
        val packageName =
            if (parent?.name.equals("skin", ignoreCase = true)) {
                parent?.parentFile?.name
            } else {
                parent?.name
            }
        val fallback =
            if (source.nameWithoutExtension.equals("uiskin", ignoreCase = true)) {
                packageName ?: source.nameWithoutExtension
            } else {
                source.nameWithoutExtension
            }
        return sanitizePathSegment(fallback).ifBlank { "Skin" }
    }

    private fun sanitizePathSegment(name: String): String =
        sanitizeFileName(name)
            .replace('.', '_')
            .trim()
            .trim('_')

    private fun normalizeSourcePath(sourcePath: String): String = sourcePath.trim().trim('"').trim('\'')

    private fun relativePath(file: File): String {
        val base =
            registry
                .baseDir()
                .toPath()
                .toAbsolutePath()
                .normalize()
        val target = file.toPath().toAbsolutePath().normalize()
        require(target.startsWith(base)) { "Target path escapes asset root: ${file.path}" }
        return normalizePath(base.relativize(target).toString())
    }

    private fun dependencyKind(extension: String): AssetImportDependencyKind =
        when (extension) {
            in TextureExtensions -> AssetImportDependencyKind.Texture
            "atlas" -> AssetImportDependencyKind.Atlas
            "fnt" -> AssetImportDependencyKind.BitmapFont
            "ttf", "otf" -> AssetImportDependencyKind.Font
            else -> AssetImportDependencyKind.Other
        }

    private data class ImportSpec(
        val targetDirectory: String,
        val type: AssetType,
        val category: AssetCategory,
        val importerId: String?,
    )

    private data class TargetPath(
        val path: String,
        val exists: Boolean,
    )

    companion object {
        private const val TAG = "LocalAssetImportService"
        private val TextureExtensions = setOf("png", "jpg", "jpeg", "webp")
        private val SameBasenameDependencyExtensions =
            setOf(
                "atlas",
                "png",
                "jpg",
                "jpeg",
                "webp",
                "fnt",
                "ttf",
                "otf",
            )
        private val DependencyFileReferenceRegex =
            Regex(
                "(?i)\"([^\"]+\\.(?:atlas|png|jpg|jpeg|webp|fnt|ttf|otf))\"|([A-Za-z0-9_.-]+\\.(?:atlas|png|jpg|jpeg|webp|fnt|ttf|otf))",
            )
        private val Scene2DSkinMarkers =
            listOf(
                "com.badlogic.gdx.scenes.scene2d.ui",
                "com.badlogic.gdx.graphics.g2d.BitmapFont",
                "LabelStyle",
                "TextButtonStyle",
                "ButtonStyle",
                "ImageButtonStyle",
                "CheckBoxStyle",
                "TextFieldStyle",
                "ProgressBarStyle",
                "ScrollPaneStyle",
                "SelectBoxStyle",
                "WindowStyle",
                "TintedDrawable",
            )
    }
}
