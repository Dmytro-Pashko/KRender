package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.model.Animation
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Pool
import com.pashkd.krender.engine.api.AnimationPlaybackView
import com.pashkd.krender.engine.api.ApplyEnvironment
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.RenderContext
import com.pashkd.krender.engine.api.Renderer
import com.pashkd.krender.engine.api.RuntimeTextureFilter
import com.pashkd.krender.engine.api.RuntimeTextureWrap
import com.pashkd.krender.engine.api.TextureDebugComponent
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.DefaultAmbientLightIntensity
import com.pashkd.krender.engine.scene.defaultAmbientLightColor
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneSkybox
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider
import net.mgsx.gltf.scene3d.utils.IBLBuilder
import net.mgsx.gltf.scene3d.utils.MaterialConverter
import java.lang.Math
import java.lang.System
import kotlin.math.cos
import kotlin.math.sin
import net.mgsx.gltf.scene3d.scene.Scene as GltfScene
import net.mgsx.gltf.scene3d.scene.SceneManager as GltfSceneManager

/**
 * LibGDX renderer that draws static models, dynamic meshes, wireframes, and lines.
 */
class GdxRenderer3D(
    private val assets: GdxAssetService,
    private val logger: Logger,
) : Renderer {
    private val maxShaderBones =
        systemInt("krender.gl.maxBones", default = DefaultMaxShaderBones).coerceAtLeast(MinShaderBones)
    private val modelBatch =
        ModelBatch(
            DefaultShaderProvider(
                DefaultShader.Config().apply {
                    numBones = maxShaderBones
                },
            ),
        )
    private val lineRenderer = GdxLineShaderRenderer()
    private val modelViewerDebugRenderer = GdxModelViewerDebugRenderer(assets, logger)
    private val pbrPreviewRenderer = GdxGltfPbrPreviewRenderer(assets, logger)
    private val skyboxRenderer = GdxSkyboxRenderer(assets, logger)

    // Per-entity render caches used only by the renderer. These stay separate from
    // GdxAssetService pose-sampling caches so animation preview and skeleton sampling do
    // not leak state across different entities or into asset-level debug sampling.
    private val instances = mutableMapOf<ModelCacheKey, ModelInstance>()
    private val animationControllers = mutableMapOf<ModelCacheKey, AnimationController>()
    private val gltfScenes = mutableMapOf<ModelCacheKey, GltfScene>()
    private val primitives = mutableMapOf<String, Model>()
    private val dynamicModels = mutableMapOf<String, DynamicModelCacheEntry>()
    private val wireframeRenderables = Array<Renderable>()
    private val wireframeRenderablePool =
        object : Pool<Renderable>() {
            override fun newObject(): Renderable = Renderable()
        }
    private val wireframeTmpVertex = Vector3()
    private val forceBackBufferAlphaOpaque = systemBoolean("krender.gl.forceOpaqueAlpha", default = false)
    private val warnedGltfRenderKeys = mutableSetOf<String>()

    private var width: Int = Gdx.graphics.width
    private var height: Int = Gdx.graphics.height

    init {
        logger.info(TAG) { "Configured LibGDX default shader maxBones=$maxShaderBones" }
    }

    /** Renders the full frame for the provided render context. */
    override fun render(context: RenderContext) {
        prepareSceneFrame()
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val camera = cameraFor(context)
        val environment = environmentFor(context)
        context.commands.filterIsInstance<ApplyEnvironment>().firstOrNull()?.let { command ->
            skyboxRenderer.render(command, camera, modelBatch)
        }
        val wireframeCommands = mutableListOf<DrawModel>()
        val wireframeDynamicCommands = mutableListOf<DrawDynamicModel>()
        val debugModelCommands = mutableListOf<DrawModel>()
        val pbrModelCommands = mutableListOf<DrawModel>()

        lineRenderer.render(context.commands, camera)

        modelBatch.begin(camera)
        context.commands.forEach { command ->
            when (command) {
                is DrawModel -> {
                    if (command.material.wireframe) {
                        wireframeCommands += command
                    } else if (command.debugView?.active == true) {
                        debugModelCommands += command
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    } else if (command.pbrPreview?.enabled == true) {
                        pbrModelCommands += command
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    } else {
                        renderModel(command, environment, camera)
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    }
                }

                is DrawDynamicModel -> {
                    if (command.material.wireframe) {
                        wireframeDynamicCommands += command
                    } else {
                        renderDynamicModel(command, environment)
                        if (command.material.wireframeOverlay) {
                            wireframeDynamicCommands += command
                        }
                    }
                }

                else -> Unit
            }
        }
        modelBatch.end()
        debugModelCommands.forEach { modelViewerDebugRenderer.render(it, camera, ::modelInstanceForDebug) }
        pbrModelCommands.forEach { command ->
            if (!pbrPreviewRenderer.render(command, camera, ::applyVisibleMeshPartFilter)) {
                modelBatch.begin(camera)
                renderModel(command, environment, camera)
                modelBatch.end()
            }
        }
        wireframeCommands.forEach { renderWireframeModel(it, camera) }
        wireframeDynamicCommands.forEach { renderWireframeDynamicModel(it, camera) }
        lineRenderer.renderOverlayLines(context.commands, camera)
        if (forceBackBufferAlphaOpaque) {
            forceOpaqueBackBufferAlpha()
        }
    }

    /**
     * Starts the 3D pass from a known GL state.
     *
     * ImGui uses scissor rectangles and blend state internally. If those flags
     * leak into the following frame, the backbuffer clear or scene pass can be
     * clipped, which shows up as intermittent full-UI blinking.
     */
    private fun prepareSceneFrame() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glColorMask(true, true, true, true)
    }

    /**
     * Keeps the presented window fully opaque after the scene pass.
     *
     * Overlay backends repeat the same alpha-only clear after their own draws so
     * DWM/capture paths never see partially transparent content in the final frame.
     */
    private fun forceOpaqueBackBufferAlpha() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glColorMask(false, false, false, true)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glDepthMask(true)
    }

    /** Updates the cached viewport size used for camera creation. */
    override fun resize(
        width: Int,
        height: Int,
    ) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    /** Disposes renderer-owned GPU resources and cached backend assets. */
    override fun dispose() {
        modelBatch.dispose()
        lineRenderer.dispose()
        modelViewerDebugRenderer.dispose()
        pbrPreviewRenderer.dispose()
        skyboxRenderer.dispose()
        instances.clear()
        animationControllers.clear()
        gltfScenes.clear()
        warnedGltfRenderKeys.clear()
        primitives.values.forEach { it.dispose() }
        dynamicModels.values.forEach { it.model.dispose() }
        assets.dispose()
    }

    /** Renders one static model command, including primitive and glTF handling. */
    private fun renderModel(
        command: DrawModel,
        environment: Environment,
        camera: Camera,
    ) {
        command.material.shader
            .assets()
            .forEach(assets::queue)
        assets.queue(command.model)

        val model =
            if (command.model.isPrimitive) {
                primitive(command.model.path)
            } else if (command.model.isGltf()) {
                renderGltfScene(command, environment, camera)
                return
            } else {
                assets.gdxModel(command.model)
            } ?: return

        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val existing = instances[cacheKey]
        val instance =
            if (existing?.model === model) {
                existing
            } else {
                ModelInstance(model).also { created ->
                    instances[cacheKey] = created
                }
            }

        instance.materials.forEach { applyMaterialAttributes(it, command.material) }
        applyAnimationPreview(instance, cacheKey, command.animation)

        val transform = command.transform
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
        applyVisibleMeshPartFilter(instance, command.visibleMeshPartIndices)

        modelBatch.render(instance, environment)
    }

    /** Returns a prepared model instance for shader-based debug rendering. */
    private fun modelInstanceForDebug(
        command: DrawModel,
        camera: Camera,
    ): ModelInstance? {
        command.material.shader
            .assets()
            .forEach(assets::queue)
        assets.queue(command.model)
        val instance =
            if (command.model.isGltf()) {
                val sceneAsset = assets.gltfScene(command.model) ?: return null
                val cacheKey = ModelCacheKey(command.entityId, command.model.path)
                val scene =
                    gltfScenes.getOrPut(cacheKey) {
                        GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
                    }
                applyAnimationPreview(scene, command.animation)
                applyTransform(scene.modelInstance, command)
                applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
                scene.update(camera, if (command.animation != null) 0f else Gdx.graphics.deltaTime)
                scene.modelInstance
            } else {
                val model =
                    if (command.model.isPrimitive) {
                        primitive(command.model.path)
                    } else {
                        assets.gdxModel(command.model)
                    } ?: return null

                val cacheKey = ModelCacheKey(command.entityId, command.model.path)
                val existing = instances[cacheKey]
                if (existing?.model === model) {
                    existing
                } else {
                    ModelInstance(model).also { created ->
                        instances[cacheKey] = created
                    }
                }.also { prepared ->
                    prepared.materials.forEach { applyMaterialAttributes(it, command.material) }
                    applyAnimationPreview(prepared, cacheKey, command.animation)
                    applyTransform(prepared, command)
                    applyVisibleMeshPartFilter(prepared, command.visibleMeshPartIndices)
                }
            }
        return instance
    }

    /** Renders one dynamic mesh-backed model command. */
    private fun renderDynamicModel(
        command: DrawDynamicModel,
        environment: Environment,
    ) {
        command.runtimeTextures.forEach(assets::upsertRuntimeTexture)
        val model = dynamicGdxModel(command.model)
        val cacheKey = ModelCacheKey(command.entityId, command.model.id)
        val existing = instances[cacheKey]
        val instance =
            if (existing?.model === model) {
                existing
            } else {
                ModelInstance(model).also { created ->
                    instances[cacheKey] = created
                }
            }

        instance.materials.forEach { applyMaterialAttributes(it, command.material) }

        applyTransform(instance, command.transform)
        modelBatch.render(instance, environment)
    }

    /** Applies engine material color and optional diffuse texture to a LibGDX material. */
    private fun applyMaterialAttributes(
        gdxMaterial: Material,
        material: com.pashkd.krender.engine.render3d.Material,
    ) {
        gdxMaterial.set(
            ColorAttribute.createDiffuse(
                material.baseColor.r,
                material.baseColor.g,
                material.baseColor.b,
                material.baseColor.a,
            ),
        )
        val resolvedDiffuseTexture =
            material.diffuseTextureRef
                ?.let { ref -> assets.textureByPathOrId(ref.id) }
        if (resolvedDiffuseTexture != null) {
            gdxMaterial.set(TextureAttribute.createDiffuse(resolvedDiffuseTexture))
        } else {
            gdxMaterial.remove(TextureAttribute.Diffuse)
        }
    }

    /** Renders a cached glTF scene instance. */
    private fun renderGltfScene(
        command: DrawModel,
        environment: Environment,
        camera: Camera,
    ) {
        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model) ?: return
        val requiredBones = maxOf(sceneAsset.maxBones, assets.modelInfo(command.model)?.boneCount ?: 0)
        if (requiredBones > maxShaderBones) {
            warnGltfRenderOnce("bones-${command.model.path}-$requiredBones") {
                "Skipping glTF model '${command.model.path}' because it requires $requiredBones bones, " +
                    "but the current renderer is configured for maxBones=$maxShaderBones. " +
                    "Increase -Dkrender.gl.maxBones or use skeleton-only view."
            }
            return
        }
        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val scene =
            gltfScenes.getOrPut(cacheKey) {
                GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
            }
        applyAnimationPreview(scene, command.animation)

        val transform = command.transform
        scene.modelInstance.transform.idt()
        scene.modelInstance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        scene.modelInstance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        scene.modelInstance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        scene.modelInstance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        scene.modelInstance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
        applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
        scene.update(camera, if (command.animation != null) 0f else Gdx.graphics.deltaTime)
        try {
            modelBatch.render(scene, environment)
        } catch (error: Throwable) {
            warnGltfRenderOnce("render-${command.model.path}-${error::class.qualifiedName}") {
                "glTF model render failed for '${command.model.path}': ${error.message ?: error::class.simpleName}"
            }
        }
    }

    private fun warnGltfRenderOnce(
        key: String,
        message: () -> String,
    ) {
        if (warnedGltfRenderKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    /** Converts a static model command into line vertices and draws it as wireframe. */
    private fun renderWireframeModel(
        command: DrawModel,
        camera: Camera,
    ) {
        command.material.shader
            .assets()
            .forEach(assets::queue)
        assets.queue(command.model)

        val instance =
            if (command.model.isGltf()) {
                val scene = wireframeGltfScene(command, camera) ?: return
                scene.modelInstance
            } else {
                val model =
                    if (command.model.isPrimitive) {
                        primitive(command.model.path)
                    } else {
                        assets.gdxModel(command.model)
                    } ?: return

                val cacheKey = ModelCacheKey(command.entityId, command.model.path)
                val existing = instances[cacheKey]
                if (existing?.model === model) {
                    existing
                } else {
                    ModelInstance(model).also { created ->
                        instances[cacheKey] = created
                    }
                }.also { instance ->
                    applyAnimationPreview(instance, cacheKey, command.animation)
                    applyTransform(instance, command)
                    applyVisibleMeshPartFilter(instance, command.visibleMeshPartIndices)
                }
            }

        val vertices = wireframeVerticesFor(instance, command.material.baseColor)
        lineRenderer.renderVertices(vertices, camera)
    }

    /** Converts a dynamic model command into line vertices and draws it as wireframe. */
    private fun renderWireframeDynamicModel(
        command: DrawDynamicModel,
        camera: Camera,
    ) {
        val model = dynamicGdxModel(command.model)
        val cacheKey = ModelCacheKey(command.entityId, command.model.id)
        val existing = instances[cacheKey]
        val instance =
            if (existing?.model === model) {
                existing
            } else {
                ModelInstance(model).also { created ->
                    instances[cacheKey] = created
                }
            }

        applyTransform(instance, command.transform)
        val vertices = wireframeVerticesFor(instance, command.material.baseColor)
        lineRenderer.renderVertices(vertices, camera)
    }

    /** Returns a transformed glTF scene instance for wireframe extraction. */
    private fun wireframeGltfScene(
        command: DrawModel,
        camera: Camera,
    ): GltfScene? {
        val sceneAsset = assets.gltfScene(command.model) ?: return null
        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val scene =
            gltfScenes.getOrPut(cacheKey) {
                GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
            }
        applyAnimationPreview(scene, command.animation)
        applyTransform(scene.modelInstance, command)
        applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
        scene.update(camera, if (command.animation != null) 0f else Gdx.graphics.deltaTime)
        return scene
    }

    private fun applyAnimationPreview(
        instance: ModelInstance,
        cacheKey: ModelCacheKey,
        preview: AnimationPlaybackView?,
    ) {
        if (instance.animations.isEmpty) return
        val controller = animationControllers.getOrPut(cacheKey) { AnimationController(instance) }
        applyAnimationPreview(instance, controller, preview)
    }

    private fun applyAnimationPreview(
        scene: GltfScene,
        preview: AnimationPlaybackView?,
    ) {
        applyAnimationPreview(scene.modelInstance, scene.animationController, preview)
    }

    private fun applyAnimationPreview(
        instance: ModelInstance,
        controller: AnimationController?,
        preview: AnimationPlaybackView?,
    ) {
        if (controller == null || instance.animations.isEmpty) return
        val animationName = preview?.animationName
        if (animationName.isNullOrBlank()) {
            controller.setAnimation(null as String?)
            controller.update(0f)
            return
        }
        val animation =
            instance.getAnimation(animationName) ?: run {
                controller.setAnimation(null as String?)
                controller.update(0f)
                return
            }
        controller.paused = false
        controller.setAnimation(animationName, if (preview.loop) -1 else 1, 1f, null)
        controller.current?.time = normalizedAnimationTime(animation, preview.timeSeconds, preview.loop)
        controller.update(0f)
    }

    /** Applies a draw command's transform to the instance. */
    private fun applyTransform(
        instance: ModelInstance,
        command: DrawModel,
    ) {
        applyTransform(instance, command.transform)
    }

    /** Applies an engine transform snapshot to the LibGDX model instance. */
    private fun applyTransform(
        instance: ModelInstance,
        transform: com.pashkd.krender.engine.api.TransformSnapshot,
    ) {
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
    }

    /**
     * Applies a backend-neutral node-part index filter using the same flattened node order as model metadata.
     */
    private fun applyVisibleMeshPartFilter(
        instance: ModelInstance,
        visibleMeshPartIndices: Set<Int>?,
    ) {
        var index = 0
        collectNodes(instance.nodes).forEach { node ->
            for (part in node.parts) {
                part.enabled = visibleMeshPartIndices == null || index in visibleMeshPartIndices
                index += 1
            }
        }
    }

    /** Returns a cached LibGDX model for the dynamic mesh revision, rebuilding when needed. */
    private fun dynamicGdxModel(dynamicModel: com.pashkd.krender.engine.api.DynamicModel): Model {
        val cached = dynamicModels[dynamicModel.id]
        if (cached != null && cached.revision == dynamicModel.revision) {
            return cached.model
        }

        cached?.model?.dispose()
        val built = buildDynamicModel(dynamicModel)
        dynamicModels[dynamicModel.id] = DynamicModelCacheEntry(dynamicModel.revision, built)
        return built
    }

    /** Builds a LibGDX [Model] from engine dynamic mesh data. */
    private fun buildDynamicModel(dynamicModel: com.pashkd.krender.engine.api.DynamicModel): Model {
        val meshData = dynamicModel.mesh
        val positions = meshData.positions
        val normals = meshData.normals
        val uvs = meshData.uvs
        val hasColors = meshData.hasVertexColors()
        val maxIndex = meshData.indices.maxOrNull() ?: -1
        val canUseIndices =
            meshData.indices.isNotEmpty() &&
                meshData.vertexCount <= UNSIGNED_SHORT_MASK &&
                maxIndex <= UNSIGNED_SHORT_MASK

        val attributes =
            mutableListOf(
                VertexAttribute.Position(),
                VertexAttribute.Normal(),
                VertexAttribute.TexCoords(0),
            )
        if (hasColors) {
            attributes += VertexAttribute.ColorUnpacked()
        }

        val vertexBuffer =
            if (canUseIndices) {
                interleaveVertices(positions, normals, uvs, meshData.colors)
            } else {
                expandVertices(meshData)
            }
        val vertexStride = dynamicVertexFloatCount(hasColors)

        val mesh =
            Mesh(
                true,
                vertexBuffer.size / vertexStride,
                if (canUseIndices) meshData.indices.size else 0,
                *attributes.toTypedArray(),
            )
        mesh.setVertices(vertexBuffer)
        if (canUseIndices) {
            mesh.setIndices(meshData.indices.map(Int::toShort).toShortArray())
        }

        val material =
            Material(
                ColorAttribute.createDiffuse(1f, 1f, 1f, 1f),
            )
        val partSize = if (canUseIndices) meshData.indices.size else vertexBuffer.size / vertexStride
        val meshPart = MeshPart(dynamicModel.id, mesh, 0, partSize, GL20.GL_TRIANGLES)
        val nodePart = NodePart(meshPart, material)
        val node =
            Node().apply {
                id = dynamicModel.id
                parts.add(nodePart)
            }

        return Model().also { model ->
            model.meshes.add(mesh)
            model.meshParts.add(meshPart)
            model.materials.add(material)
            model.nodes.add(node)
            model.manageDisposable(mesh)
            model.calculateTransforms()
        }
    }

    /** Interleaves indexed vertex attributes into one packed float buffer. */
    private fun interleaveVertices(
        positions: FloatArray,
        normals: FloatArray,
        uvs: FloatArray,
        colors: FloatArray?,
    ): FloatArray {
        val vertexCount = positions.size / 3
        val hasColors = colors != null && colors.size == vertexCount * 4
        val vertices = FloatArray(vertexCount * dynamicVertexFloatCount(hasColors))
        var offset = 0
        for (vertex in 0 until vertexCount) {
            val positionBase = vertex * 3
            val uvBase = vertex * 2
            val colorBase = vertex * 4
            vertices[offset++] = positions[positionBase]
            vertices[offset++] = positions[positionBase + 1]
            vertices[offset++] = positions[positionBase + 2]
            vertices[offset++] = normals[positionBase]
            vertices[offset++] = normals[positionBase + 1]
            vertices[offset++] = normals[positionBase + 2]
            vertices[offset++] = uvs[uvBase]
            vertices[offset++] = uvs[uvBase + 1]
            if (hasColors) {
                vertices[offset++] = colors[colorBase]
                vertices[offset++] = colors[colorBase + 1]
                vertices[offset++] = colors[colorBase + 2]
                vertices[offset++] = colors[colorBase + 3]
            }
        }
        return vertices
    }

    /** Expands indexed mesh data into a non-indexed vertex buffer when required. */
    private fun expandVertices(meshData: com.pashkd.krender.engine.api.DynamicMesh): FloatArray {
        if (meshData.indices.isEmpty()) {
            return interleaveVertices(meshData.positions, meshData.normals, meshData.uvs, meshData.colors)
        }

        val hasColors = meshData.hasVertexColors()
        val vertices = FloatArray(meshData.indices.size * dynamicVertexFloatCount(hasColors))
        var offset = 0
        meshData.indices.forEach { index ->
            val positionBase = index * 3
            val uvBase = index * 2
            val colorBase = index * 4
            vertices[offset++] = meshData.positions[positionBase]
            vertices[offset++] = meshData.positions[positionBase + 1]
            vertices[offset++] = meshData.positions[positionBase + 2]
            vertices[offset++] = meshData.normals[positionBase]
            vertices[offset++] = meshData.normals[positionBase + 1]
            vertices[offset++] = meshData.normals[positionBase + 2]
            vertices[offset++] = meshData.uvs[uvBase]
            vertices[offset++] = meshData.uvs[uvBase + 1]
            if (hasColors) {
                val colors = meshData.colors ?: return@forEach
                vertices[offset++] = colors[colorBase]
                vertices[offset++] = colors[colorBase + 1]
                vertices[offset++] = colors[colorBase + 2]
                vertices[offset++] = colors[colorBase + 3]
            }
        }
        return vertices
    }

    /** Returns whether the dynamic mesh contains one RGBA color per vertex. */
    private fun com.pashkd.krender.engine.api.DynamicMesh.hasVertexColors(): Boolean =
        colors != null && colors.size == vertexCount * 4

    /** Returns the packed float count for one dynamic vertex. */
    private fun dynamicVertexFloatCount(hasColors: Boolean): Int =
        if (hasColors) FLOATS_PER_COLORED_DYNAMIC_VERTEX else FLOATS_PER_DYNAMIC_VERTEX

    /** Extracts world-space wireframe vertices from a model instance. */
    private fun wireframeVerticesFor(
        instance: ModelInstance,
        color: com.pashkd.krender.engine.api.Color,
    ): List<Float> {
        val vertices = mutableListOf<Float>()
        wireframeRenderables.clear()
        instance.getRenderables(wireframeRenderables, wireframeRenderablePool)
        wireframeRenderables.forEach { renderable ->
            appendWireframeMeshPart(vertices, renderable.meshPart, renderable.worldTransform, color)
        }
        wireframeRenderablePool.freeAll(wireframeRenderables)
        wireframeRenderables.clear()
        return vertices
    }

    /** Appends the line edges needed to visualize one mesh part as wireframe. */
    private fun appendWireframeMeshPart(
        vertices: MutableList<Float>,
        meshPart: MeshPart,
        transform: Matrix4,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        val mesh = meshPart.mesh
        val positionAttribute = mesh.getVertexAttribute(VertexAttributes.Usage.Position) ?: return
        val vertexStride = mesh.vertexSize / FLOAT_BYTES
        val positionOffset = positionAttribute.offset / FLOAT_BYTES
        val vertexData = FloatArray(mesh.numVertices * vertexStride)
        mesh.getVertices(vertexData)
        val indexData =
            if (mesh.numIndices > 0) {
                ShortArray(mesh.numIndices).also(mesh::getIndices)
            } else {
                null
            }

        fun vertexIndex(localIndex: Int): Int {
            val absoluteIndex = meshPart.offset + localIndex
            return indexData?.getOrNull(absoluteIndex)?.toInt()?.and(UNSIGNED_SHORT_MASK) ?: absoluteIndex
        }

        fun appendEdge(
            localA: Int,
            localB: Int,
        ) {
            appendWireframeVertex(
                vertices,
                vertexData,
                vertexStride,
                positionOffset,
                vertexIndex(localA),
                transform,
                color,
            )
            appendWireframeVertex(
                vertices,
                vertexData,
                vertexStride,
                positionOffset,
                vertexIndex(localB),
                transform,
                color,
            )
        }

        when (meshPart.primitiveType) {
            GL20.GL_TRIANGLES -> {
                var i = 0
                while (i + 2 < meshPart.size) {
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, i + 2)
                    appendEdge(i + 2, i)
                    i += 3
                }
            }

            GL20.GL_TRIANGLE_STRIP -> {
                for (i in 0 until meshPart.size - 2) {
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, i + 2)
                    appendEdge(i + 2, i)
                }
            }

            GL20.GL_TRIANGLE_FAN -> {
                for (i in 1 until meshPart.size - 1) {
                    appendEdge(0, i)
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, 0)
                }
            }

            GL20.GL_LINES -> {
                var i = 0
                while (i + 1 < meshPart.size) {
                    appendEdge(i, i + 1)
                    i += 2
                }
            }
        }
    }

    /** Appends one transformed wireframe vertex with color. */
    private fun appendWireframeVertex(
        vertices: MutableList<Float>,
        vertexData: FloatArray,
        vertexStride: Int,
        positionOffset: Int,
        vertexIndex: Int,
        transform: Matrix4,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        val base = vertexIndex * vertexStride + positionOffset
        if (base + 2 >= vertexData.size) return
        wireframeTmpVertex.set(vertexData[base], vertexData[base + 1], vertexData[base + 2]).mul(transform)
        vertices += wireframeTmpVertex.x
        vertices += wireframeTmpVertex.y
        vertices += wireframeTmpVertex.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    /** Builds a LibGDX perspective camera from the active scene camera components. */
    private fun cameraFor(context: RenderContext): PerspectiveCamera {
        val activeCameraEntity =
            context.scene.world
                .query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>()
                .firstOrNull()
        val cameraEntity =
            activeCameraEntity ?: context.scene.world
                .query<TransformComponent, PerspectiveCameraComponent>()
                .firstOrNull()
        val cameraTransform = cameraEntity?.get<TransformComponent>()
        val cameraComponent = cameraEntity?.get<PerspectiveCameraComponent>()
        val camera =
            PerspectiveCamera(
                cameraComponent?.fieldOfViewDegrees ?: 67f,
                width.toFloat(),
                height.toFloat(),
            )
        val position = cameraTransform?.position
        camera.position.set(position?.x ?: 0f, position?.y ?: 2.5f, position?.z ?: 6f)
        camera.near = cameraComponent?.near ?: 0.1f
        camera.far = cameraComponent?.far ?: 100f
        val lookAt = cameraComponent?.lookAt
        if (lookAt != null) {
            camera.lookAt(lookAt.x, lookAt.y, lookAt.z)
        } else {
            val euler = cameraTransform?.eulerDegrees
            val pitch = Math.toRadians((euler?.x ?: 0f).toDouble())
            val yaw = Math.toRadians((euler?.y ?: 0f).toDouble())
            camera.direction
                .set(
                    (sin(yaw) * cos(pitch)).toFloat(),
                    sin(pitch).toFloat(),
                    (cos(yaw) * cos(pitch)).toFloat(),
                ).nor()
        }
        camera.update()
        return camera
    }

    /** Builds a LibGDX lighting environment from scene light components. */
    private fun environmentFor(context: RenderContext): Environment {
        val environment = Environment()
        var ambientApplied = false
        context.scene.world.query<TransformComponent, LightComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val light = entity.get<LightComponent>() ?: return@forEach
            when (light.type) {
                LightType.Ambient -> {
                    environment.set(
                        ColorAttribute(
                            ColorAttribute.AmbientLight,
                            light.color.r * light.intensity,
                            light.color.g * light.intensity,
                            light.color.b * light.intensity,
                            light.color.a,
                        ),
                    )
                    ambientApplied = true
                }

                LightType.Directional -> {
                    val direction = Vector3(light.direction.x, light.direction.y, light.direction.z)
                    if (!direction.isZero) {
                        direction.nor()
                        environment.add(
                            DirectionalLight().set(
                                light.color.r * light.intensity,
                                light.color.g * light.intensity,
                                light.color.b * light.intensity,
                                direction.x,
                                direction.y,
                                direction.z,
                            ),
                        )
                    }
                }

                LightType.Point -> {
                    environment.add(
                        PointLight().set(
                            light.color.r,
                            light.color.g,
                            light.color.b,
                            transform.position.x,
                            transform.position.y,
                            transform.position.z,
                            light.intensity,
                        ),
                    )
                }
            }
        }
        if (!ambientApplied) {
            val environmentCommand = context.commands.filterIsInstance<ApplyEnvironment>().firstOrNull()
            val ambient = environmentCommand?.ambientColor ?: defaultAmbientLightColor()
            val intensity = environmentCommand?.ambientIntensity ?: DefaultAmbientLightIntensity
            environment.set(
                ColorAttribute(
                    ColorAttribute.AmbientLight,
                    ambient.r * intensity,
                    ambient.g * intensity,
                    ambient.b * intensity,
                    ambient.a,
                ),
            )
        }
        return environment
    }

    /** Returns a cached primitive model for built-in placeholder assets. */
    private fun primitive(path: String): Model =
        primitives.getOrPut(path) {
            ModelBuilder().createBox(
                1f,
                1f,
                1f,
                Material(ColorAttribute.createDiffuse(0.1f, 0.62f, 0.82f, 1f)),
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            )
        }

    companion object {
        private const val TAG = "GdxRenderer3D"
        private const val DefaultMaxShaderBones = 96
        private const val MinShaderBones = 12
        private const val FLOATS_PER_DYNAMIC_VERTEX = 8
        private const val FLOATS_PER_COLORED_DYNAMIC_VERTEX = 12
        private const val FLOAT_BYTES = 4
        private const val UNSIGNED_SHORT_MASK = 0xFFFF
    }
}

