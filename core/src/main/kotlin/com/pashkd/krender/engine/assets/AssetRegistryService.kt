package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.api.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Immutable result of a registry scan, suitable for posting from a background thread.
 */
data class AssetRegistrySnapshot(
    val assets: List<AssetDescriptor>,
    val scannedAtMillis: Long,
    val durationMillis: Long,
    val errors: List<AssetRegistryError>,
)

/**
 * Structured scan error.
 */
data class AssetRegistryError(
    val path: String,
    val message: String,
)

/**
 * Asset registry API used by editor browser systems.
 *
 * The scan is split into [scanSnapshot] (safe for background threads, returns an immutable snapshot)
 * and [applySnapshot] (must run on the main thread, mutates the in-memory descriptor list).
 */
interface AssetRegistryService {
    /** Current main-thread descriptor list. */
    val assets: List<AssetDescriptor>

    /** Performs filesystem scan. Safe to call from a background thread. */
    fun scanSnapshot(): AssetRegistrySnapshot

    /** Applies a snapshot to the in-memory state. Must be called on the main thread. */
    fun applySnapshot(snapshot: AssetRegistrySnapshot)

    fun findById(id: AssetId): AssetDescriptor?
    fun findByPath(path: String): AssetDescriptor?
    fun byCategory(category: AssetCategory): List<AssetDescriptor>
}

/**
 * Local filesystem registry that scans project asset roots and maintains `.krmeta` sidecars.
 */
