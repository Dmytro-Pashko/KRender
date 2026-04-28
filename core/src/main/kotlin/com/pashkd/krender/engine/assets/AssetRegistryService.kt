package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Asset registry API used by editor browser systems.
 */
interface AssetRegistryService {
    fun scan()
    fun all(): List<AssetDescriptor>
    fun findById(id: AssetId): AssetDescriptor?
    fun findByPath(path: String): AssetDescriptor?
    fun byCategory(category: AssetCategory): List<AssetDescriptor>
    fun refresh(path: String)
}

/**
 * Local filesystem registry that scans project asset roots and maintains .krmeta sidecars.
 */
class LocalAssetRegistryService(
    private val logger: Logger,
    private val baseDirectory: File = defaultBaseDirectory(),
    private val rootPaths: List<String> = DefaultRootPaths,
) : AssetRegistryService {
    private var descriptors: List<AssetDescriptor> = emptyList()

    /**
     * Scans configured roots, creates missing metadata, and updates the in-memory descriptor list.
     */
    override fun scan() {
        logger.info(TAG) {
            "Asset scan started base='${baseDirectory.path}' roots=${rootPaths.joinToString(", ")}"
        }
        val scanned = mutableListOf<AssetDescriptor>()
        rootPaths.distinct().forEach { rootPath ->
            val root = File(baseDirectory, rootPath)
            if (!root.exists()) return@forEach
            if (!root.isDirectory) {
                logger.warn(TAG) { "Asset root '$rootPath' is not a directory. Skipping." }
                return@forEach
            }

            root.walkTopDown()
                .filter(File::isFile)
                .filterNot(::isIgnoredFile)
                .forEach { file ->
                    try {
                        scanned += describe(file)
                    } catch (error: Exception) {
                        logger.warn(TAG, error) { "Failed to index asset '${file.path}': ${error.message}" }
                    }
                }
        }

        descriptors = scanned
            .distinctBy(AssetDescriptor::path)
            .sortedWith(compareBy<AssetDescriptor> { it.category.sortOrder }.thenBy { it.name.lowercase() })
        logger.info(TAG) { "Asset scan completed assets=${descriptors.size}" }
    }

    override fun all(): List<AssetDescriptor> = descriptors

    override fun findById(id: AssetId): AssetDescriptor? =
        descriptors.firstOrNull { it.id == id }

    override fun findByPath(path: String): AssetDescriptor? {
        val normalized = normalizePath(path)
        return descriptors.firstOrNull { it.path == normalized }
    }

    override fun byCategory(category: AssetCategory): List<AssetDescriptor> =
        descriptors.filter { it.category == category }

    /**
     * Refreshes registry contents after an asset path changes.
     */
    override fun refresh(path: String) {
        logger.info(TAG) { "Refreshing asset registry for '${normalizePath(path)}'" }
        scan()
    }

    private fun describe(file: File): AssetDescriptor {
        val path = relativeAssetPath(file)
        val detection = AssetTypeDetector.detect(path)
        val metadata = readOrCreateMetadata(file, detection)
        val extension = file.extension.lowercase()
        val displayName = metadata.displayName.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
        val type = metadata.type.takeUnless { it == AssetType.Unknown } ?: detection.type
        val category = metadata.category.takeUnless { it == AssetCategory.Unknown } ?: detection.category
        val descriptorMetadata = metadata.metadata + textureMetadata(file, category)
        return AssetDescriptor(
            id = metadata.id,
            name = displayName,
            path = path,
            category = category,
            type = type,
            extension = extension,
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified(),
            tags = metadata.tags,
            metadata = descriptorMetadata,
        )
    }

    private fun textureMetadata(file: File, category: AssetCategory): Map<String, String> {
        if (category != AssetCategory.Texture) return emptyMap()
        val texture = TextureMetadataReader.read(file) ?: return emptyMap()
        return mapOf(
            "textureResolution" to "${texture.width}x${texture.height}",
            "textureWidth" to texture.width.toString(),
            "textureHeight" to texture.height.toString(),
            "textureHasAlpha" to texture.hasAlpha.toString(),
            "textureColorFormat" to texture.colorFormat,
        )
    }

    private fun readOrCreateMetadata(file: File, detection: AssetTypeDetection): AssetMetadata {
        val metadataFile = metadataFileFor(file)
        if (!metadataFile.exists()) {
            return createMetadata(metadataFile, file, detection, reason = "missing")
        }

        val text = metadataFile.readText(StandardCharsets.UTF_8)
        return try {
            AssetMetadataCodec.decode(text, fallbackDisplayName = file.nameWithoutExtension)
        } catch (error: Exception) {
            logger.warn(TAG, error) {
                "Malformed metadata '${relativeAssetPath(metadataFile)}'. Recreating safe metadata: ${error.message}"
            }
            createMetadata(metadataFile, file, detection, reason = "malformed")
        }
    }

    private fun createMetadata(
        metadataFile: File,
        assetFile: File,
        detection: AssetTypeDetection,
        reason: String,
    ): AssetMetadata {
        val metadata = AssetMetadata(
            id = AssetId("asset:${UUID.randomUUID()}"),
            type = detection.type,
            category = detection.category,
            displayName = assetFile.nameWithoutExtension,
            tags = emptyList(),
            metadata = mapOf(
                "displayName" to assetFile.nameWithoutExtension,
                "importSettings" to "{}",
            ),
        )
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(AssetMetadataCodec.encode(metadata), StandardCharsets.UTF_8)
        logger.info(TAG) { "Created .krmeta (${reason}) '${relativeAssetPath(metadataFile)}' id='${metadata.id.value}'" }
        return metadata
    }

    private fun metadataFileFor(file: File): File =
        File(file.parentFile, "${file.name}.krmeta")

    private fun relativeAssetPath(file: File): String {
        val basePath = baseDirectory.toPath().toAbsolutePath().normalize()
        val filePath = file.toPath().toAbsolutePath().normalize()
        return normalizePath(basePath.relativize(filePath).toString())
    }

    private fun isIgnoredFile(file: File): Boolean =
        file.name.endsWith(".krmeta", ignoreCase = true) ||
            file.name.startsWith(".") ||
            file.isHidden

    companion object {
        private const val TAG = "LocalAssetRegistryService"
        val DefaultRootPaths = listOf("model", "textures", "materials", "terrains", "shaders", "assets")

        private fun defaultBaseDirectory(): File {
            val current = File(".")
            val hasDirectRoot = DefaultRootPaths.any { root ->
                root != "assets" && File(current, root).isDirectory
            }
            val assetDirectory = File(current, "assets")
            return if (!hasDirectRoot && assetDirectory.isDirectory) assetDirectory else current
        }
    }
}