/** Cached LibGDX model entry keyed by the engine dynamic mesh revision. */
private data class DynamicModelCacheEntry(
    val revision: Long,
    val model: Model,
)

internal data class RuntimeTextureEntry(
    val revision: Long,
    val texture: Texture,
)

/** Composite cache key that distinguishes model instances by entity and asset id. */
private data class ModelCacheKey(
    val entityId: Long,
    val modelPath: String,
)

internal fun RuntimeTextureFilter.gdx(): Texture.TextureFilter =
    when (this) {
        RuntimeTextureFilter.Nearest -> Texture.TextureFilter.Nearest
        RuntimeTextureFilter.Linear -> Texture.TextureFilter.Linear
    }

internal fun RuntimeTextureWrap.gdx(): Texture.TextureWrap =
    when (this) {
        RuntimeTextureWrap.ClampToEdge -> Texture.TextureWrap.ClampToEdge
        RuntimeTextureWrap.Repeat -> Texture.TextureWrap.Repeat
    }

/**
 * Shared runtime skybox renderer for backend-neutral environment commands.
 */
private class GdxSkyboxRenderer(
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

/**
 * glTF PBR preview renderer backed by gdx-gltf SceneManager.
 */
private class GdxGltfPbrPreviewRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<ModelCacheKey, PbrSceneEntry>()
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: DrawModel,
        camera: Camera,
        meshPartFilter: (ModelInstance, Set<Int>?) -> Unit,
    ): Boolean {
        val settings = command.pbrPreview?.takeIf { it.enabled } ?: return false
        if (!command.model.isGltf()) {
            warnOnce("not-gltf-${command.model.path}") {
                "PBR preview unavailable: '${command.model.path}' is not a glTF/glb model."
            }
            return false
        }

        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model)
        if (sceneAsset == null) {
            warnOnce("not-loaded-${command.model.path}") {
                "PBR preview unavailable: glTF asset '${command.model.path}' is not loaded yet."
            }
            return false
        }

        return try {
            settings.skyboxTexture?.let { ref -> assets.queue(AssetRef.texture(ref.id)) }
            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val entry =
                entries.getOrPut(cacheKey) {
                    val scene = GltfScene(sceneAsset.scene)
                    val manager =
                        GltfSceneManager(
                            PBRShaderProvider.createDefault(sceneAsset.maxBones.coerceAtLeast(1)),
                            PBRShaderProvider.createDefaultDepth(sceneAsset.maxBones.coerceAtLeast(1)),
                        )
                    manager.addScene(scene, false)
                    logger.info(TAG) { "Created PBR preview scene for '${command.model.path}'." }
                    PbrSceneEntry(scene = scene, manager = manager)
                }

            applyTransform(entry.scene.modelInstance, command)
            meshPartFilter(entry.scene.modelInstance, command.visibleMeshPartIndices)
            configureEnvironment(entry, settings)
            entry.manager.setCamera(camera)
            entry.manager.update(Gdx.graphics.deltaTime)
            entry.manager.render()
            true
        } catch (error: Throwable) {
            warnOnce("error-${command.model.path}-${error::class.qualifiedName}") {
                "PBR preview unavailable for '${command.model.path}': ${error.message ?: error::class.simpleName}"
            }
            false
        }
    }

    fun dispose() {
        entries.values.forEach(PbrSceneEntry::dispose)
        entries.clear()
    }

    private fun configureEnvironment(
        entry: PbrSceneEntry,
        settings: com.pashkd.krender.engine.api.PbrPreviewView,
    ) {
        val direction = pbrLightDirection(settings.directionalLightYawDegrees, settings.directionalLightPitchDegrees)
        val intensity = (settings.environmentIntensity * settings.exposure).coerceAtLeast(0f)
        entry.manager.environment.clear()
        entry.manager.environment.set(
            ColorAttribute(
                ColorAttribute.AmbientLight,
                0.08f * intensity,
                0.09f * intensity,
                0.1f * intensity,
                1f,
            ),
        )
        if (settings.directionalLightEnabled) {
            entry.manager.environment.add(
                DirectionalLightEx().set(
                    Color.WHITE,
                    direction,
                    intensity.coerceAtLeast(0.01f),
                ),
            )
        }
        val skyboxCubemap =
            settings.skyboxTexture
                ?.id
                ?.takeIf { path -> settings.showSkybox && path.isNotBlank() }
                ?.let { path -> entry.ensureSkyboxCubemap(path) }
        if (skyboxCubemap != null) {
            entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(skyboxCubemap))
            entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(skyboxCubemap))
            entry.manager.skyBox = entry.skybox
            return
        } else {
            entry.manager.skyBox = null
        }

        if (settings.showSkybox) {
            val iblAvailable = entry.ensureIbl(direction, intensity.coerceAtLeast(0.01f))
            if (iblAvailable) {
                entry.irradianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(map)) }
                entry.radianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(map)) }
                entry.manager.skyBox = entry.skybox
            }
        }
    }

    private fun PbrSceneEntry.ensureSkyboxCubemap(path: String): Cubemap? {
        if (skyboxTexturePath == path && skyboxCubemap != null) return skyboxCubemap
        disposeSkyboxCubemap()
        return try {
            val cubemap = cubemapFromSingleTexture(path)
            try {
                SceneSkybox.enableMipmaps(cubemap)
            } catch (error: Throwable) {
                warnOnce("skybox-mipmaps-$path-${error::class.qualifiedName}") {
                    "PBR preview skybox mipmaps unavailable; roughness reflections may be less accurate: " +
                        (error.message ?: error::class.simpleName)
                }
            }
            skyboxTexturePath = path
            skyboxCubemap = cubemap
            skybox = SceneSkybox(cubemap)
            cubemap
        } catch (error: Throwable) {
            disposeSkyboxCubemap()
            warnOnce("skybox-texture-$path-${error::class.qualifiedName}") {
                "PBR preview skybox texture '$path' unavailable; continuing without asset skybox: " +
                    (error.message ?: error::class.simpleName)
            }
            null
        }
    }

    private fun PbrSceneEntry.ensureIbl(
        direction: Vector3,
        intensity: Float,
    ): Boolean {
        val nextKey =
            PbrEnvironmentKey(
                intensity = "%.3f".format(intensity),
                directionX = "%.3f".format(direction.x),
                directionY = "%.3f".format(direction.y),
                directionZ = "%.3f".format(direction.z),
            )
        if (environmentKey == nextKey) return iblAvailable
        disposeEnvironment()
        val light = DirectionalLightEx().set(Color.WHITE, direction, intensity)
        val builder = IBLBuilder.createOutdoor(light)
        try {
            envMap = builder.buildEnvMap(64)
            irradianceMap = builder.buildIrradianceMap(16)
            radianceMap = builder.buildRadianceMap(64)
            skybox = envMap?.let(::SceneSkybox)
            environmentKey = nextKey
            iblAvailable = true
            return true
        } catch (error: Throwable) {
            disposeEnvironment()
            environmentKey = nextKey
            iblAvailable = false
            warnOnce("ibl-unavailable-${error::class.qualifiedName}") {
                "PBR preview IBL/skybox unavailable; continuing with direct lighting only: " +
                    (error.message ?: error::class.simpleName)
            }
            return false
        } finally {
            builder.dispose()
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

    companion object {
        private const val TAG = "GdxGltfPbrPreviewRenderer"
    }
}

private data class PbrSceneEntry(
    val scene: GltfScene,
    val manager: GltfSceneManager,
    var environmentKey: PbrEnvironmentKey? = null,
    var envMap: Cubemap? = null,
    var irradianceMap: Cubemap? = null,
    var radianceMap: Cubemap? = null,
    var skyboxTexturePath: String? = null,
    var skyboxCubemap: Cubemap? = null,
    var skybox: SceneSkybox? = null,
    var iblAvailable: Boolean = false,
) {
    fun dispose() {
        disposeEnvironment()
        disposeSkyboxCubemap()
        manager.dispose()
    }

    fun disposeEnvironment() {
        if (skyboxCubemap == null) {
            skybox?.dispose()
            skybox = null
        }
        envMap?.dispose()
        envMap = null
        irradianceMap?.dispose()
        irradianceMap = null
        radianceMap?.dispose()
        radianceMap = null
        iblAvailable = false
    }

    fun disposeSkyboxCubemap() {
        skybox?.dispose()
        skybox = null
        skyboxCubemap?.dispose()
        skyboxCubemap = null
        skyboxTexturePath = null
    }
}

private data class PbrEnvironmentKey(
    val intensity: String,
    val directionX: String,
    val directionY: String,
    val directionZ: String,
)

private fun pbrLightDirection(
    yawDegrees: Float,
    pitchDegrees: Float,
): Vector3 {
    val yaw = Math.toRadians(yawDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.toDouble())
    val x = (cos(pitch) * cos(yaw)).toFloat()
    val y = sin(pitch).toFloat()
    val z = (cos(pitch) * sin(yaw)).toFloat()
    return Vector3(-x, -y, -z).nor()
}

private fun cubemapFromSingleTexture(path: String): Cubemap {
    val source = Pixmap(Gdx.files.internal(path))
    val layout = cubemapLayoutFor(source.width, source.height)
    val faceSize = layout.faceSize
    val faces = ArrayList<Pixmap>(CUBEMAP_FACE_COUNT)
    try {
        layout.faces.forEach { faceRegion ->
            val face = Pixmap(faceSize, faceSize, source.format)
            face.drawPixmap(
                source,
                0,
                0,
                faceRegion.x * faceSize,
                faceRegion.y * faceSize,
                faceSize,
                faceSize,
            )
            faces += face
        }
        return Cubemap(
            faces[0].textureData(disposePixmap = true),
            faces[1].textureData(disposePixmap = true),
            faces[2].textureData(disposePixmap = true),
            faces[3].textureData(disposePixmap = true),
            faces[4].textureData(disposePixmap = true),
            faces[5].textureData(disposePixmap = true),
        ).also { cubemap ->
            cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    } catch (error: Throwable) {
        faces.forEach { face ->
            if (!face.isDisposed) face.dispose()
        }
        throw error
    } finally {
        source.dispose()
    }
}

private data class CubemapLayout(
    val faceSize: Int,
    val faces: List<CubemapFaceRegion>,
)

private data class CubemapFaceRegion(
    val x: Int,
    val y: Int,
)

private fun cubemapLayoutFor(
    width: Int,
    height: Int,
): CubemapLayout {
    require(width > 0 && height > 0) {
        "Cubemap source dimensions must be positive, got ${width}x$height."
    }
    return when {
        width == height * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = height,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(index, 0) },
            )
        }

        height == width * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = width,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(0, index) },
            )
        }

        width % 4 == 0 && height % 3 == 0 && width / 4 == height / 3 -> {
            // Standard horizontal cross:
            //       +Y
            // -X +Z +X -Z
            //       -Y
            val faceSize = width / 4
            CubemapLayout(
                faceSize = faceSize,
                faces =
                    listOf(
                        CubemapFaceRegion(2, 1), // +X
                        CubemapFaceRegion(0, 1), // -X
                        CubemapFaceRegion(1, 0), // +Y
                        CubemapFaceRegion(1, 2), // -Y
                        CubemapFaceRegion(1, 1), // +Z
                        CubemapFaceRegion(3, 1), // -Z
                    ),
            )
        }

        width % 3 == 0 && height % 4 == 0 && width / 3 == height / 4 -> {
            // Standard vertical cross:
            //    +Y
            // -X +Z +X
            //    -Y
            //    -Z
            val faceSize = width / 3
            CubemapLayout(
                faceSize = faceSize,
                faces =
                    listOf(
                        CubemapFaceRegion(2, 1), // +X
                        CubemapFaceRegion(0, 1), // -X
                        CubemapFaceRegion(1, 0), // +Y
                        CubemapFaceRegion(1, 2), // -Y
                        CubemapFaceRegion(1, 1), // +Z
                        CubemapFaceRegion(1, 3), // -Z
                    ),
            )
        }

        else ->
            error(
                "Unsupported cubemap texture layout ${width}x$height. " +
                    "Expected 6x1 strip, 1x6 strip, 4x3 cross, or 3x4 cross.",
            )
    }
}