class LocalAssetRegistryService(
    private val logger: Logger,
    private val importers: AssetImporterRegistry,
    private val baseDirectory: File = defaultBaseDirectory(),
    private val rootPaths: List<String> = DefaultRootPaths,
) : AssetRegistryService {
    @Volatile
    private var descriptors: List<AssetDescriptor> = emptyList()

    override val assets: List<AssetDescriptor>
        get() = descriptors

    /**
     * Scans configured roots, creates missing metadata, and returns an immutable snapshot.
     *
     * Pure IO — safe for background threads. Does not mutate registry state.
     */
    override fun scanSnapshot(): AssetRegistrySnapshot {
        val started = System.currentTimeMillis()
        logger.info(TAG) {
            "Asset scan started base='${baseDirectory.path}' roots=${rootPaths.joinToString(", ")}"
        }
        val scanned = mutableListOf<AssetDescriptor>()
        val errors = mutableListOf<AssetRegistryError>()
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
                        val rel = relativeAssetPath(file)
                        logger.warn(TAG, error) { "Failed to index asset '$rel': ${error.message}" }
                        errors += AssetRegistryError(rel, error.message ?: error.javaClass.simpleName)
                    }
                }
        }

        val finalAssets = scanned
            .distinctBy(AssetDescriptor::path)
            .sortedWith(compareBy<AssetDescriptor> { it.category.sortOrder }.thenBy { it.name.lowercase() })
        val finished = System.currentTimeMillis()
        logger.info(TAG) {
            "Asset scan completed assets=${finalAssets.size} errors=${errors.size} duration=${finished - started}ms"
        }
        return AssetRegistrySnapshot(
            assets = finalAssets,
            scannedAtMillis = finished,
            durationMillis = finished - started,
            errors = errors,
        )
    }

    override fun applySnapshot(snapshot: AssetRegistrySnapshot) {
        descriptors = snapshot.assets
        logger.info(TAG) { "Snapshot applied assets=${snapshot.assets.size} errors=${snapshot.errors.size}" }
    }

    override fun findById(id: AssetId): AssetDescriptor? =
        descriptors.firstOrNull { it.id == id }

    override fun findByPath(path: String): AssetDescriptor? {
        val normalized = normalizePath(path)
        return descriptors.firstOrNull { it.path == normalized }
    }

    override fun byCategory(category: AssetCategory): List<AssetDescriptor> =
        descriptors.filter { it.category == category }

    private fun describe(file: File): AssetDescriptor {
        val path = relativeAssetPath(file)
        val importer = importers.resolve(path)
        val detection = if (importer != null) {
            AssetTypeDetection(importer.outputType, importer.outputCategory)
        } else {
            AssetTypeDetector.detect(path)
        }
        val document = readOrCreateMetadata(file, detection, importer)
        val extension = file.extension.lowercase()
        val displayName = document.displayName.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
        val type = enumValueOrNull<AssetType>(document.type)?.takeUnless { it == AssetType.Unknown } ?: detection.type
        val category = enumValueOrNull<AssetCategory>(document.category)?.takeUnless { it == AssetCategory.Other } ?: detection.category
        val descriptorMetadata = buildMap<String, String> {
            put("displayName", displayName)
            put("sourcePath", path)
            put("importSettings", encodeImportSettings(document.importSettings))
            document.importerId?.let { put("importerId", it) }
            putAll(importer?.readMetadata(file) ?: emptyMap())
            putAll(textureMetadata(file, category))
            putAll(terrainMetadata(file, category))
            putAll(sceneMetadata(file, category))
        }
        return AssetDescriptor(
            id = AssetId(document.id),
            name = displayName,
            path = path,
            category = category,
            type = type,
            extension = extension,
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified(),
            tags = document.tags,
            metadata = descriptorMetadata,
        )
    }

    private fun encodeImportSettings(settings: Map<String, Any?>): String {
        if (settings.isEmpty()) return "{}"
        return settings.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=$v" }
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

    private fun terrainMetadata(file: File, category: AssetCategory): Map<String, String> {
        if (category != AssetCategory.Terrain) return emptyMap()
        val terrain = TerrainMetadataReader.read(file) ?: return emptyMap()
        return mapOf(
            "terrainSize" to terrain.size,
            "terrainWidth" to terrain.width.toString(),
            "terrainHeight" to terrain.height.toString(),
            "terrainLayerCount" to terrain.layerCount.toString(),
        )
    }

    private fun sceneMetadata(file: File, category: AssetCategory): Map<String, String> {
        if (category != AssetCategory.Scene) return emptyMap()
        return try {
            SceneAssetMetadataReader.read(file, baseDirectory).toMetadataMap()
        } catch (error: Exception) {
            logger.warn(TAG, error) {
                "Failed to read scene metadata '${relativeAssetPath(file)}': ${error.message}"
            }
            emptyMap()
        }
    }

    private fun readOrCreateMetadata(
        file: File,
        detection: AssetTypeDetection,
        importer: AssetImporter?,
    ): AssetMetadataDocument {
        val metadataFile = metadataFileFor(file)
        if (!metadataFile.exists()) {
            return createMetadata(metadataFile, file, detection, importer, reason = "missing")
        }

        val text = metadataFile.readText(StandardCharsets.UTF_8)
        return try {
            val parsed = AssetMetadataCodec.decode(text)
            if (parsed.displayName.isBlank()) parsed.copy(displayName = file.nameWithoutExtension) else parsed
        } catch (error: Exception) {
            logger.warn(TAG, error) {
                "Malformed metadata '${relativeAssetPath(metadataFile)}'. Recreating safe metadata: ${error.message}"
            }
            createMetadata(metadataFile, file, detection, importer, reason = "malformed")
        }
    }

    private fun createMetadata(
        metadataFile: File,
        assetFile: File,
        detection: AssetTypeDetection,
        importer: AssetImporter?,
        reason: String,
    ): AssetMetadataDocument {
        val document = AssetMetadataDocument(
            id = "asset:${UUID.randomUUID()}",
            type = detection.type.name,
            category = detection.category.name,
            displayName = assetFile.nameWithoutExtension,
            tags = emptyList(),
            importerId = importer?.id,
            importSettings = emptyMap(),
        )
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(AssetMetadataCodec.encode(document), StandardCharsets.UTF_8)
        logger.info(TAG) { "Created .krmeta (${reason}) '${relativeAssetPath(metadataFile)}' id='${document.id}'" }
        return document
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

    /** Returns the base directory used to resolve relative asset paths. */
    fun baseDir(): File = baseDirectory

    /** Resolves a relative descriptor path to a concrete [File]. */
    fun resolve(descriptor: AssetDescriptor): File = File(baseDirectory, descriptor.path)

    companion object {
        private const val TAG = "LocalAssetRegistryService"

        /**
         * Asset root folders scanned by the local registry.
         *
         * The `ui/scenes` entry exists so `.krui` UiScene documents under `assets/ui/scenes` are indexed by
         * Asset Browser. `ui/skins` indexes LibGDX Scene2D Skin JSON descriptors for `.krui` creation and
         * future Skin tooling. These are asset-indexing routes only; they do not enable UI Composer editing,
         * preview rendering, Skin editing, drag/drop editing, or asset-id based references.
         */
        val DefaultRootPaths = listOf(
            "model",
            "textures",
            "skyboxes",
            "materials",
            "terrains",
            "ui/scenes",
            "ui/skins",
            "scenes",
            "assets",
        )

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

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
