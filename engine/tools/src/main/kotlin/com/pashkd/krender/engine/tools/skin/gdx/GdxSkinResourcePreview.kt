package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.tools.skin.DefaultFontPreviewSampleText
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinFontPreviewState
import com.pashkd.krender.engine.tools.skin.SkinColorValueParser
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.tools.skin.SkinProject
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceIndex
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewKind
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewState
import java.io.File

class GdxSkinResourcePreview(
    private val logger: Logger,
) : Disposable {
    private val batch = SpriteBatch()
    private val glyphLayout = GlyphLayout()
    private val previewMatrix = Matrix4()
    private var loadedTexturePath: String? = null
    private var loadedTexture: Texture? = null
    private var loadedPreviewFontPath: String? = null
    private var loadedPreviewFont: BitmapFont? = null
    private var previewFontOwned = false
    private var failedFontLoadKey: String? = null
    private var fontPreviewBuffer: FrameBuffer? = null
    private var fontPreviewRenderKey: String? = null
    private var currentInfo: SkinResourceVisualPreviewInfo = SkinResourceVisualPreviewInfo()
    private var lastFontLoadFailure: String? = null
    private val warnedFailureKeys = mutableSetOf<String>()

    fun update(
        project: SkinProject?,
        resourceIndex: SkinResourceIndex,
        selectedResource: SkinResourceInfo?,
        previewState: SkinResourceVisualPreviewState,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
    ): SkinResourceVisualPreviewInfo {
        val resolved = resolveResourcePreview(project, resourceIndex, selectedResource, previewState, loadedSkin, editSession)
        currentInfo =
            when {
                resolved.kind == SkinResourceVisualPreviewKind.Font -> updateFontPreviewInfo(resolved, previewState.fontPreview)
                resolved.kind == SkinResourceVisualPreviewKind.Color -> {
                    unloadTexture()
                    unloadPreviewFont()
                    SkinResourceVisualPreviewInfo(
                        statusMessage = resolved.message,
                        kind = resolved.kind,
                        colorValue = resolved.colorValue,
                    )
                }
                resolved.texturePath == null -> {
                    unloadTexture()
                    unloadPreviewFont()
                    SkinResourceVisualPreviewInfo(
                        statusMessage = resolved.message,
                        kind = resolved.kind,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
                }

                ensureTextureLoaded(resolved.texturePath) -> {
                    unloadPreviewFont()
                    val texture = loadedTexture
                    SkinResourceVisualPreviewInfo(
                        statusMessage = resolved.message,
                        kind = resolved.kind,
                        resolvedTexturePath = resolved.texturePath,
                        texturePreviewHandle =
                            texture?.let {
                                TexturePreviewHandle(
                                    id = it.textureObjectHandle,
                                    width = it.width,
                                    height = it.height,
                                )
                            },
                        textureWidth = texture?.width ?: 0,
                        textureHeight = texture?.height ?: 0,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
                }

                else ->
                    SkinResourceVisualPreviewInfo(
                        statusMessage = "Failed to load texture preview: ${File(resolved.texturePath).name}",
                        kind = resolved.kind,
                        resolvedTexturePath = resolved.texturePath,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
            }
        return currentInfo
    }

    override fun dispose() {
        unloadTexture()
        unloadPreviewFont()
        fontPreviewBuffer?.dispose()
        fontPreviewBuffer = null
        batch.dispose()
    }

    private fun resolveResourcePreview(
        project: SkinProject?,
        resourceIndex: SkinResourceIndex,
        selectedResource: SkinResourceInfo?,
        previewState: SkinResourceVisualPreviewState,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
    ): ResolvedPreview {
        if (selectedResource == null) {
            return ResolvedPreview(message = "Select a texture, atlas, atlas region, font, or color.")
        }
        return when (selectedResource.category) {
            SkinResourceCategory.Texture -> {
                val texturePath = selectedResource.source?.takeIf { File(it).exists() }
                if (texturePath != null) {
                    ResolvedPreview(
                        kind = SkinResourceVisualPreviewKind.Texture,
                        texturePath = texturePath,
                        message = "Showing texture '${File(texturePath).name}'.",
                    )
                } else {
                    ResolvedPreview(kind = SkinResourceVisualPreviewKind.Texture, message = "Texture file could not be resolved.")
                }
            }

            SkinResourceCategory.Atlas -> {
                val atlasFile = selectedResource.source?.let(::File)
                val pageName = selectedResource.details["pages"]?.split(',')?.firstOrNull()?.trim().orEmpty()
                if (atlasFile == null || pageName.isBlank()) {
                    ResolvedPreview(message = "Atlas page metadata is unavailable for visual preview.")
                } else {
                    val pagePath = resolveAtlasPagePath(project, atlasFile, pageName)
                    val region =
                        previewState.selectedAtlasRegionName
                            ?.let { selectedName ->
                                resourceIndex.atlasRegions.firstOrNull { region ->
                                    region.source == selectedResource.source && region.name == selectedName
                                }
                            }?.toResolvedRegion()
                    if (pagePath != null) {
                        val message =
                            if (selectedResource.details["pageCount"]?.toIntOrNull()?.let { it > 1 } == true) {
                                "Showing atlas page '${File(pagePath).name}' (first page)."
                            } else {
                                "Showing atlas page '${File(pagePath).name}'."
                            }
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Texture,
                            texturePath = pagePath,
                            atlasPageName = pageName,
                            region = region,
                            message = message,
                        )
                    } else {
                        ResolvedPreview(kind = SkinResourceVisualPreviewKind.Texture, message = "Atlas page '$pageName' could not be resolved.")
                    }
                }
            }

            SkinResourceCategory.AtlasRegion -> {
                val atlasFile = selectedResource.source?.let(::File)
                val pageName = selectedResource.details["page"]?.takeIf(String::isNotBlank)
                if (atlasFile == null || pageName == null) {
                    ResolvedPreview(message = "Atlas region page metadata is unavailable.")
                } else {
                    val pagePath = resolveAtlasPagePath(project, atlasFile, pageName)
                    if (pagePath != null) {
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Texture,
                            texturePath = pagePath,
                            atlasPageName = pageName,
                            region = selectedResource.toResolvedRegion(),
                            message = "Showing atlas region '${selectedResource.name}' on '${File(pagePath).name}'.",
                        )
                    } else {
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Texture,
                            message = "Atlas page '$pageName' could not be resolved for '${selectedResource.name}'.",
                        )
                    }
                }
            }

            SkinResourceCategory.Font -> {
                val matchedFontPath = selectedResource.details["matchedFile"]?.takeUnless { it == "<none>" }
                when {
                    loadedSkin?.skin?.has(selectedResource.name, BitmapFont::class.java) == true ->
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Font,
                            skinFontName = selectedResource.name,
                            fontPreviewSource = "Loaded skin resource",
                            loadedSkin = loadedSkin,
                            message = "Showing loaded skin font sample for '${selectedResource.name}'.",
                        )

                    matchedFontPath != null && selectedResource.details["matchedFileExtension"]?.equals("fnt", ignoreCase = true) == true ->
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Font,
                            fontPath = matchedFontPath,
                            projectRoot = project?.rootDirectory,
                            fontPreviewSource = "Matched .fnt file",
                            message = "Showing BMFont sample for '${selectedResource.name}'.",
                        )

                    matchedFontPath != null ->
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Font,
                            fontPath = matchedFontPath,
                            fontPreviewSource = "Indexed font file",
                            message = "Font metadata is available, but .${
                                selectedResource.details["matchedFileExtension"] ?: "unknown"
                            } preview is deferred in this step.",
                        )

                    else ->
                        ResolvedPreview(
                            kind = SkinResourceVisualPreviewKind.Font,
                            message = "No matched .fnt file or loaded skin font was available for preview.",
                        )
                }
            }

            SkinResourceCategory.Color -> {
                val editable = editSession.resources[selectedResource.key]
                val values = editable?.values ?: selectedResource.details
                val parsed = parseColor(values)
                if (parsed != null) {
                    ResolvedPreview(
                        kind = SkinResourceVisualPreviewKind.Color,
                        color = parsed.first,
                        colorValue = parsed.second,
                        message = "Showing color '${selectedResource.name}' from ${if (editable?.modifiedFields?.isNotEmpty() == true) "in-memory edits" else "loaded skin metadata"}.",
                    )
                } else {
                    ResolvedPreview(
                        kind = SkinResourceVisualPreviewKind.Color,
                        message = "Color '${selectedResource.name}' has an invalid or unsupported value.",
                    )
                }
            }

            else -> ResolvedPreview(message = "Visual preview is available for textures, atlas resources, fonts, and colors.")
        }
    }

    private fun ensureTextureLoaded(path: String): Boolean {
        if (loadedTexturePath == path && loadedTexture != null) {
            return true
        }
        unloadTexture()
        return runCatching {
            Texture(Gdx.files.absolute(path))
        }.fold(
            onSuccess = { texture ->
                loadedTexture = texture
                loadedTexturePath = path
                true
            },
            onFailure = { error ->
                loadedTexturePath = null
                loadedTexture = null
                val failureKey = "$path:${error.message}"
                if (warnedFailureKeys.add(failureKey)) {
                    logger.warn(TAG, error) { "Skin resource preview failed to load texture '$path': ${error.message}" }
                }
                false
            },
        )
    }

    private fun unloadTexture() {
        loadedTexture?.dispose()
        loadedTexture = null
        loadedTexturePath = null
    }

    private fun updateFontPreviewInfo(
        resolved: ResolvedPreview,
        fontPreview: SkinFontPreviewState,
    ): SkinResourceVisualPreviewInfo {
        unloadTexture()
        val fontReady =
            when {
                resolved.fontPath != null -> ensurePreviewFontLoaded(resolved.fontPath, resolved.projectRoot)
                resolved.skinFontName != null -> ensurePreviewFontFromSkin(resolved.skinFontName, resolved.loadedSkin)
                else -> false
            }
        val previewHandle =
            if (fontReady && renderFontPreviewTexture(fontPreview)) {
                fontPreviewBuffer?.colorBufferTexture?.let { texture ->
                    TexturePreviewHandle(
                        id = texture.textureObjectHandle,
                        width = texture.width,
                        height = texture.height,
                        v0 = 1f,
                        v1 = 0f,
                    )
                }
            } else {
                null
            }
        return SkinResourceVisualPreviewInfo(
            statusMessage =
                if (fontReady) {
                    resolved.message
                } else {
                    "Font preview unavailable: ${lastFontLoadFailure ?: resolved.message}"
                },
            kind = SkinResourceVisualPreviewKind.Font,
            texturePreviewHandle = previewHandle,
            resolvedFontPath = resolved.fontPath,
            fontPreviewSource = resolved.fontPreviewSource ?: defaultFontPreviewSource(fontReady, resolved, fontPreview),
        )
    }

    private fun ensurePreviewFontLoaded(
        path: String,
        projectRoot: File?,
    ): Boolean {
        val loadKey = "${File(path).absolutePath}:${projectRoot?.absolutePath}"
        if (loadedPreviewFontPath == path && loadedPreviewFont != null) {
            return true
        }
        if (failedFontLoadKey == loadKey) {
            return false
        }
        unloadPreviewFont()
        val normalizedPath = File(path).absolutePath.replace('\\', '/')
        return runCatching {
            GdxBitmapFontPreviewLoader.load(File(normalizedPath), projectRoot)
        }.fold(
            onSuccess = { font ->
                loadedPreviewFont = font
                loadedPreviewFontPath = path
                previewFontOwned = true
                lastFontLoadFailure = null
                failedFontLoadKey = null
                true
            },
            onFailure = { error ->
                val failureKey = "font:$path:${error.message}"
                lastFontLoadFailure = error.message ?: "Could not load BMFont pages."
                failedFontLoadKey = loadKey
                if (warnedFailureKeys.add(failureKey)) {
                    logger.warn(TAG, error) { "Skin resource preview failed to load font '$path': ${error.message}" }
                }
                false
            },
        )
    }

    private fun ensurePreviewFontFromSkin(
        fontName: String,
        loadedSkin: LoadedSkinHandle?,
    ): Boolean {
        if (loadedPreviewFontPath == "skin:$fontName" && loadedPreviewFont != null) {
            return true
        }
        unloadPreviewFont()
        val skin = loadedSkin?.skin ?: return false
        return runCatching { skin.getFont(fontName) }.fold(
            onSuccess = { bitmapFont ->
                loadedPreviewFont = bitmapFont
                loadedPreviewFontPath = "skin:$fontName"
                previewFontOwned = false
                lastFontLoadFailure = null
                true
            },
            onFailure = { error ->
                val failureKey = "skin-font:$fontName:${error.message}"
                lastFontLoadFailure = error.message ?: "Could not resolve skin font."
                if (warnedFailureKeys.add(failureKey)) {
                    logger.warn(TAG, error) { "Skin resource preview failed to resolve skin font '$fontName': ${error.message}" }
                }
                false
            },
        )
    }

    private fun unloadPreviewFont() {
        if (previewFontOwned) {
            loadedPreviewFont?.dispose()
        }
        loadedPreviewFont = null
        loadedPreviewFontPath = null
        previewFontOwned = false
        fontPreviewRenderKey = null
    }

    private fun renderFontPreviewTexture(fontPreview: SkinFontPreviewState): Boolean {
        val previewFont = loadedPreviewFont ?: return false
        val sampleText = buildFontSample(fontPreview)
        val renderKey =
            "${loadedPreviewFontPath}:${fontPreview.fontScale}:${fontPreview.showCyrillicSample}:" +
                "${fontPreview.showAsciiSample}:$sampleText"
        if (fontPreviewRenderKey == renderKey && fontPreviewBuffer != null) return true

        val buffer =
            fontPreviewBuffer
                ?: FrameBuffer(Pixmap.Format.RGBA8888, FontPreviewTextureWidth, FontPreviewTextureHeight, false)
                    .also { fontPreviewBuffer = it }
        val oldScaleX = previewFont.data.scaleX
        val oldScaleY = previewFont.data.scaleY
        val oldColor = Color(previewFont.color)
        val previousViewport = BufferUtils.newIntBuffer(4)
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, previousViewport)
        var beganBuffer = false
        return runCatching {
            buffer.begin()
            beganBuffer = true
            Gdx.gl.glClearColor(0.055f, 0.06f, 0.07f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            previewMatrix.setToOrtho2D(0f, 0f, FontPreviewTextureWidth.toFloat(), FontPreviewTextureHeight.toFloat())
            previewFont.data.setScale(fontPreview.fontScale)
            previewFont.color = Color.WHITE
            glyphLayout.setText(
                previewFont,
                sampleText,
                Color.WHITE,
                FontPreviewTextureWidth - FontPreviewPadding * 2f,
                1,
                true,
            )
            batch.projectionMatrix = previewMatrix
            batch.begin()
            previewFont.draw(batch, glyphLayout, FontPreviewPadding, FontPreviewTextureHeight - FontPreviewPadding)
            batch.end()
            buffer.end()
            beganBuffer = false
            fontPreviewRenderKey = renderKey
            true
        }.getOrElse {
            if (beganBuffer) buffer.end()
            false
        }.also {
            previewFont.data.setScale(oldScaleX, oldScaleY)
            previewFont.color = oldColor
            Gdx.gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        }
    }

    private fun parseColor(values: Map<String, String>): Pair<Color, String>? {
        val color = SkinColorValueParser.parse(values) ?: return null
        return Color(color.r, color.g, color.b, color.a) to color.displayValue
    }

    private fun buildFontSample(fontPreview: SkinFontPreviewState): String {
        val lines = fontPreview.sampleText.ifBlank { DefaultFontPreviewSampleText }.lineSequence().toMutableList()
        return lines
            .filterNot { line -> !fontPreview.showCyrillicSample && line.any(::isCyrillicCharacter) }
            .filterNot { line -> !fontPreview.showAsciiSample && line.any(Char::isLetterOrDigit) && line.none(::isCyrillicCharacter) }
            .joinToString("\n")
            .ifBlank { "Font preview sample is empty." }
    }

    private fun defaultFontPreviewSource(
        fontReady: Boolean,
        resolved: ResolvedPreview,
        fontPreview: SkinFontPreviewState,
    ): String? {
        if (!fontReady) return null
        return resolved.fontPreviewSource ?: "Font preview"
    }

    private fun isCyrillicCharacter(character: Char): Boolean = character.code in 0x0400..0x04FF

    private fun resolveAtlasPagePath(
        project: SkinProject?,
        atlasFile: File,
        pageName: String,
    ): String? {
        val atlasParent = atlasFile.parentFile ?: atlasFile.absoluteFile.parentFile
        val candidates =
            buildList {
                val pageFile = File(pageName)
                if (pageFile.isAbsolute) add(pageFile)
                if (atlasParent != null) {
                    add(File(atlasParent, pageName))
                }
                project?.rootDirectory?.let { root -> add(File(root, pageName)) }
            }
        return candidates.firstOrNull(File::exists)?.path
    }

    private fun SkinResourceInfo.toResolvedRegion(): ResolvedRegion? {
        val xy = details["xy"]?.parseIntPair() ?: return null
        val size = details["size"]?.parseIntPair() ?: return null
        return ResolvedRegion(
            name = name,
            x = xy.first,
            y = xy.second,
            width = size.first,
            height = size.second,
        )
    }

    private fun String.parseIntPair(): Pair<Int, Int>? {
        val parts = split(',').map(String::trim)
        if (parts.size < 2) return null
        val first = parts[0].toIntOrNull() ?: return null
        val second = parts[1].toIntOrNull() ?: return null
        return first to second
    }

    private data class ResolvedPreview(
        val kind: SkinResourceVisualPreviewKind = SkinResourceVisualPreviewKind.None,
        val texturePath: String? = null,
        val atlasPageName: String? = null,
        val region: ResolvedRegion? = null,
        val fontPath: String? = null,
        val skinFontName: String? = null,
        val fontPreviewSource: String? = null,
        val loadedSkin: LoadedSkinHandle? = null,
        val projectRoot: File? = null,
        val color: Color? = null,
        val colorValue: String? = null,
        val message: String,
    )

    private data class ResolvedRegion(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private companion object {
        private const val TAG = "GdxSkinResourcePreview"
        private const val FontPreviewTextureWidth = 640
        private const val FontPreviewTextureHeight = 260
        private const val FontPreviewPadding = 16f
    }
}
