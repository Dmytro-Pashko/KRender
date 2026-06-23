package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetRegistryService
import java.io.File

class TextureManagerProjectLoader(
    private val logger: Logger,
    private val assetRegistry: AssetRegistryService,
    private val atlasParser: TextureAtlasParser = TextureAtlasParser(),
    private val metadataService: TextureMetadataService = TextureMetadataService(),
) {
    /**
     * Resolves the user-provided path, classifies it by type/extension, scans a
     * working directory, and builds the neutral project snapshot used by the UI.
     */
    fun load(inputPath: String?): TextureManagerLoadResult {
        val normalizedInput = inputPath?.trim()?.replace('\\', '/')?.ifBlank { null }
        if (normalizedInput == null) {
            return TextureManagerLoadResult(
                project = TextureManagerProject(),
                diagnostics =
                    listOf(
                        TextureManagerDiagnostic(
                            severity = TextureManagerDiagnosticSeverity.Info,
                            category = TextureManagerDiagnosticCategory.Input,
                            message = "Open a texture, atlas, or directory to begin.",
                        ),
                    ),
            )
        }
        val resolved = resolveInput(normalizedInput)
        logger.info(TAG) { "Resolved Texture Manager input raw='$normalizedInput' resolved='${resolved?.path ?: "<missing>"}'" }
        if (resolved == null || !resolved.exists()) {
            return TextureManagerLoadResult(
                project = TextureManagerProject(inputPath = normalizedInput, resolvedInputPath = resolved?.path),
                diagnostics =
                    listOf(
                        TextureManagerDiagnostic(
                            severity = TextureManagerDiagnosticSeverity.Error,
                            category = TextureManagerDiagnosticCategory.FileSystem,
                            message = "Input path was not found.",
                            source = normalizedInput,
                        ),
                    ),
            )
        }

        val diagnostics = mutableListOf<TextureManagerDiagnostic>()
        val rootDirectory = if (resolved.isDirectory) resolved else resolved.parentFile ?: resolved.absoluteFile.parentFile
        val discoveredFiles = scanRoot(rootDirectory)
        val atlasDocuments =
            discoveredFiles.atlases.associate { file ->
                val atlas = atlasParser.parse(file)
                logger.info(TAG) { "Parsed atlas '${normalizePath(file.path)}' pages=${atlas.pages.size} regions=${atlas.regions.size} readable=${atlas.readable}" }
                atlas.diagnostics.forEach(diagnostics::add)
                atlas.pages.forEach { page ->
                    val pageFile = resolveAtlasPage(file, page.name)
                    if (pageFile == null || !pageFile.isFile) {
                        diagnostics +=
                            TextureManagerDiagnostic(
                                severity = TextureManagerDiagnosticSeverity.Warning,
                                category = TextureManagerDiagnosticCategory.Atlas,
                                message = "Atlas page texture '${page.name}' is missing.",
                                source = normalizePath(file.path),
                            )
                    }
                }
                normalizePath(file.path) to atlas
            }

        val assets =
            buildList {
                discoveredFiles.textures.forEach { file ->
                    add(describeAsset(file, TextureManagerAssetKind.Texture))
                }
                discoveredFiles.atlases.forEach { file ->
                    add(describeAsset(file, TextureManagerAssetKind.Atlas))
                }
            }.sortedBy { asset -> asset.displayName.lowercase() }

        val selectedAsset =
            when {
                resolved.isDirectory -> null
                isTextureFile(resolved) -> normalizePath(resolved.path)
                isAtlasFile(resolved) -> normalizePath(resolved.path)
                else -> null
            }
        if (!resolved.isDirectory && !isTextureFile(resolved) && !isAtlasFile(resolved)) {
            diagnostics +=
                TextureManagerDiagnostic(
                    severity = TextureManagerDiagnosticSeverity.Warning,
                    category = TextureManagerDiagnosticCategory.Input,
                    message = "Unsupported file extension '${resolved.extension}'.",
                    source = normalizePath(resolved.path),
                )
            logger.warn(TAG) { "Unsupported Texture Manager file extension path='${normalizePath(resolved.path)}'" }
        }

        assets.forEach { asset ->
            if (asset.metadataPath == null) {
                diagnostics +=
                    TextureManagerDiagnostic(
                        severity = TextureManagerDiagnosticSeverity.Info,
                        category = TextureManagerDiagnosticCategory.Metadata,
                        message = "Metadata sidecar is missing for '${asset.fileName}'.",
                        source = asset.path,
                    )
            }
        }

        return TextureManagerLoadResult(
            project =
                TextureManagerProject(
                    inputPath = normalizedInput,
                    resolvedInputPath = normalizePath(resolved.path),
                    rootDirectory = rootDirectory,
                    selectedTexturePath = selectedAsset?.takeIf { isTextureFile(resolved) },
                    selectedAtlasPath = selectedAsset?.takeIf { isAtlasFile(resolved) },
                    discoveredTextureFiles = discoveredFiles.textures,
                    discoveredAtlasFiles = discoveredFiles.atlases,
                    discoveredMetadataFiles = discoveredFiles.metadata,
                    assets = assets,
                    atlasDocuments = atlasDocuments,
                ),
            diagnostics = diagnostics,
        )
    }

    private fun resolveInput(path: String): File? {
        val direct = File(path)
        if (direct.exists()) return direct
        val fromBase = File(assetRegistry.baseDir(), path)
        if (fromBase.exists()) return fromBase
        return direct
    }

    private fun scanRoot(rootDirectory: File?): ScanResult {
        if (rootDirectory == null || !rootDirectory.isDirectory) {
            return ScanResult()
        }
        val textures = mutableListOf<File>()
        val atlases = mutableListOf<File>()
        val metadata = mutableListOf<File>()
        rootDirectory
            .walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                when {
                    file.name.endsWith(".krmeta", ignoreCase = true) -> metadata += file
                    isAtlasFile(file) -> atlases += file
                    isTextureFile(file) -> textures += file
                }
            }
        return ScanResult(
            textures = textures.sortedBy { file -> file.name },
            atlases = atlases.sortedBy { file -> file.name },
            metadata = metadata.sortedBy { file -> file.name },
        )
    }

    private fun describeAsset(
        file: File,
        kind: TextureManagerAssetKind,
    ): TextureManagerAssetDescriptor {
        val normalizedPath = normalizePath(file.path)
        val metadataFile = File(file.parentFile, "${file.name}.krmeta").takeIf(File::isFile)
        val registryDescriptor =
            assetRegistry.findByPath(normalizedPath.removePrefix(normalizePath(assetRegistry.baseDir().path)).trimStart('/'))
                ?: assetRegistry.findByPath(normalizedPath)
        return TextureManagerAssetDescriptor(
            id = TextureAssetId(normalizedPath),
            path = normalizedPath,
            displayName = file.name,
            kind = kind,
            extension = file.extension.lowercase(),
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified(),
            metadataPath = metadataFile?.let { normalizePath(it.path) },
            textureInfo = if (kind == TextureManagerAssetKind.Texture) metadataService.read(file) else null,
            registryMetadata = registryDescriptor?.metadata ?: emptyMap(),
        )
    }

    private fun resolveAtlasPage(
        atlasFile: File,
        pageName: String,
    ): File? {
        val pageFile = File(pageName)
        if (pageFile.isAbsolute) return pageFile
        val atlasParent = atlasFile.parentFile ?: return null
        return File(atlasParent, pageName)
    }

    private data class ScanResult(
        val textures: List<File> = emptyList(),
        val atlases: List<File> = emptyList(),
        val metadata: List<File> = emptyList(),
    )

    companion object {
        private const val TAG = "TextureManagerLoader"

        private val SupportedTextureExtensions = setOf("png", "jpg", "jpeg", "ktx", "webp")

        fun isTextureFile(file: File): Boolean = file.isFile && file.extension.lowercase() in SupportedTextureExtensions

        fun isAtlasFile(file: File): Boolean = file.isFile && file.extension.equals("atlas", ignoreCase = true)
    }
}

data class TextureManagerLoadResult(
    val project: TextureManagerProject,
    val diagnostics: List<TextureManagerDiagnostic> = emptyList(),
)

internal fun normalizePath(path: String): String = path.replace('\\', '/')