private fun Pixmap.textureData(disposePixmap: Boolean): PixmapTextureData =
    PixmapTextureData(this, format, false, disposePixmap)

private const val CUBEMAP_FACE_COUNT = 6

private fun applyTransform(
    instance: ModelInstance,
    command: DrawModel,
) {
    val transform = command.transform
    instance.transform.idt()
    instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
    instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
    instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
    instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
    instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
}

/**
 * Shader-based ModelViewer material debug renderer.
 */
private class GdxModelViewerDebugRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val uvShaders = mutableMapOf<Int, ShaderProgram?>()
    private val fallbackShader: ShaderProgram? =
        compileShader(
            name = "model-viewer-debug-fallback",
            vertex =
                """
                attribute vec3 a_position;
                uniform mat4 u_projViewTrans;
                uniform mat4 u_worldTrans;

                void main() {
                    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
                }
                """.trimIndent(),
            fragment =
                """
                #ifdef GL_ES
                precision mediump float;
                #endif

                uniform vec4 u_FallbackColor;

                void main() {
                    gl_FragColor = u_FallbackColor;
                }
                """.trimIndent(),
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
            com.pashkd.krender.engine.api.DebugCullingMode.Backface -> {
                Gdx.gl.glEnable(GL20.GL_CULL_FACE)
                Gdx.gl.glCullFace(GL20.GL_BACK)
            }

            com.pashkd.krender.engine.api.DebugCullingMode.DoubleSided -> {
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
        debugView: com.pashkd.krender.engine.api.MaterialDebugView,
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
        // TODO: carry semantic texture color space through asset loading; scalar debug channels are sampled raw here.
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
        debugView: com.pashkd.krender.engine.api.MaterialDebugView,
    ): com.pashkd.krender.engine.api.MaterialDebugTextureRef? =
        debugView.textureRefs.firstOrNull { ref ->
            ref.materialId != null && material.id != null && ref.materialId == material.id
        }
            ?: debugView.textureRefs.firstOrNull { ref -> materialIndex != null && ref.materialIndex == materialIndex }
            ?: debugView.textureRefs.firstOrNull { ref -> ref.materialIndex == null && ref.materialId == null }

    private fun matchesSelectedMaterial(
        material: Material,
        materialIndex: Int?,
        debugView: com.pashkd.krender.engine.api.MaterialDebugView,
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
                    """
                    attribute vec3 a_position;
                    attribute vec2 a_texCoord$uvChannel;
                    uniform mat4 u_projViewTrans;
                    uniform mat4 u_worldTrans;
                    varying vec2 v_uv;

                    void main() {
                        v_uv = a_texCoord$uvChannel;
                        gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
                    }
                    """.trimIndent(),
                fragment =
                    """
                    #ifdef GL_ES
                    precision mediump float;
                    #endif

                    varying vec2 v_uv;
                    uniform int u_DebugMode;
                    uniform int u_DebugComponent;
                    uniform sampler2D u_DebugTexture;
                    uniform float u_UvCheckerScale;

                    void main() {
                        vec2 uv = v_uv;
                        if (u_DebugMode == ${debugModeCode(MaterialDebugMode.UvChecker)}) {
                            uv = v_uv * u_UvCheckerScale;
                        }
                        vec4 texel = texture2D(u_DebugTexture, uv);
                        if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.R)}) {
                            gl_FragColor = vec4(vec3(texel.r), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.G)}) {
                            gl_FragColor = vec4(vec3(texel.g), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.B)}) {
                            gl_FragColor = vec4(vec3(texel.b), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.A)}) {
                            gl_FragColor = vec4(vec3(texel.a), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.RGBA)}) {
                            gl_FragColor = texel;
                        } else {
                            gl_FragColor = vec4(texel.rgb, 1.0);
                        }
                    }
                    """.trimIndent(),
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

private fun normalizeTextureChannel(channel: String?): String =
    channel
        .orEmpty()
        .lowercase()
        .filter { char -> char.isLetterOrDigit() }

/**
 * Minimal shader-based line renderer used for debug grids, axes, and wireframes.
 */
class GdxLineShaderRenderer {
    private val shader =
        ShaderProgram(
            """
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            varying vec4 v_color;

            void main() {
                v_color = a_color;
                gl_Position = u_projViewTrans * vec4(a_position, 1.0);
            }
            """.trimIndent(),
            """
            #ifdef GL_ES
            precision mediump float;
            #endif

            varying vec4 v_color;

            void main() {
                gl_FragColor = v_color;
            }
            """.trimIndent(),
        )

    private var mesh: Mesh? = null
    private var vertexCapacity: Int = 0

    init {
        check(shader.isCompiled) { shader.log }
    }

    /** Renders non-overlay world debug lines such as grids and axes. */
    fun render(
        commands: List<com.pashkd.krender.engine.api.RenderCommand>,
        camera: Camera,
    ) {
        val vertices = mutableListOf<Float>()
        commands.forEach { command ->
            when (command) {
                is DrawWorldGrid -> appendGrid(vertices, command)
                is DrawWorldAxes -> appendAxes(vertices, command)
                else -> Unit
            }
        }

        renderVertices(vertices, camera)
    }

    /** Renders explicit overlay lines without depth testing. */
    fun renderOverlayLines(
        commands: List<com.pashkd.krender.engine.api.RenderCommand>,
        camera: Camera,
    ) {
        val vertices = mutableListOf<Float>()
        commands.filterIsInstance<DrawLine>().forEach { command ->
            appendLine(vertices, command.from, command.to, command.color)
        }

        renderVertices(vertices, camera, depthTest = false)
    }

    /** Uploads line vertices and renders them with the internal shader. */
    fun renderVertices(
        vertices: List<Float>,
        camera: Camera,
        depthTest: Boolean = true,
    ) {
        val vertexCount = vertices.size / FLOATS_PER_VERTEX
        if (vertexCount == 0) return

        val lineMesh = meshFor(vertexCount)
        lineMesh.setVertices(vertices.toFloatArray())

        if (depthTest) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        } else {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        }
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1f)

        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        lineMesh.render(shader, GL20.GL_LINES, 0, vertexCount)

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
    }

    /** Disposes the reusable mesh and shader program. */
    fun dispose() {
        mesh?.dispose()
        shader.dispose()
    }

    /** Appends a world grid to the vertex list. */
    private fun appendGrid(
        vertices: MutableList<Float>,
        command: DrawWorldGrid,
    ) {
        val half = command.halfExtentCells.coerceAtLeast(1)
        val min = -half * command.cellSize
        val max = half * command.cellSize

        for (i in -half..half) {
            val offset = i * command.cellSize
            appendLine(
                vertices,
                from = Vec3(offset, command.y, min),
                to = Vec3(offset, command.y, max),
                color = command.color,
            )
            appendLine(
                vertices,
                from = Vec3(min, command.y, offset),
                to = Vec3(max, command.y, offset),
                color = command.color,
            )
        }
    }

    /** Appends RGB world axes centered at the origin. */
    private fun appendAxes(
        vertices: MutableList<Float>,
        command: DrawWorldAxes,
    ) {
        val length = command.length.coerceAtLeast(1f)
        appendLine(
            vertices,
            Vec3(-length, 0f, 0f),
            Vec3(length, 0f, 0f),
            com.pashkd.krender.engine.api
                .Color(1f, 0f, 0f, 1f),
        )
        appendLine(
            vertices,
            Vec3(0f, -length, 0f),
            Vec3(0f, length, 0f),
            com.pashkd.krender.engine.api
                .Color(0f, 1f, 0f, 1f),
        )
        appendLine(
            vertices,
            Vec3(0f, 0f, -length),
            Vec3(0f, 0f, length),
            com.pashkd.krender.engine.api
                .Color(0f, 0.35f, 1f, 1f),
        )
    }

    /** Appends one colored line segment. */
    private fun appendLine(
        vertices: MutableList<Float>,
        from: Vec3,
        to: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        appendVertex(vertices, from, color)
        appendVertex(vertices, to, color)
    }

    /** Appends one colored line vertex. */
    private fun appendVertex(
        vertices: MutableList<Float>,
        position: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        vertices += position.x
        vertices += position.y
        vertices += position.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    /** Returns a reusable mesh with capacity for the requested vertex count. */
    private fun meshFor(vertexCount: Int): Mesh {
        if (mesh == null || vertexCount > vertexCapacity) {
            mesh?.dispose()
            vertexCapacity = vertexCount
            mesh =
                Mesh(
                    false,
                    vertexCapacity,
                    0,
                    VertexAttribute.Position(),
                    VertexAttribute.ColorUnpacked(),
                )
        }
        return mesh ?: error("Line mesh was not created")
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 7
    }
}

/** Returns whether the asset reference points to a glTF or GLB model. */
internal fun AssetRef<*>.isGltf(): Boolean =
    path.endsWith(".glb", ignoreCase = true) || path.endsWith(".gltf", ignoreCase = true)

internal fun normalizedAnimationTime(
    animation: Animation,
    timeSeconds: Float,
    loop: Boolean,
): Float {
    val duration = animation.duration.takeIf { it > 0f } ?: return 0f
    if (!loop) return timeSeconds.coerceIn(0f, duration)
    val wrapped = timeSeconds % duration
    return if (wrapped < 0f) wrapped + duration else wrapped
}

private fun systemBoolean(
    name: String,
    default: Boolean,
): Boolean {
    val envName = name.replace('.', '_').uppercase()
    val value =
        System.getProperty(name)
            ?: System.getenv(envName)
            ?: return default
    return when (value.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }
}

private fun systemInt(
    name: String,
    default: Int,
): Int {
    val envName = name.replace('.', '_').uppercase()
    val value =
        System.getProperty(name)
            ?: System.getenv(envName)
            ?: return default
    return value.trim().toIntOrNull() ?: default
}