/**
 * In-memory representation of .krmeta files.
 */
private data class AssetMetadata(
    val id: AssetId,
    val type: AssetType,
    val category: AssetCategory,
    val displayName: String,
    val tags: List<String>,
    val metadata: Map<String, String>,
)

/**
 * Minimal JSON codec for the flat .krmeta shape used by the asset browser.
 */
private object AssetMetadataCodec {
    fun encode(metadata: AssetMetadata): String =
        buildString {
            appendLine("{")
            appendLine("  \"id\": \"${escape(metadata.id.value)}\",")
            appendLine("  \"type\": \"${metadata.type.name}\",")
            appendLine("  \"category\": \"${metadata.category.name}\",")
            appendLine("  \"displayName\": \"${escape(metadata.displayName)}\",")
            appendLine("  \"tags\": [${metadata.tags.joinToString(", ") { "\"${escape(it)}\"" }}],")
            appendLine("  \"importSettings\": {}")
            appendLine("}")
        }

    fun decode(text: String, fallbackDisplayName: String): AssetMetadata {
        val id = readString(text, "id")?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("missing id")
        val type = readEnum<AssetType>(text, "type") ?: AssetType.Unknown
        val category = readEnum<AssetCategory>(text, "category") ?: AssetCategory.Unknown
        val displayName = readString(text, "displayName")?.takeIf(String::isNotBlank) ?: fallbackDisplayName
        val tags = readStringArray(text, "tags")
        return AssetMetadata(
            id = AssetId(id),
            type = type,
            category = category,
            displayName = displayName,
            tags = tags,
            metadata = mapOf(
                "displayName" to displayName,
                "importSettings" to readObjectText(text, "importSettings"),
            ),
        )
    }

    private inline fun <reified T : Enum<T>> readEnum(text: String, name: String): T? =
        readString(text, name)?.let { value ->
            enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        }

    private fun readString(text: String, name: String): String? {
        val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return pattern.find(text)?.groupValues?.get(1)?.let(::unescape)
    }

    private fun readStringArray(text: String, name: String): List<String> {
        val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val body = pattern.find(text)?.groupValues?.get(1) ?: return emptyList()
        return Regex("\"((?:\\\\.|[^\"])*)\"").findAll(body).map { unescape(it.groupValues[1]) }.toList()
    }

    private fun readObjectText(text: String, name: String): String {
        val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*(\\{[^}]*})", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: "{}"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescape(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")
}
