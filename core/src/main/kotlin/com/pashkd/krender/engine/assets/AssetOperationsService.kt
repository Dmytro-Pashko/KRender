package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.Logger
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    data class Success(val path: String, val message: String) : AssetOperationResult

    /** Operation failed; [message] is human-readable. */
    data class Failure(val message: String) : AssetOperationResult
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
    fun rename(asset: AssetDescriptor, newName: String): AssetOperationResult
    fun duplicate(asset: AssetDescriptor, targetName: String): AssetOperationResult
    fun delete(asset: AssetDescriptor, mode: DeleteMode = DeleteMode.Permanent): AssetOperationResult
    fun reveal(asset: AssetDescriptor): AssetOperationResult
}

/**
 * Local filesystem [AssetOperationsService]. Resolves paths through [LocalAssetRegistryService].
 */
class LocalAssetOperationsService(
    private val registry: LocalAssetRegistryService,
    private val importers: AssetImporterRegistry,
    private val logger: Logger,
    private val onChanged: () -> Unit,
) : AssetOperationsService {
    override fun create(request: CreateAssetRequest): AssetOperationResult {
        require(CreatableAssetKind.entries.any { kind ->
            kind.type == request.type && kind.category == request.category
        }) {
            "Unsupported asset creation type=${request.type} category=${request.category}"
        }
        val baseName = sanitizedAssetName(request.name, defaultAssetBaseName(request.type, request.category))
        val ext = request.extension.lowercase().trimStart('.')
        val dir = File(registry.baseDir(), request.targetDirectory)
        if (!dir.exists() && !dir.mkdirs()) {
            return failure("Failed to create directory '${dir.path}'")
        }
        val target = uniqueFile(dir, baseName, ext)
        return runCatching {
            target.writeBytes(request.initialContent ?: ByteArray(0))
            val rel = relativePath(target)
            val importer = importers.byCategory(request.category).firstOrNull { it.canImport(rel) }
                ?: importers.resolve(rel)
            writeMetadata(
                file = target,
                document = AssetMetadataDocument(
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

    override fun rename(asset: AssetDescriptor, newName: String): AssetOperationResult {
        if (!asset.assetCapabilities().canRename) return failure("Rename is unavailable for '${asset.path}'")
        val sanitized = sanitizeFileName(newName)
        if (sanitized.isBlank()) return failure("Name cannot be blank")
        val source = registry.resolve(asset)
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")

        val ext = source.extension
        val target = File(source.parentFile, if (ext.isBlank()) sanitized else "$sanitized.$ext")
        if (target.exists()) return failure("'${target.name}' already exists")

        return runCatching {
            val sourceMeta = metadataFileFor(source)
            val document = readDocumentOrSynthetic(sourceMeta, asset)
            if (!source.renameTo(target)) error("rename '${source.name}' -> '${target.name}' failed")
            val targetMeta = metadataFileFor(target)
            if (sourceMeta.exists()) sourceMeta.renameTo(targetMeta)
            // Preserve AssetId; only update displayName.
            writeMetadata(target, document.copy(displayName = sanitized))
            val rel = relativePath(target)
            logger.info(TAG) { "Renamed '${asset.path}' -> '$rel' (id preserved=${document.id})" }
            onChanged()
            AssetOperationResult.Success(rel, "Renamed to '${target.name}'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Rename failed for '${asset.path}': ${error.message}" }
            failure("Rename failed: ${error.message}")
        }
    }

    override fun duplicate(asset: AssetDescriptor, targetName: String): AssetOperationResult {
        if (!asset.assetCapabilities().canDuplicate) return failure("Duplicate is unavailable for '${asset.path}'")
        val source = registry.resolve(asset)
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")
        val baseName = sanitizeFileName(targetName).ifBlank { "${source.nameWithoutExtension}_copy" }
        val ext = source.extension
        val target = uniqueFile(source.parentFile, baseName, ext)
        return runCatching {
            source.copyTo(target, overwrite = false)
            val sourceMeta = metadataFileFor(source)
            val sourceDocument = readDocumentOrSynthetic(sourceMeta, asset)
            writeMetadata(
                file = target,
                document = sourceDocument.copy(
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

    override fun delete(asset: AssetDescriptor, mode: DeleteMode): AssetOperationResult {
        if (!asset.assetCapabilities().canDelete) return failure("Delete is unavailable for '${asset.path}'")
        val source = registry.resolve(asset)
        if (!source.exists()) return failure("Asset file no longer exists at '${asset.path}'")
        val sourceMeta = metadataFileFor(source)
        return runCatching {
            when (mode) {
                DeleteMode.Permanent -> {
                    if (!source.delete()) error("delete '${source.name}' failed")
                    if (sourceMeta.exists()) sourceMeta.delete()
                }
                DeleteMode.Trash -> {
                    val trashDir = File(registry.baseDir(), ".trash")
                    if (!trashDir.exists()) trashDir.mkdirs()
                    val trashed = uniqueFile(trashDir, source.nameWithoutExtension, source.extension)
                    if (!source.renameTo(trashed)) error("move to trash failed")
                    if (sourceMeta.exists()) sourceMeta.renameTo(File(trashed.parentFile, "${trashed.name}.krmeta"))
                }
            }
            logger.info(TAG) { "Deleted '${asset.path}' mode=$mode" }
            onChanged()
            AssetOperationResult.Success(asset.path, "Deleted '${asset.name}'")
        }.getOrElse { error ->
            logger.error(TAG, error) { "Delete failed for '${asset.path}': ${error.message}" }
            failure("Delete failed: ${error.message}")
        }
    }

    override fun reveal(asset: AssetDescriptor): AssetOperationResult {
        if (!asset.assetCapabilities().canReveal) return failure("Reveal is unavailable for '${asset.path}'")
        val file = registry.resolve(asset)
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

    private fun readDocumentOrSynthetic(metadataFile: File, asset: AssetDescriptor): AssetMetadataDocument {
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

    private fun writeMetadata(file: File, document: AssetMetadataDocument) {
        val metadataFile = metadataFileFor(file)
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(AssetMetadataCodec.encode(document), StandardCharsets.UTF_8)
    }

    private fun metadataFileFor(file: File): File = File(file.parentFile, "${file.name}.krmeta")

    private fun relativePath(file: File): String {
        val base = registry.baseDir().toPath().toAbsolutePath().normalize()
        val target = file.toPath().toAbsolutePath().normalize()
        return normalizePath(base.relativize(target).toString())
    }

    private fun uniqueFile(directory: File, baseName: String, extension: String): File {
        val ext = extension.lowercase().trimStart('.')
        val makeFile: (String) -> File = { name ->
            if (ext.isBlank()) File(directory, name) else File(directory, "$name.$ext")
        }
        if (!makeFile(baseName).exists()) return makeFile(baseName)
        var counter = 2
        while (true) {
            val candidate = makeFile("${baseName}_$counter")
            if (!candidate.exists()) return candidate
            counter += 1
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun failure(message: String): AssetOperationResult.Failure {
        logger.warn(TAG) { message }
        return AssetOperationResult.Failure(message)
    }

    companion object {
        private const val TAG = "LocalAssetOperationsService"
    }
}
