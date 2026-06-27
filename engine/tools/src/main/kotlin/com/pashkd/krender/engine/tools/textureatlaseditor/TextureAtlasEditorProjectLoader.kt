package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetRegistryService
import java.io.File

class TextureAtlasEditorProjectLoader(
    private val logger: Logger,
    private val assetRegistry: AssetRegistryService,
    private val atlasParser: TextureAtlasParser = TextureAtlasParser(),
    private val metadataService: TextureMetadataService = TextureMetadataService(),
    private val ninePatchParser: NinePatchParser = NinePatchParser(),
    private val ninePatchPixelReader: NinePatchPixelReader? = null,
    private val bitmapFontParser: BitmapFontParser = BitmapFontParser(),
) {
    /**
     * Resolves the user-provided path, classifies it by type/extension, scans a
     * working directory, and builds the neutral project snapshot used by the UI.
     */
    fun load(inputPath: String?): TextureAtlasEditorLoadResult {
        val normalizedInput = inputPath?.trim()?.replace('\\', '/')?.ifBlank { null }
        if (normalizedInput == null) {
            return TextureAtlasEditorLoadResult(
                project = TextureAtlasEditorProject(),
                diagnostics =
                    listOf(
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Info,
                            category = TextureAtlasEditorDiagnosticCategory.Input,
                            message = "Open a texture atlas to begin. Add textures and bitmap fonts as resources.",
                        ),
                    ),
            )
        }
        val resolved = resolveInput(normalizedInput)
        logger.info(TAG) { "Resolved Texture Atlas Editor input raw='$normalizedInput' resolved='${resolved?.path ?: "<missing>"}'" }
        if (resolved == null || !resolved.exists()) {
            return TextureAtlasEditorLoadResult(
                project = TextureAtlasEditorProject(inputPath = normalizedInput, resolvedInputPath = resolved?.path),
                diagnostics =
                    listOf(
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Error,
                            category = TextureAtlasEditorDiagnosticCategory.FileSystem,
                            message = "Input path was not found.",
                            source = normalizedInput,
                        ),
                    ),
            )
        }

        val diagnostics = mutableListOf<TextureAtlasEditorDiagnostic>()
        val assetRoot = assetRegistry.baseDir()
        val rootDirectory =
            when {
                resolved.isDirectory -> resolved
                isAtlasFile(resolved) && TextureAtlasEditorPathValidator.isInsideRoot(assetRoot, resolved) -> assetRoot
                else -> resolved.parentFile ?: resolved.absoluteFile.parentFile
            }
        val discoveredFiles = scanRoot(rootDirectory)
        diagnostics += discoveredFiles.diagnostics
        val atlasDocuments =
            discoveredFiles.atlases.associate { file ->
                val atlas = enrichAtlasDocument(file, atlasParser.parse(file), diagnostics)
                logger.info(TAG) { "Parsed atlas '${normalizePath(file.path)}' pages=${atlas.pages.size} regions=${atlas.regions.size} readable=${atlas.readable}" }
                atlas.diagnostics.forEach(diagnostics::add)
                normalizeAssetPath(file.path) to atlas
            }
        val ninePatchDocuments = loadNinePatchDocuments(discoveredFiles.textures, diagnostics)
        val fontDocuments = loadFontDocuments(discoveredFiles.fonts, diagnostics)

        val assets =
            buildList {
                discoveredFiles.textures.forEach { file ->
                    add(describeAsset(file, TextureAtlasEditorAssetKind.Texture))
                }
                discoveredFiles.atlases.forEach { file ->
                    add(describeAsset(file, TextureAtlasEditorAssetKind.Atlas))
                }
            }.sortedBy { asset -> asset.displayName.lowercase() }

        val selectedAsset =
            when {
                resolved.isDirectory -> null
                isTextureFile(resolved) -> normalizeAssetPath(resolved.path)
                isAtlasFile(resolved) -> normalizeAssetPath(resolved.path)
                else -> null
            }
        if (!resolved.isDirectory && !isTextureFile(resolved) && !isAtlasFile(resolved)) {
            diagnostics +=
                TextureAtlasEditorDiagnostic(
                    severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                    category = TextureAtlasEditorDiagnosticCategory.Input,
                    message = "Unsupported file extension '${resolved.extension}'.",
                    source = normalizePath(resolved.path),
                )
            logger.warn(TAG) { "Unsupported Texture Atlas Editor file extension path='${normalizePath(resolved.path)}'" }
        }

        assets.forEach { asset ->
            if (asset.metadataPath == null) {
                diagnostics +=
                    TextureAtlasEditorDiagnostic(
                        severity = TextureAtlasEditorDiagnosticSeverity.Info,
                        category = TextureAtlasEditorDiagnosticCategory.Metadata,
                        message = "Metadata sidecar is missing for '${asset.fileName}'.",
                        source = asset.path,
                    )
            }
        }

        return TextureAtlasEditorLoadResult(
            project =
                TextureAtlasEditorProject(
                    inputPath = normalizedInput,
                    resolvedInputPath = normalizePath(resolved.path),
                    rootDirectory = rootDirectory,
                    selectedTexturePath = selectedAsset?.takeIf { isTextureFile(resolved) },
                    selectedAtlasPath = selectedAsset?.takeIf { isAtlasFile(resolved) },
                    discoveredTextureFiles = discoveredFiles.textures,
                    discoveredAtlasFiles = discoveredFiles.atlases,
                    discoveredMetadataFiles = discoveredFiles.metadata,
                    assets = assets,
                    discoveredFontFiles = discoveredFiles.fonts,
                    atlasDocuments = atlasDocuments,
                    ninePatchDocuments = ninePatchDocuments,
                    fontDocuments = fontDocuments,
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
        val fonts = mutableListOf<File>()
        val diagnostics = mutableListOf<TextureAtlasEditorDiagnostic>()
        var scannedFiles = 0
        rootDirectory
            .walkTopDown()
            .onEnter { directory ->
                val skip =
                    directory != rootDirectory &&
                        (directory.isHidden || directory.name in IgnoredDirectoryNames)
                if (skip) {
                    logger.debug(TAG) { "Skipping Texture Atlas Editor directory '${normalizePath(directory.path)}'" }
                }
                !skip
            }.filter(File::isFile)
            .takeWhile { file ->
                scannedFiles++
                if (scannedFiles > MaxScannedFiles) {
                    diagnostics +=
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            category = TextureAtlasEditorDiagnosticCategory.FileSystem,
                            message = "Directory scan limit reached at $MaxScannedFiles files.",
                            source = normalizePath(rootDirectory.path),
                        )
                    logger.warn(TAG) { "Texture Atlas Editor scan limit reached root='${normalizePath(rootDirectory.path)}' limit=$MaxScannedFiles" }
                    false
                } else {
                    true
                }
            }.forEach { file ->
                when {
                    file.name.endsWith(".krmeta", ignoreCase = true) -> metadata += file
                    isAtlasFile(file) -> atlases += file
                    isFontFile(file) -> fonts += file
                    isTextureFile(file) -> textures += file
                }
            }
        return ScanResult(
            textures = textures.sortedBy { file -> file.name },
            atlases = atlases.sortedBy { file -> file.name },
            metadata = metadata.sortedBy { file -> file.name },
            fonts = fonts.sortedBy { file -> file.name },
            diagnostics = diagnostics,
        )
    }

    private fun describeAsset(
        file: File,
        kind: TextureAtlasEditorAssetKind,
    ): TextureAtlasEditorAssetDescriptor {
        val normalizedPath = normalizeAssetPath(file.path)
        val metadataFile = File(file.parentFile, "${file.name}.krmeta").takeIf(File::isFile)
        val registryDescriptor =
            assetRegistry.findByPath(normalizedPath.removePrefix(normalizePath(assetRegistry.baseDir().path)).trimStart('/'))
                ?: assetRegistry.findByPath(normalizedPath)
        return TextureAtlasEditorAssetDescriptor(
            id = TextureAssetId(normalizedPath),
            path = normalizedPath,
            displayName = file.name,
            kind = kind,
            extension = file.extension.lowercase(),
            sizeBytes = file.length(),
            modifiedAtMillis = file.lastModified(),
            metadataPath = metadataFile?.let { normalizePath(it.path) },
            textureInfo = if (kind == TextureAtlasEditorAssetKind.Texture) metadataService.read(file) else null,
            registryMetadata = registryDescriptor?.metadata ?: emptyMap(),
        )
    }

    private fun loadNinePatchDocuments(
        textures: List<File>,
        diagnostics: MutableList<TextureAtlasEditorDiagnostic>,
    ): Map<String, NinePatchDocument> {
        val reader = ninePatchPixelReader ?: return emptyMap()
        return textures
            .filter { file -> isNinePatchTexturePath(file.name) }
            .associate { file ->
                val normalizedPath = normalizeAssetPath(file.path)
                logger.info(TAG) { "Detected Nine-patch texture path='$normalizedPath'" }
                val document = ninePatchParser.parse(normalizedPath, reader)
                document.issues.forEach { issue ->
                    diagnostics +=
                        TextureAtlasEditorDiagnostic(
                            severity = issue.toDiagnosticSeverity(),
                            category = TextureAtlasEditorDiagnosticCategory.Texture,
                            message = issue.message,
                            source = normalizedPath,
                        )
                }
                if (document.readable) {
                    logger.info(TAG) {
                        "Parsed Nine-patch path='$normalizedPath' stretchX=${document.stretchX.size} stretchY=${document.stretchY.size} issues=${document.issues.size}"
                    }
                } else {
                    logger.warn(TAG) {
                        "Nine-patch parse failed path='$normalizedPath' issues=${document.issues.size}"
                    }
                }
                normalizedPath to document
            }
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

    private fun enrichAtlasDocument(
        atlasFile: File,
        atlas: TextureAtlasDocument,
        diagnostics: MutableList<TextureAtlasEditorDiagnostic>,
    ): TextureAtlasDocument {
        val pageRegions = atlas.regions.groupBy { region -> region.id.pageName }
        val enrichedPages =
            atlas.pages.map { page ->
                val pageFile = resolveAtlasPage(atlasFile, page.name)
                val pageMetadata = pageFile?.takeIf(File::isFile)?.let(metadataService::read)
                if (pageFile == null || !pageFile.isFile) {
                    diagnostics +=
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            category = TextureAtlasEditorDiagnosticCategory.Atlas,
                            message = "Atlas page texture '${page.name}' is missing.",
                            source = normalizePath(atlasFile.path),
                        )
                }
                if (pageRegions[page.name].isNullOrEmpty()) {
                    diagnostics +=
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            category = TextureAtlasEditorDiagnosticCategory.Atlas,
                            message = "Atlas page '${page.name}' has no regions.",
                            source = normalizePath(atlasFile.path),
                        )
                }
                validatePageRegions(atlasFile, page.name, pageRegions[page.name].orEmpty(), pageMetadata, diagnostics)
                page.copy(
                    details =
                        page.details +
                            mapOf(
                                "texturePath" to normalizePath(pageFile?.path ?: page.name),
                                "textureExists" to ((pageFile?.isFile == true).toString()),
                            ) +
                            buildMap {
                                pageMetadata?.width?.let { put("textureWidth", it.toString()) }
                                pageMetadata?.height?.let { put("textureHeight", it.toString()) }
                            },
                )
            }
        return atlas.copy(pages = enrichedPages)
    }

    private fun validatePageRegions(
        atlasFile: File,
        pageName: String,
        regions: List<TextureAtlasRegion>,
        pageMetadata: TextureAtlasEditorTextureInfo?,
        diagnostics: MutableList<TextureAtlasEditorDiagnostic>,
    ) {
        regions.forEach { region ->
            if (region.xy == null) {
                diagnostics +=
                    TextureAtlasEditorDiagnostic(
                        severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                        category = TextureAtlasEditorDiagnosticCategory.Atlas,
                        message = "Region '${region.id.regionName}' is missing xy.",
                        source = normalizePath(atlasFile.path),
                    )
            }
            if (region.size == null) {
                diagnostics +=
                    TextureAtlasEditorDiagnostic(
                        severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                        category = TextureAtlasEditorDiagnosticCategory.Atlas,
                        message = "Region '${region.id.regionName}' is missing size.",
                        source = normalizePath(atlasFile.path),
                    )
            }
            val width = pageMetadata?.width
            val height = pageMetadata?.height
            if (width != null && height != null && region.xy != null && region.size != null) {
                val right = region.xy.first + region.size.first
                val bottom = region.xy.second + region.size.second
                if (region.xy.first < 0 || region.xy.second < 0 || right > width || bottom > height) {
                    diagnostics +=
                        TextureAtlasEditorDiagnostic(
                            severity = TextureAtlasEditorDiagnosticSeverity.Warning,
                            category = TextureAtlasEditorDiagnosticCategory.Atlas,
                            message = "Region '${region.id.regionName}' is outside page bounds.",
                            source = normalizePath(atlasFile.path),
                        )
                    logger.warn(TAG) {
                        "Texture Atlas Editor detected out-of-bounds region region='${region.id.regionName}' page='$pageName' atlas='${normalizePath(atlasFile.path)}'"
                    }
                }
            }
        }
    }

    private fun loadFontDocuments(
        fonts: List<File>,
        diagnostics: MutableList<TextureAtlasEditorDiagnostic>,
    ): Map<String, BitmapFontDocument> =
        fonts.associate { file ->
            val normalizedPath = normalizeAssetPath(file.path)
            logger.info(TAG) { "Detected font file path='$normalizedPath'" }
            val document = bitmapFontParser.parse(file)
            document.diagnostics.forEach { fontDiag ->
                diagnostics +=
                    TextureAtlasEditorDiagnostic(
                        severity = fontDiag.severity.toEditorSeverity(),
                        category = TextureAtlasEditorDiagnosticCategory.Font,
                        message = fontDiag.message,
                        source = fontDiag.source ?: normalizedPath,
                    )
            }
            if (document.readable) {
                logger.info(TAG) {
                    "Parsed font path='$normalizedPath' glyphs=${document.glyphs.size} pages=${document.pages.size} kernings=${document.kernings.size}"
                }
            } else {
                logger.warn(TAG) { "Font parse failed path='$normalizedPath' diagnostics=${document.diagnostics.size}" }
            }
            normalizedPath to document
        }

    private data class ScanResult(
        val textures: List<File> = emptyList(),
        val atlases: List<File> = emptyList(),
        val metadata: List<File> = emptyList(),
        val fonts: List<File> = emptyList(),
        val diagnostics: List<TextureAtlasEditorDiagnostic> = emptyList(),
    )

    companion object {
        private const val TAG = "TextureAtlasEditorLoader"
        private const val MaxScannedFiles = 5_000

        private val IgnoredDirectoryNames = setOf(".git", ".gradle", "build", "out")

        fun isTextureFile(file: File): Boolean = isSupportedTextureFile(file)

        fun isAtlasFile(file: File): Boolean = file.isFile && file.extension.equals("atlas", ignoreCase = true)

        fun isFontFile(file: File): Boolean = file.isFile && file.extension.equals("fnt", ignoreCase = true)
    }
}

data class TextureAtlasEditorLoadResult(
    val project: TextureAtlasEditorProject,
    val diagnostics: List<TextureAtlasEditorDiagnostic> = emptyList(),
)

internal fun normalizePath(path: String): String = path.replace('\\', '/')

private fun normalizeAssetPath(path: String): String = normalizePath(path).removePrefix("./")

private fun NinePatchValidationIssue.toDiagnosticSeverity(): TextureAtlasEditorDiagnosticSeverity =
    when (severity) {
        NinePatchValidationSeverity.Warning -> TextureAtlasEditorDiagnosticSeverity.Warning
        NinePatchValidationSeverity.Error -> TextureAtlasEditorDiagnosticSeverity.Error
    }

private fun com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity.toEditorSeverity(): TextureAtlasEditorDiagnosticSeverity =
    when (this) {
        com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity.Info -> TextureAtlasEditorDiagnosticSeverity.Info
        com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity.Warning -> TextureAtlasEditorDiagnosticSeverity.Warning
        com.pashkd.krender.engine.tools.common.bitmapfont.model.BitmapFontDiagnosticSeverity.Error -> TextureAtlasEditorDiagnosticSeverity.Error
    }
