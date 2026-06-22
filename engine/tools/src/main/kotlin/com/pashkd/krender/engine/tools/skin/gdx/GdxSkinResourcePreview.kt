package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.skin.SkinProject
import com.pashkd.krender.engine.tools.skin.SkinResourceCategory
import com.pashkd.krender.engine.tools.skin.SkinResourceIndex
import com.pashkd.krender.engine.tools.skin.SkinResourceInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewInfo
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewState
import com.pashkd.krender.engine.tools.skin.SkinResourceVisualPreviewZoomMode
import java.io.File

class GdxSkinResourcePreview(
    private val logger: Logger,
) : Disposable {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont()
    private val previewMatrix = Matrix4()
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var loadedTexturePath: String? = null
    private var loadedTexture: Texture? = null
    private var currentInfo: SkinResourceVisualPreviewInfo = SkinResourceVisualPreviewInfo()
    private var currentRegion: ResolvedRegion? = null
    private var lastFailureKey: String? = null

    init {
        font.color = Color(0.95f, 0.96f, 0.98f, 1f)
    }

    fun update(
        project: SkinProject?,
        resourceIndex: SkinResourceIndex,
        selectedResource: SkinResourceInfo?,
        previewState: SkinResourceVisualPreviewState,
    ): SkinResourceVisualPreviewInfo {
        val resolved = resolveResourcePreview(project, resourceIndex, selectedResource, previewState)
        currentRegion = resolved.region
        currentInfo =
            when {
                resolved.texturePath == null -> {
                    unloadTexture()
                    SkinResourceVisualPreviewInfo(
                        statusMessage = resolved.message,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
                }

                ensureTextureLoaded(resolved.texturePath) -> {
                    val texture = loadedTexture
                    SkinResourceVisualPreviewInfo(
                        statusMessage = resolved.message,
                        resolvedTexturePath = resolved.texturePath,
                        textureWidth = texture?.width ?: 0,
                        textureHeight = texture?.height ?: 0,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
                }

                else ->
                    SkinResourceVisualPreviewInfo(
                        statusMessage = "Failed to load texture preview: ${File(resolved.texturePath).name}",
                        resolvedTexturePath = resolved.texturePath,
                        atlasPageName = resolved.atlasPageName,
                        selectedRegionName = resolved.region?.name,
                    )
            }
        return currentInfo
    }

    fun setCanvasViewport(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        viewportX = x.coerceAtLeast(0)
        viewportY = y.coerceAtLeast(0)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
    }

    fun clearCanvasViewport() {
        viewportWidth = 1
        viewportHeight = 1
    }

    fun resize(
        @Suppress("UNUSED_PARAMETER")
        width: Int,
        @Suppress("UNUSED_PARAMETER")
        height: Int,
    ) = Unit

    fun render(previewState: SkinResourceVisualPreviewState) {
        if (viewportWidth <= 1 || viewportHeight <= 1) return

        val graphicsWidth = Gdx.graphics.width.coerceAtLeast(1)
        val graphicsHeight = Gdx.graphics.height.coerceAtLeast(1)
        val safeViewportX = viewportX.coerceIn(0, graphicsWidth - 1)
        val safeViewportY = viewportY.coerceIn(0, graphicsHeight - 1)
        val safeViewportWidth = viewportWidth.coerceAtMost(graphicsWidth - safeViewportX).coerceAtLeast(1)
        val safeViewportHeight = viewportHeight.coerceAtMost(graphicsHeight - safeViewportY).coerceAtLeast(1)
        val glY = (graphicsHeight - safeViewportY - safeViewportHeight).coerceAtLeast(0)

        val previousViewport = BufferUtils.newIntBuffer(4)
        val previousScissor = BufferUtils.newIntBuffer(4)
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, previousViewport)
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, previousScissor)
        val scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)

        Gdx.gl.glViewport(safeViewportX, glY, safeViewportWidth, safeViewportHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(safeViewportX, glY, safeViewportWidth, safeViewportHeight)
        Gdx.gl.glClearColor(0.055f, 0.06f, 0.07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        previewMatrix.setToOrtho2D(0f, 0f, safeViewportWidth.toFloat(), safeViewportHeight.toFloat())
        val texture = loadedTexture
        if (texture != null) {
            val scale = computeScale(texture, safeViewportWidth, safeViewportHeight, previewState.zoomMode)
            val drawWidth = texture.width * scale
            val drawHeight = texture.height * scale
            val drawX = ((safeViewportWidth - drawWidth) * 0.5f).coerceAtLeast(0f)
            val drawY = ((safeViewportHeight - drawHeight) * 0.5f).coerceAtLeast(0f)

            batch.projectionMatrix = previewMatrix
            batch.begin()
            batch.draw(texture, drawX, drawY, drawWidth, drawHeight)
            batch.end()

            val region = currentRegion
            if (region != null && previewState.showRegionBounds) {
                val bounds = mapRegionBounds(region, texture, drawX, drawY, scale)
                shapes.projectionMatrix = previewMatrix
                shapes.begin(ShapeRenderer.ShapeType.Line)
                shapes.color = Color(0.15f, 0.85f, 0.55f, 1f)
                shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height)
                shapes.end()

                if (previewState.showRegionLabels) {
                    batch.projectionMatrix = previewMatrix
                    batch.begin()
                    font.draw(batch, region.name, bounds.x + 4f, bounds.y + bounds.height - 4f)
                    batch.end()
                }
            }
        }

        if (!scissorWasEnabled) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        } else {
            Gdx.gl.glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3])
        }
        Gdx.gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    override fun dispose() {
        unloadTexture()
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }

    private fun resolveResourcePreview(
        project: SkinProject?,
        resourceIndex: SkinResourceIndex,
        selectedResource: SkinResourceInfo?,
        previewState: SkinResourceVisualPreviewState,
    ): ResolvedPreview {
        if (selectedResource == null) {
            return ResolvedPreview(message = "Select a texture, atlas, or atlas region.")
        }
        return when (selectedResource.category) {
            SkinResourceCategory.Texture -> {
                val texturePath = selectedResource.source?.takeIf { File(it).exists() }
                if (texturePath != null) {
                    ResolvedPreview(texturePath = texturePath, message = "Showing texture '${File(texturePath).name}'.")
                } else {
                    ResolvedPreview(message = "Texture file could not be resolved.")
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
                            texturePath = pagePath,
                            atlasPageName = pageName,
                            region = region,
                            message = message,
                        )
                    } else {
                        ResolvedPreview(message = "Atlas page '$pageName' could not be resolved.")
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
                            texturePath = pagePath,
                            atlasPageName = pageName,
                            region = selectedResource.toResolvedRegion(),
                            message = "Showing atlas region '${selectedResource.name}' on '${File(pagePath).name}'.",
                        )
                    } else {
                        ResolvedPreview(message = "Atlas page '$pageName' could not be resolved for '${selectedResource.name}'.")
                    }
                }
            }

            else -> ResolvedPreview(message = "Visual preview is available for textures and atlas resources.")
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
                lastFailureKey = null
                true
            },
            onFailure = { error ->
                loadedTexturePath = null
                loadedTexture = null
                val failureKey = "$path:${error.message}"
                if (failureKey != lastFailureKey) {
                    logger.warn(TAG, error) { "Skin resource preview failed to load texture '$path': ${error.message}" }
                    lastFailureKey = failureKey
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

    private fun computeScale(
        texture: Texture,
        canvasWidth: Int,
        canvasHeight: Int,
        zoomMode: SkinResourceVisualPreviewZoomMode,
    ): Float =
        when (zoomMode) {
            SkinResourceVisualPreviewZoomMode.Fit ->
                minOf(
                    canvasWidth.toFloat() / texture.width.coerceAtLeast(1).toFloat(),
                    canvasHeight.toFloat() / texture.height.coerceAtLeast(1).toFloat(),
                ).coerceAtLeast(0.05f)

            SkinResourceVisualPreviewZoomMode.Percent50 -> 0.5f
            SkinResourceVisualPreviewZoomMode.Percent100 -> 1f
            SkinResourceVisualPreviewZoomMode.Percent200 -> 2f
        }

    private fun mapRegionBounds(
        region: ResolvedRegion,
        texture: Texture,
        drawX: Float,
        drawY: Float,
        scale: Float,
    ): PreviewBounds {
        val regionBottom = texture.height - region.y - region.height
        return PreviewBounds(
            x = drawX + region.x * scale,
            y = drawY + regionBottom * scale,
            width = region.width * scale,
            height = region.height * scale,
        )
    }

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
        val texturePath: String? = null,
        val atlasPageName: String? = null,
        val region: ResolvedRegion? = null,
        val message: String,
    )

    private data class ResolvedRegion(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private data class PreviewBounds(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )

    private companion object {
        private const val TAG = "GdxSkinResourcePreview"
    }
}
