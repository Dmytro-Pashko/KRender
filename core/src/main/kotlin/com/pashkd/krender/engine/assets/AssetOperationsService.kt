package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.Logger
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

/**
 * Mode used by [AssetOperationsService.delete].
 */
enum class DeleteMode {
    /** Delete file and `.krmeta` permanently from disk. */
    Permanent,

    /** Move file and `.krmeta` to a sibling `.trash/` directory. */
    Trash,
}

/**
 * Request describing an asset to create from scratch.
 *
 * The new file is created with [initialContent] (or empty bytes when null) and a fresh `.krmeta` sidecar.
 */
data class CreateAssetRequest(
    val name: String,
    val type: AssetType,
    val category: AssetCategory,
    val targetDirectory: String,
    val extension: String,
    val initialContent: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CreateAssetRequest) return false
        return name == other.name &&
            type == other.type &&
            category == other.category &&
            targetDirectory == other.targetDirectory &&
            extension == other.extension
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + targetDirectory.hashCode()
        result = 31 * result + extension.hashCode()
        return result
    }
}

/**
 * Result of an [AssetOperationsService] call.
 */
sealed interface AssetOperationResult {
    /** Operation succeeded. [path] is the registry-relative new path (when applicable). */
    data class Success(
        val path: String,
        val message: String,
    ) : AssetOperationResult

    /** Operation failed; [message] is human-readable. */
    data class Failure(
        val message: String,
    ) : AssetOperationResult
}

/**
 * Backend-neutral asset CRUD service.
 *
 * Implementations must:
 *  - log via [Logger]
 *  - never mutate browser state directly
 *  - call [onChanged] after successful mutations so callers can trigger registry refresh
 *  - keep `.krmeta` sidecars in sync with their assets
 *  - preserve [AssetId] on rename, generate a fresh id on duplicate
 */
interface AssetOperationsService {
    fun create(request: CreateAssetRequest): AssetOperationResult

    fun createFolder(
        parentDirectory: String,
        name: String,
    ): AssetOperationResult

    fun rename(
        asset: AssetDescriptor,
        newName: String,
    ): AssetOperationResult

    fun renameDirectory(
        directoryPath: String,
        newName: String,
    ): AssetOperationResult

    fun duplicate(
        asset: AssetDescriptor,
        targetName: String,
    ): AssetOperationResult

    fun duplicateDirectory(
        directoryPath: String,
        targetName: String,
    ): AssetOperationResult

    fun delete(
        asset: AssetDescriptor,
        mode: DeleteMode = DeleteMode.Permanent,
    ): AssetOperationResult

    fun deleteDirectory(
        directoryPath: String,
        mode: DeleteMode = DeleteMode.Trash,
    ): AssetOperationResult

    fun reveal(asset: AssetDescriptor): AssetOperationResult

    fun revealDirectory(directoryPath: String): AssetOperationResult
}

/**
 * Local filesystem [AssetOperationsService].
 *
 * Resolves project-relative asset paths through the shared [AssetRegistryService].
 */
