@file:Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.DebugCullingMode
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.MaterialDebugTextureRef
import com.pashkd.krender.engine.api.MaterialDebugView
import com.pashkd.krender.engine.api.TextureDebugComponent

/**
 * Shader-based ModelViewer material debug renderer.
 */
internal class GdxModelViewerDebugRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val uvShaders = mutableMapOf<Int, ShaderProgram?>()
    private val fallbackShader: ShaderProgram? =
        compileShader(
            name = "model-viewer-debug-fallback",
            vertex = GdxShaderSources.read("shaders/model_viewer_debug_fallback.vert"),
            fragment = GdxShaderSources.read("shaders/model_viewer_debug_fallback.frag"),
        )
    private val renderables = Array<Renderable>()
    private val renderablePool =
        object : Pool<Renderable>() {
            override fun newObject(): Renderable = Renderable()
        }
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: DrawModel,
        camera: Camera,
        instanceProvider: (DrawModel, Camera) -> ModelInstance?,
    ) {
        val debugView = command.debugView ?: return
        val instance = instanceProvider(command, camera) ?: return

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        when (debugView.culling) {
            DebugCullingMode.Backface -> {
                Gdx.gl.glEnable(GL20.GL_CULL_FACE)
                Gdx.gl.glCullFace(GL20.GL_BACK)
            }

            DebugCullingMode.DoubleSided -> {
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            }
        }

        renderables.clear()
        instance.getRenderables(renderables, renderablePool)
        for (renderable in renderables) {
            renderRenderable(renderable, instance, debugView, camera)
        }
        renderablePool.freeAll(renderables)
        renderables.clear()
    }

    fun dispose() {
        uvShaders.values
            .filterNotNull()
            .toSet()
            .forEach(ShaderProgram::dispose)
        fallbackShader?.dispose()
    }

    private fun renderRenderable(
        renderable: Renderable,
        instance: ModelInstance,
        debugView: MaterialDebugView,
        camera: Camera,
    ) {
        val instanceMaterialIndex = materialIndexOf(instance.materials, renderable.material)
        val selectedMaterialIndex = debugView.selectedMaterialIndex
        val mode = debugView.mode
        val textureRef = textureRefFor(renderable.material, instanceMaterialIndex, debugView)
        val materialIndex = textureRef?.materialIndex ?: instanceMaterialIndex
        if (selectedMaterialIndex != null && !matchesSelectedMaterial(renderable.material, materialIndex, debugView)) {
            renderFallback(renderable, camera, FallbackUnselectedMaterial)
            return
        }

        val uvChannel =
            if (mode == MaterialDebugMode.UvChecker) {
                debugView.uvChannel.coerceAtLeast(0)
            } else {
                textureRef?.texture?.uvChannel?.coerceAtLeast(0) ?: 0
            }
        if (!renderable.meshPart.hasUvChannel(uvChannel)) {
            warnOnce("missing-uv-$mode-$uvChannel") {
                "ModelViewer debug shader fallback: mesh part '${renderable.meshPart.id}' has no UV$uvChannel for mode=$mode"
            }
            renderFallback(renderable, camera, FallbackMissingUv)
            return
        }

        val texture =
            if (mode == MaterialDebugMode.UvChecker) {
                val checkerRef = debugView.uvCheckerTexture
                checkerRef?.let { ref ->
                    assets.queue(AssetRef.texture(ref.id))
                    assets.gdxTexture(AssetRef.texture(ref.id))
                }
            } else {
                textureRef?.texture?.let { ref -> assets.textureByPathOrId(ref.id) }
            }

        if (texture == null) {
            warnOnce("missing-texture-$mode-${materialIndex ?: "unknown"}") {
                "ModelViewer debug shader fallback: texture for mode=$mode is unavailable materialIndex=${materialIndex ?: "unknown"}"
            }
            renderFallback(renderable, camera, FallbackMissingTexture)
            return
        }

        val shader =
            uvShader(uvChannel) ?: run {
                renderFallback(renderable, camera, FallbackShaderError)
                return
            }

        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        texture.bind(0)
        shader.bind()
        // Scalar debug channels are sampled raw until texture color-space metadata is carried through asset loading.
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        shader.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        shader.setUniformi("u_DebugTexture", 0)
        shader.setUniformi("u_DebugMode", debugModeCode(mode))
        shader.setUniformi(
            "u_DebugComponent",
            textureDebugComponentCode(
                if (mode == MaterialDebugMode.UvChecker) {
                    TextureDebugComponent.RGB
                } else {
                    textureRef?.component ?: TextureDebugComponent.RGB
                },
            ),
        )
        shader.setUniformf("u_UvCheckerScale", debugView.uvScale.coerceAtLeast(MIN_UV_SCALE))
        renderable.meshPart.render(shader)
    }

    private fun renderFallback(
        renderable: Renderable,
        camera: Camera,
        color: Color,
    ) {
        val shader = fallbackShader ?: return
        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        shader.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        shader.setUniformf("u_FallbackColor", color)
        renderable.meshPart.render(shader)
    }

    private fun textureRefFor(
        material: Material,
        materialIndex: Int?,
        debugView: MaterialDebugView,
    ): MaterialDebugTextureRef? =
        debugView.textureRefs.firstOrNull { ref ->
            ref.materialId != null && material.id != null && ref.materialId == material.id
        }
            ?: debugView.textureRefs.firstOrNull { ref -> materialIndex != null && ref.materialIndex == materialIndex }
            ?: debugView.textureRefs.firstOrNull { ref -> ref.materialIndex == null && ref.materialId == null }

    private fun matchesSelectedMaterial(
        material: Material,
        materialIndex: Int?,
        debugView: MaterialDebugView,
    ): Boolean {
        val selectedMaterialId = debugView.selectedMaterialId
        if (selectedMaterialId != null && material.id != null) {
            return material.id == selectedMaterialId
        }
        val selectedMaterialIndex = debugView.selectedMaterialIndex ?: return true
        return materialIndex == selectedMaterialIndex
    }

    private fun uvShader(uvChannel: Int): ShaderProgram? =
        uvShaders.getOrPut(uvChannel) {
            compileShader(
                name = "model-viewer-debug-uv$uvChannel",
                vertex =
                    GdxShaderSources.readTemplate(
                        "shaders/model_viewer_debug_uv.vert",
                        mapOf("UV_CHANNEL" to uvChannel.toString()),
                    ),
                fragment =
                    GdxShaderSources.readTemplate(
                        "shaders/model_viewer_debug_uv.frag",
                        mapOf(
                            "MODE_UV_CHECKER" to debugModeCode(MaterialDebugMode.UvChecker).toString(),
                            "COMPONENT_R" to textureDebugComponentCode(TextureDebugComponent.R).toString(),
                            "COMPONENT_G" to textureDebugComponentCode(TextureDebugComponent.G).toString(),
                            "COMPONENT_B" to textureDebugComponentCode(TextureDebugComponent.B).toString(),
                            "COMPONENT_A" to textureDebugComponentCode(TextureDebugComponent.A).toString(),
                            "COMPONENT_RGBA" to textureDebugComponentCode(TextureDebugComponent.RGBA).toString(),
                        ),
                    ),
            )
        }

    private fun compileShader(
        name: String,
        vertex: String,
        fragment: String,
    ): ShaderProgram? {
        val shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            logger.error(TAG) { "ModelViewer debug shader compile failed name='$name': ${shader.log}" }
            shader.dispose()
            return null
        }
        logger.info(TAG) { "ModelViewer debug shader compiled name='$name'" }
        return shader
    }

    private fun warnOnce(
        key: String,
        message: () -> String,
    ) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    private fun MeshPart.hasUvChannel(uvChannel: Int): Boolean =
        mesh.vertexAttributes.any { attribute ->
            attribute.usage == VertexAttributes.Usage.TextureCoordinates && attribute.unit == uvChannel
        }

    companion object {
        private const val TAG = "GdxModelViewerDebugRenderer"
        private const val MIN_UV_SCALE = 0.01f
        private val FallbackMissingTexture = Color(1f, 0.15f, 0.65f, 1f)
        private val FallbackMissingUv = Color(1f, 0.72f, 0.1f, 1f)
        private val FallbackShaderError = Color(1f, 0f, 0f, 1f)
        private val FallbackUnselectedMaterial = Color(0.28f, 0.28f, 0.3f, 1f)
    }
}

private fun debugModeCode(mode: MaterialDebugMode): Int =
    when (mode) {
        MaterialDebugMode.Alpha -> 6
        MaterialDebugMode.UvChecker -> 7
        else -> 1
    }

private fun textureDebugComponentCode(component: TextureDebugComponent): Int =
    when (component) {
        TextureDebugComponent.RGB -> 0
        TextureDebugComponent.R -> 1
        TextureDebugComponent.G -> 2
        TextureDebugComponent.B -> 3
        TextureDebugComponent.A -> 4
        TextureDebugComponent.RGBA -> 5
    }
