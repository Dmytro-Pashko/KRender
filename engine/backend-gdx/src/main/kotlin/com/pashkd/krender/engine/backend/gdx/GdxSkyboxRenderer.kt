@file:Suppress("TooGenericExceptionCaught")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.pashkd.krender.engine.api.ApplyEnvironment
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Logger
import net.mgsx.gltf.scene3d.scene.SceneSkybox

/**
 * Shared runtime skybox renderer for backend-neutral environment commands.
 */
internal class GdxSkyboxRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<String, RuntimeSkyboxEntry>()
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: ApplyEnvironment,
        camera: Camera,
        modelBatch: ModelBatch,
    ) {
        val texturePath =
            command.skyboxTexture
                ?.id
                ?.takeIf { command.showSkybox && it.isNotBlank() }
                ?: return
        val entry = entryFor(texturePath) ?: return
        val intensity = command.environmentIntensity.coerceAtLeast(0f)
        entry.skybox.color.set(intensity, intensity, intensity, 1f)
        entry.skybox.update(camera, Gdx.graphics.deltaTime)

        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        try {
            modelBatch.begin(camera)
            try {
                modelBatch.render(entry.skybox)
            } finally {
                modelBatch.end()
            }
        } catch (error: Throwable) {
            warnOnce("render-$texturePath-${error::class.qualifiedName}") {
                "Runtime skybox render failed texture='$texturePath': ${error.message ?: error::class.simpleName}"
            }
        } finally {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        }
    }

    fun dispose() {
        entries.values.forEach(RuntimeSkyboxEntry::dispose)
        entries.clear()
        warnedKeys.clear()
    }

    private fun entryFor(path: String): RuntimeSkyboxEntry? {
        entries[path]?.let { return it }
        assets.queue(AssetRef.texture(path))
        return try {
            val cubemap = cubemapFromSingleTexture(path)
            try {
                SceneSkybox.enableMipmaps(cubemap)
            } catch (error: Throwable) {
                warnOnce("mipmaps-$path-${error::class.qualifiedName}") {
                    "Runtime skybox mipmaps unavailable texture='$path': ${error.message ?: error::class.simpleName}"
                }
            }
            RuntimeSkyboxEntry(
                cubemap = cubemap,
                skybox = SceneSkybox(cubemap),
            ).also { entry ->
                entries[path] = entry
                logger.info(TAG) { "Runtime skybox loaded texture='$path'" }
            }
        } catch (error: Throwable) {
            warnOnce("texture-$path-${error::class.qualifiedName}") {
                "Runtime skybox texture '$path' unavailable; continuing without skybox: ${error.message ?: error::class.simpleName}"
            }
            null
        }
    }

    private fun warnOnce(
        key: String,
        message: () -> String,
    ) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    private companion object {
        private const val TAG = "GdxSkyboxRenderer"
    }
}

private data class RuntimeSkyboxEntry(
    val cubemap: Cubemap,
    val skybox: SceneSkybox,
) {
    fun dispose() {
        skybox.dispose()
        cubemap.dispose()
    }
}