class LocalAssetOperationsService(
    private val registry: AssetRegistryService,
    private val importers: AssetImporterRegistry,
    private val logger: Logger,
    private val onChanged: () -> Unit,
) : AssetOperationsService {
    private val basePath =
        registry
            .baseDir()
            .toPath()
            .toAbsolutePath()
            .normalize()
    private val skinsRootPath = basePath.resolve(normalizePath("ui/skins")).normalize()

    override fun create(request: CreateAssetRequest): AssetOperationResult {
        val baseName = sanitizeAssetBaseName(request.name, defaultAssetBaseName(request.type, request.category))
        val ext = request.extension.lowercase().trimStart('.')
        val dir =
            resolveTargetDirectory(request.targetDirectory)
                ?: return failure("Target directory is outside asset root: '${request.targetDirectory}'")
        if (!dir.exists() && !dir.mkdirs()) {
            return failure("Failed to create directory '${dir.path}'")
        }
        val target = uniqueFile(dir, baseName, ext)
        return runCatching {
            target.writeBytes(request.initialContent ?: ByteArray(0))
            val rel = relativePath(target)
            val importer =
                importers.byCategory(request.category).firstOrNull { it.canImport(rel) }
                    ?: importers.resolve(rel)
            writeMetadata(
                file = target,
                document =
                    AssetMetadataDocument(
                        id = "asset:${UUID.randomUUID()}",
                        type = request.type.name,
                        category = request.category.name,
                        displayName = target.nameWithoutExtension,
                        importerId = importer?.id,
                    ),
            )
            logger.info(TAG) { "Created asset '$rel' (${request.category.name})" }
            onChanged()
            AssetOperationResult.Success(rel, "Created '$rel'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Create failed for '${target.path}': ${error.message}" }
            failure("Create failed: ${error.message}")
        }
    }

    override fun createFolder(
        parentDirectory: String,
        name: String,
    ): AssetOperationResult {
        val parent =
            resolveTargetDirectory(parentDirectory)
                ?: return failure("Target directory is outside asset root: '$parentDirectory'")
        if (!parent.exists() || !parent.isDirectory) return failure("Parent directory does not exist: '$parentDirectory'")
        val folderName = sanitizeFileName(name).ifBlank { "New Folder" }
        val target = uniqueDirectory(parent, folderName)
        return runCatching {
            if (!target.mkdirs()) {
                error("mkdir '${target.name}' failed")
            }
            val rel = relativePath(target)
            logger.info(TAG) { "Created asset folder '$rel'" }
            onChanged()
            AssetOperationResult.Success(rel, "Created folder '$rel'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Create folder failed parent='$parentDirectory': ${error.message}" }
            failure("Create folder failed: ${error.message}")
        }
    }

    override fun rename(
        asset: AssetDescriptor,
        newName: String,
    ): AssetOperationResult {
        if (!asset.assetCapabilities().canRename) return failure("Rename is unavailable for '${asset.path}'")
        val sanitized = sanitizeFileName(newName)
        if (sanitized.isBlank()) return failure("Name cannot be blank")
        val source =
            resolveAssetFile(asset)
                ?: return failure("Asset path is outside asset root: '${asset.path}'")
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")

        val ext = source.extension
        val target = File(source.parentFile, if (ext.isBlank()) sanitized else "$sanitized.$ext")
        if (target.exists()) return failure("'${target.name}' already exists")
        val targetMeta = metadataFileFor(target)
        if (targetMeta.exists()) return failure("Metadata for '${target.name}' already exists")

        return runCatching {
            val sourceMeta = metadataFileFor(source)
            val document = readDocumentOrSynthetic(sourceMeta, asset)
            move(source, target)
            // Preserve AssetId; only update displayName.
            writeMetadata(target, document.copy(displayName = sanitized))
            deleteIfExists(sourceMeta)
            val rel = relativePath(target)
            logger.info(TAG) { "Renamed '${asset.path}' -> '$rel' (id preserved=${document.id})" }
            onChanged()
            AssetOperationResult.Success(rel, "Renamed to '${target.name}'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Rename failed for '${asset.path}': ${error.message}" }
            failure("Rename failed: ${error.message}")
        }
    }

    override fun renameDirectory(
        directoryPath: String,
        newName: String,
    ): AssetOperationResult {
        val normalized = normalizePath(directoryPath)
        if (normalized.isBlank()) return failure("Asset root cannot be renamed")
        val source =
            resolveDirectory(normalized)
                ?: return failure("Directory path is outside asset root: '$directoryPath'")
        if (!source.exists() || !source.isDirectory) return failure("Directory no longer exists at '$normalized'")
        val sanitized = sanitizeFileName(newName)
        if (sanitized.isBlank()) return failure("Name cannot be blank")
        val target = File(source.parentFile, sanitized)
        if (target.exists()) return failure("'${target.name}' already exists")
        return runCatching {
            move(source, target)
            val rel = relativePath(target)
            logger.info(TAG) { "Renamed directory '$normalized' -> '$rel'" }
            onChanged()
            AssetOperationResult.Success(rel, "Renamed folder to '$rel'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Rename directory failed for '$normalized': ${error.message}" }
            failure("Rename folder failed: ${error.message}")
        }
    }

    override fun duplicate(
        asset: AssetDescriptor,
        targetName: String,
    ): AssetOperationResult {
        if (!asset.assetCapabilities().canDuplicate) return failure("Duplicate is unavailable for '${asset.path}'")
        val source =
            resolveAssetFile(asset)
                ?: return failure("Asset path is outside asset root: '${asset.path}'")
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")
        val baseName = sanitizeFileName(targetName).ifBlank { "${source.nameWithoutExtension}_copy" }
        val ext = source.extension
        val target = uniqueFile(source.parentFile, baseName, ext)
        return runCatching {
            copy(source, target)
            val sourceMeta = metadataFileFor(source)
            val sourceDocument = readDocumentOrSynthetic(sourceMeta, asset)
            writeMetadata(
                file = target,
                document =
                    sourceDocument.copy(
                        id = "asset:${UUID.randomUUID()}",
                        displayName = target.nameWithoutExtension,
                    ),
            )
            val rel = relativePath(target)
            logger.info(TAG) { "Duplicated '${asset.path}' -> '$rel' (new id assigned)" }
            onChanged()
            AssetOperationResult.Success(rel, "Duplicated to '${target.name}'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Duplicate failed for '${asset.path}': ${error.message}" }
            failure("Duplicate failed: ${error.message}")
        }
    }

    override fun duplicateDirectory(
        directoryPath: String,
        targetName: String,
    ): AssetOperationResult {
        val normalized = normalizePath(directoryPath)
        if (normalized.isBlank()) return failure("Asset root cannot be duplicated")
        val source =
            resolveDirectory(normalized)
                ?: return failure("Directory path is outside asset root: '$directoryPath'")
        if (!source.exists() || !source.isDirectory) return failure("Directory no longer exists at '$normalized'")
        val targetBaseName = sanitizeFileName(targetName).ifBlank { "${source.name}_copy" }
        val target = uniqueDirectory(source.parentFile, targetBaseName)
        return runCatching {
            copyDirectory(source, target)
            val rel = relativePath(target)
            logger.info(TAG) { "Duplicated directory '$normalized' -> '$rel'" }
            onChanged()
            AssetOperationResult.Success(rel, "Duplicated folder to '$rel'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Duplicate directory failed for '$normalized': ${error.message}" }
            failure("Duplicate folder failed: ${error.message}")
        }
    }

    override fun delete(
        asset: AssetDescriptor,
        mode: DeleteMode,
    ): AssetOperationResult {
        if (!asset.assetCapabilities().canDelete) return failure("Delete is unavailable for '${asset.path}'")
        val source =
            resolveAssetFile(asset)
                ?: return failure("Asset path is outside asset root: '${asset.path}'")
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")
        return runCatching {
            val deletedPath =
                when {
                    asset.type == AssetType.Scene2DSkin -> {
                        val skinFolder = scene2DSkinFolderForDelete(source)
                        if (skinFolder != null) {
                            deleteScene2DSkinFolder(skinFolder, mode)
                            relativePath(skinFolder)
                        } else {
                            deleteSingleAsset(source, mode)
                            asset.path
                        }
                    }

                    else -> {
                        deleteSingleAsset(source, mode)
                        asset.path
                    }
                }
            logger.info(TAG) { "Deleted '$deletedPath' mode=$mode" }
            onChanged()
            AssetOperationResult.Success(asset.path, "Deleted '${asset.name}'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Delete failed for '${asset.path}': ${error.message}" }
            failure("Delete failed: ${error.message}")
        }
    }

    override fun deleteDirectory(
        directoryPath: String,
        mode: DeleteMode,
    ): AssetOperationResult {
        val normalized = normalizePath(directoryPath)
        if (normalized.isBlank()) return failure("Asset root cannot be deleted")
        val source =
            resolveDirectory(normalized)
                ?: return failure("Directory path is outside asset root: '$directoryPath'")
        if (!source.exists() || !source.isDirectory) return failure("Directory no longer exists at '$normalized'")
        if (normalized == ".trash" || normalized.startsWith(".trash/")) return failure("Trash folders cannot be deleted from Asset Browser")
        return runCatching {
            when (mode) {
                DeleteMode.Permanent -> deleteTree(source)
                DeleteMode.Trash -> trashDirectory(source, normalized)
            }
            logger.info(TAG) { "Deleted directory '$normalized' mode=$mode" }
            onChanged()
            AssetOperationResult.Success(normalized, "Moved folder '$normalized' to trash")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Delete directory failed for '$normalized': ${error.message}" }
            failure("Delete folder failed: ${error.message}")
        }
    }

    override fun reveal(asset: AssetDescriptor): AssetOperationResult {
        if (!asset.assetCapabilities().canReveal) return failure("Reveal is unavailable for '${asset.path}'")
        val file =
            resolveAssetFile(asset)
                ?: return failure("Asset path is outside asset root: '${asset.path}'")
        if (!file.exists()) return failure("Asset file no longer exists at '${asset.path}'")
        return runCatching {
            val parent = file.parentFile ?: file
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parent)
            }
            logger.info(TAG) { "Revealed '${asset.path}' in '${parent.path}'" }
            AssetOperationResult.Success(asset.path, "Revealed '${file.name}'")
        }.getOrElse { error ->
            logger.warn(TAG, error) { "Reveal failed for '${asset.path}': ${error.message}" }
            failure("Reveal failed: ${error.message}")
        }
    }

    override fun revealDirectory(directoryPath: String): AssetOperationResult {
        val normalized = normalizePath(directoryPath)
        val directory =
            resolveDirectory(normalized)
                ?: return failure("Directory path is outside asset root: '$directoryPath'")
        if (!directory.exists() || !directory.isDirectory) return failure("Directory no longer exists at '$normalized'")
        return runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory)
            }
            val label = normalized.ifBlank { "." }
            logger.info(TAG) { "Revealed asset directory '$label' in '${directory.path}'" }
            AssetOperationResult.Success(normalized, "Revealed folder '$label'")
        }.getOrElse { error ->
            logger.warn(TAG, error) { "Reveal directory failed for '$normalized': ${error.message}" }
            failure("Reveal folder failed: ${error.message}")
        }
    }

    private fun readDocumentOrSynthetic(
        metadataFile: File,
        asset: AssetDescriptor,
    ): AssetMetadataDocument {
        if (metadataFile.exists()) {
            try {
                return AssetMetadataCodec.decode(metadataFile.readText(StandardCharsets.UTF_8))
            } catch (error: Exception) {
                logger.warn(TAG, error) { "Falling back to descriptor-derived metadata for '${asset.path}'" }
            }
        }
        return AssetMetadataDocument(
            id = asset.id.value,
            type = asset.type.name,
            category = asset.category.name,
            displayName = asset.name,
            tags = asset.tags,
        )
    }

    private fun writeMetadata(
        file: File,
        document: AssetMetadataDocument,
    ) {
        val metadataFile = metadataFileFor(file)
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(AssetMetadataCodec.encode(document), StandardCharsets.UTF_8)
    }

    private fun deleteSingleAsset(
        source: File,
        mode: DeleteMode,
    ) {
        val sourceMeta = metadataFileFor(source)
        when (mode) {
            DeleteMode.Permanent -> {
                deletePath(source)
                deleteIfExists(sourceMeta)
            }

            DeleteMode.Trash -> {
                val trashDir =
                    resolveTargetDirectory(".trash")
                        ?: error("trash directory is outside asset root")
                if (!trashDir.exists()) trashDir.mkdirs()
                val trashed = uniqueFile(trashDir, source.nameWithoutExtension, source.extension)
                move(source, trashed)
                if (sourceMeta.exists()) {
                    move(sourceMeta, metadataFileFor(trashed))
                }
            }
        }
    }

    private fun deleteScene2DSkinFolder(
        folder: File,
        mode: DeleteMode,
    ) {
        val folderPath = folder.toPath().toAbsolutePath().normalize()
        require(folderPath.startsWith(skinsRootPath)) {
            "Scene2D Skin folder escapes ui/skins: ${folder.path}"
        }
        require(folderPath != skinsRootPath) {
            "Refusing to delete ui/skins root"
        }
        require(folderPath.parent == skinsRootPath) {
            "Scene2D Skin delete is only supported for direct folders under ui/skins"
        }
        when (mode) {
            DeleteMode.Permanent -> deleteTree(folder)
            DeleteMode.Trash -> {
                val trashRoot =
                    resolveTargetDirectory(".trash/ui/skins")
                        ?: error("trash directory is outside asset root")
                if (!trashRoot.exists()) trashRoot.mkdirs()
                val trashed = uniqueDirectory(trashRoot, folder.name)
                move(folder, trashed)
            }
        }
    }

    private fun resolveAssetFile(asset: AssetDescriptor): File? = resolveRelativePath(asset.path)

    private fun resolveTargetDirectory(relativePath: String): File? = resolveRelativePath(relativePath)

    private fun resolveDirectory(relativePath: String): File? = resolveRelativePath(relativePath)

    private fun resolveRelativePath(relativePath: String): File? {
        val target = basePath.resolve(normalizePath(relativePath)).normalize()
        return if (target.startsWith(basePath)) target.toFile() else null
    }

    private fun scene2DSkinFolderForDelete(source: File): File? {
        val sourcePath = source.toPath().toAbsolutePath().normalize()
        if (!sourcePath.startsWith(skinsRootPath)) return null
        val parent = sourcePath.parent ?: return null
        if (parent == skinsRootPath) return null
        require(parent.parent == skinsRootPath) {
            "Scene2D Skin delete is only supported for direct folders under ui/skins"
        }
        return parent.toFile()
    }

    private fun metadataFileFor(file: File): File = File(file.parentFile, "${file.name}.krmeta")

    private fun relativePath(file: File): String {
        val target = file.toPath().toAbsolutePath().normalize()
        require(target.startsWith(basePath)) { "Target path escapes asset root: ${file.path}" }
        return normalizePath(basePath.relativize(target).toString())
    }

    private fun uniqueFile(
        directory: File,
        baseName: String,
        extension: String,
    ): File {
        val ext = extension.lowercase().trimStart('.')
        val makeFile: (String) -> File = { name ->
            if (ext.isBlank()) File(directory, name) else File(directory, "$name.$ext")
        }
        if (isAvailable(makeFile(baseName))) return makeFile(baseName)
        var counter = 2
        while (true) {
            val candidate = makeFile("${baseName}_$counter")
            if (isAvailable(candidate)) return candidate
            counter += 1
        }
    }

    private fun uniqueDirectory(
        parent: File,
        baseName: String,
    ): File {
        val initial = File(parent, baseName)
        if (!initial.exists()) return initial
        var counter = 2
        while (true) {
            val candidate = File(parent, "${baseName}_$counter")
            if (!candidate.exists()) return candidate
            counter += 1
        }
    }

    private fun isAvailable(file: File): Boolean = !file.exists() && !metadataFileFor(file).exists()

    private fun move(
        source: File,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        Files.move(source.toPath(), target.toPath())
    }

    private fun copy(
        source: File,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        Files.copy(source.toPath(), target.toPath())
    }

    private fun copyDirectory(
        source: File,
        target: File,
    ) {
        source.walkTopDown().forEach { entry ->
            val relative = source.toPath().relativize(entry.toPath())
            val destination = target.toPath().resolve(relative).toFile()
            if (entry.isDirectory) {
                if (!destination.exists() && !destination.mkdirs()) {
                    error("mkdir '${destination.name}' failed")
                }
            } else {
                copy(entry, destination)
                if (entry.name.endsWith(".krmeta", ignoreCase = true)) {
                    rewriteCopiedMetadata(destination)
                }
            }
        }
    }

    private fun rewriteCopiedMetadata(metadataFile: File) {
        runCatching {
            val document = AssetMetadataCodec.decode(metadataFile.readText(StandardCharsets.UTF_8))
            metadataFile.writeText(
                AssetMetadataCodec.encode(document.copy(id = "asset:${UUID.randomUUID()}")),
                StandardCharsets.UTF_8,
            )
        }.onFailure { error ->
            logger.warn(TAG, error) { "Copied metadata '${relativePath(metadataFile)}' kept unchanged: ${error.message}" }
        }
    }

    private fun trashDirectory(
        source: File,
        normalizedPath: String,
    ) {
        val parentRelative = normalizedPath.substringBeforeLast('/', "")
        val trashParent =
            resolveTargetDirectory(".trash/$parentRelative")
                ?: error("trash directory is outside asset root")
        if (!trashParent.exists() && !trashParent.mkdirs()) {
            error("mkdir '${trashParent.path}' failed")
        }
        val target = uniqueDirectory(trashParent, source.name)
        move(source, target)
    }

    private fun deletePath(file: File) {
        if (!Files.deleteIfExists(file.toPath())) {
            error("delete '${file.name}' failed")
        }
    }

    private fun deleteIfExists(file: File) {
        Files.deleteIfExists(file.toPath())
    }

    private fun deleteTree(root: File) {
        root.walkBottomUp().forEach { entry ->
            deletePath(entry)
        }
    }

    private fun sanitizeFileName(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun failure(message: String): AssetOperationResult.Failure {
        logger.warn(TAG) { message }
        return AssetOperationResult.Failure(message)
    }

    companion object {
        private const val TAG = "LocalAssetOperationsService"

        private fun defaultAssetBaseName(
            type: AssetType,
            category: AssetCategory,
        ): String =
            when (type) {
                AssetType.UiScene -> "new_ui_scene"
                AssetType.Terrain -> "new_terrain"
                AssetType.Scene -> "new_scene"
                else -> error("Unsupported asset creation type=$type category=$category")
            }

        private fun sanitizeAssetBaseName(
            name: String,
            fallback: String,
        ): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { fallback }
    }
}
