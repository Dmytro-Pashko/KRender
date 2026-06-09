package com.pashkd.krender.engine.backend.gdx.ui.composer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.uicomposer.UiComposerSkinMetadata

/**
 * Reads LibGDX `Skin` names into immutable editor metadata for UiComposer pickers.
 *
 * This class belongs to backend Skin inspection for editor style picking. It
 * exists so UiComposer can inspect one path-based Skin file, cache the loaded
 * Skin, and expose sorted style/drawable names to backend-neutral editor state.
 * It intentionally does not edit Skin JSON, create styles, mutate assets,
 * provide a Skin composer, resolve asset ids, or change runtime UI behavior.
 */
class GdxUiComposerSkinMetadataReader(
    private val logger: Logger,
) : Disposable {
    private val skinCache = mutableMapOf<String, Skin>()
    private val metadataCache = mutableMapOf<String, UiComposerSkinMetadata>()

    /**
     * Loads [skinPath] through LibGDX and returns a read-only snapshot of known style names.
     *
     * This function belongs to backend Skin inspection for editor style picking.
     * It reads Label, TextButton, ProgressBar, and Drawable entry names only. It
     * intentionally does not edit the Skin, validate non-picker asset types,
     * migrate files, or expose mutable LibGDX Skin instances outside this
     * backend helper. Unavailable or broken Skin paths return a snapshot with
     * [UiComposerSkinMetadata.loadError] filled instead of crashing UiComposer.
     */
    fun read(skinPath: String): UiComposerSkinMetadata =
        metadataCache[skinPath] ?: loadMetadata(skinPath)

    /**
     * Disposes cached LibGDX Skin instances retained for repeated editor inspection.
     *
     * This belongs to backend inspection lifetime management. It intentionally
     * does not dispose editor state, mutate `.krui` documents, or touch Skin
     * source files on disk.
     */
    override fun dispose() {
        skinCache.values.forEach(Skin::dispose)
        skinCache.clear()
        metadataCache.clear()
    }

    private fun loadMetadata(skinPath: String): UiComposerSkinMetadata =
        try {
            val skin = skinCache.getOrPut(skinPath) { Skin(Gdx.files.internal(skinPath)) }
            val drawableNames = skin.namesFor(Drawable::class.java)
            UiComposerSkinMetadata(
                skinPath = skinPath,
                labelStyles = skin.namesFor(Label.LabelStyle::class.java),
                textButtonStyles = skin.namesFor(TextButton.TextButtonStyle::class.java),
                progressBarStyles = skin.namesFor(ProgressBar.ProgressBarStyle::class.java),
                drawables = drawableNames,
                drawablePreviewHandles = drawableNames.mapNotNull { drawableName ->
                    skin.previewHandleForRegion(drawableName)?.let { handle -> drawableName to handle }
                }.toMap(),
            ).also { metadata ->
                metadataCache[skinPath] = metadata
                logger.debug(TAG) {
                    "Loaded UiComposer Skin metadata path='$skinPath' labelStyles=${metadata.labelStyles.size} " +
                        "textButtonStyles=${metadata.textButtonStyles.size} " +
                        "progressBarStyles=${metadata.progressBarStyles.size} drawables=${metadata.drawables.size} " +
                        "drawablePreviewHandles=${metadata.drawablePreviewHandles.size}"
                }
                logger.debug(TAG) {
                    "UiComposer Skin metadata names path='$skinPath' " +
                        "labelStyles=${metadata.labelStyles.joinToString(",")} " +
                        "textButtonStyles=${metadata.textButtonStyles.joinToString(",")} " +
                        "progressBarStyles=${metadata.progressBarStyles.joinToString(",")} " +
                        "drawables=${metadata.drawables.joinToString(",")}"
                }
            }
        } catch (error: Exception) {
            logger.warn(TAG, error) {
                "Failed to inspect UiComposer Skin path='$skinPath': ${error.message}"
            }
            UiComposerSkinMetadata(
                skinPath = skinPath,
                loadError = error.message ?: error::class.simpleName ?: "Unknown Skin load error.",
            )
        }

    private fun <T> Skin.namesFor(type: Class<T>): List<String> {
        val names = mutableListOf<String>()
        // LibGDX Skin stores entries per type, so collect the current type slice only.
        for (name in getAll(type).keys()) {
            names += name
        }
        return names.sorted()
    }

    private fun Skin.previewHandleForRegion(name: String): TexturePreviewHandle? =
        try {
            getRegion(name).toPreviewHandle()
        } catch (_: Exception) {
            null
        }

    private fun TextureRegion.toPreviewHandle(): TexturePreviewHandle =
        TexturePreviewHandle(
            id = texture.textureObjectHandle,
            width = regionWidth,
            height = regionHeight,
            u0 = u,
            v0 = v,
            u1 = u2,
            v1 = v2,
        )

    companion object {
        private const val TAG = "GdxUiComposerSkinMetadataReader"
    }
}
