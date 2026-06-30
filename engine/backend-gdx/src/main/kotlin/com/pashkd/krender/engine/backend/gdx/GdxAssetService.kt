package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.UBJsonReader
import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.api.Color
import net.mgsx.gltf.loaders.glb.GLBAssetLoader
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute
import net.mgsx.gltf.scene3d.scene.SceneAsset

/**
 * Asset service that queues, loads, inspects, and unloads LibGDX-backed assets.
 */
class GdxAssetService(
    private val logger: Logger? = null,
) : AssetService {
    private val manager = AssetManager()
    private val requested = mutableSetOf<String>()
    private val missing = mutableSetOf<String>()
    private val loggedLoaded = mutableSetOf<String>()
    private val warnedTextureBindings = mutableSetOf<String>()
    private val failedLoads = mutableMapOf<String, String>()
    private val shaderSources = mutableMapOf<String, String>()
    private val modelInfos = mutableMapOf<String, ModelAssetInfo>()
    private val modelSkeletons = mutableMapOf<String, ModelSkeletonInfo>()
    private val modelBoundsCache = mutableMapOf<String, ModelAssetBounds>()
    private val modelTriangleCounts = mutableMapOf<String, Int>()
    private val texturePreviewRegistry = mutableMapOf<String, Texture>()
    private val runtimeTextures = mutableMapOf<String, RuntimeTextureEntry>()
    private val modelTexturePreviewKeys = mutableMapOf<String, Set<String>>()
    private val poseSampler = GdxModelPoseSampler(this)

    init {
        manager.setLoader(Model::class.java, ".g3dj", G3dModelLoader(JsonReader()))
        manager.setLoader(Model::class.java, ".g3db", G3dModelLoader(UBJsonReader()))
        manager.setLoader(Model::class.java, ".obj", ObjLoader())
        manager.setLoader(SceneAsset::class.java, ".gltf", GLTFAssetLoader())
        manager.setLoader(SceneAsset::class.java, ".glb", GLBAssetLoader())
    }

    /** Queues a supported asset for loading if it exists and is not already tracked. */
    override fun queue(asset: AssetRef<*>) {
        if (asset.isPrimitive || asset.path in requested || asset.path in missing) return

        when (asset.type) {
            ModelAsset::class -> {
                if (!Gdx.files.internal(asset.path).exists()) {
                    missing += asset.path
                    throw missingAsset("model", asset.path)
                }
                requested += asset.path
                failedLoads -= asset.path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Queue asset type=model path='${asset.path}' gltf=${asset.isGltf()}" }
                if (asset.isGltf()) {
                    manager.load(asset.path, SceneAsset::class.java)
                } else {
                    manager.load(asset.path, Model::class.java)
                }
            }

            TextureAsset::class -> {
                if (!Gdx.files.internal(asset.path).exists()) {
                    missing += asset.path
                    throw missingAsset("texture", asset.path)
                }
                requested += asset.path
                failedLoads -= asset.path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Queue asset type=texture path='${asset.path}'" }
                manager.load(asset.path, Texture::class.java)
            }

            TerrainAsset::class -> {
                logger?.info(GDX_ASSET_SERVICE_TAG) {
                    "Ignoring queued terrain asset path='${asset.path}'. Runtime terrain loading is handled outside AssetService."
                }
            }

            ShaderAsset::class -> {
                val file = Gdx.files.internal(asset.path)
                if (file.exists()) {
                    requested += asset.path
                    failedLoads -= asset.path
                    shaderSources[asset.path] = file.readString()
                    logger?.info(GDX_ASSET_SERVICE_TAG) { "Loaded shader source path='${asset.path}'" }
                } else {
                    missing += asset.path
                    throw missingAsset("shader", asset.path)
                }
            }
        }
    }

    private fun missingAsset(
        type: String,
        path: String,
    ): IllegalArgumentException {
        val message = "Asset not found type=$type path='$path'"
        logger?.error(GDX_ASSET_SERVICE_TAG) { message }
        return IllegalArgumentException(message)
    }

    /** Advances asynchronous loading for up to the given time budget. */
    override fun update(budgetMs: Int): Float {
        try {
            manager.update(budgetMs)
        } catch (error: GdxRuntimeException) {
            handleAssetLoadFailure(error)
        }
        logLoadedAssets()
        cacheLoadedModelBounds()
        return progress()
    }

    /** Returns normalized loading progress for queued non-primitive assets. */
    override fun progress(): Float {
        val assetCount = requested.count { !it.startsWith("primitive:") }
        if (assetCount == 0) return 1f
        return manager.progress.coerceIn(0f, 1f)
    }

    /** Returns whether the asset is available through the underlying backend loaders. */
    override fun isLoaded(asset: AssetRef<*>): Boolean {
        if (asset.isPrimitive) return true
        return when (asset.type) {
            ModelAsset::class -> {
                if (asset.isGltf()) {
                    manager.isLoaded(asset.path, SceneAsset::class.java)
                } else {
                    manager.isLoaded(asset.path, Model::class.java)
                }
            }

            TextureAsset::class -> manager.isLoaded(asset.path)
            TerrainAsset::class -> false
            ShaderAsset::class -> asset.path in shaderSources
            else -> false
        }
    }

    override fun loadFailure(asset: AssetRef<*>): String? = failedLoads[asset.path]

    /** Rejects untyped access because core code should keep using asset references. */
    override fun <T : Any> get(asset: AssetRef<T>): T {
        error("Use backend-specific typed accessors for '${asset.path}'. Core code should keep AssetRef handles.")
    }

    /** Returns the triangle count for a loaded backend model, caching the result. */
    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        return modelTriangleCounts.getOrPut(asset.path) {
            when {
                asset.isGltf() -> gltfScene(asset)?.scene?.model?.let(::countTrianglesInModel) ?: 0
                else -> gdxModel(asset)?.let(::countTrianglesInModel) ?: 0
            }
        }
    }

    /**
     * Builds a stable metadata snapshot for the currently loaded model asset.
     */
    override fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        return modelInfos.getOrPut(asset.path) {
            when {
                asset.isGltf() -> {
                    val sceneAsset = gltfScene(asset)
                    val model = sceneAsset?.scene?.model
                    if (model == null) {
                        emptyModelInfo(asset.path, "glTF")
                    } else {
                        buildModelInfo(asset.path, model, sceneAsset)
                    }
                }

                else ->
                    gdxModel(asset)?.let { model ->
                        buildModelInfo(asset.path, model, sceneAsset = null)
                    } ?: emptyModelInfo(asset.path, modelFormat(asset.path))
            }
        }
    }

    override fun modelSkeleton(asset: AssetRef<ModelAsset>): ModelSkeletonInfo? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        modelSkeletons[asset.path]?.let { return it }
        val model =
            when {
                asset.isGltf() -> gltfScene(asset)?.scene?.model
                else -> gdxModel(asset)
            } ?: return null
        val skeleton = buildModelSkeletonInfo(model) ?: return null
        modelSkeletons[asset.path] = skeleton
        return skeleton
    }

    override fun modelSkeletonPose(
        asset: AssetRef<ModelAsset>,
        animationName: String?,
        timeSeconds: Float,
        loop: Boolean,
    ): List<ModelBonePose> {
        if (asset.isPrimitive || !isLoaded(asset)) return emptyList()
        return poseSampler.sample(asset, animationName, timeSeconds, loop)
    }

    /** Returns an opaque handle for a loaded model texture or standalone texture asset. */
    override fun texturePreviewHandle(texturePathOrId: String): TexturePreviewHandle? {
        val key = texturePathOrId.takeIf(String::isNotBlank) ?: return null
        val texture =
            texturePreviewRegistry[key]
                ?: loadedTextureAsset(key)
                ?: return null
        return TexturePreviewHandle(
            id = texture.textureObjectHandle,
            width = texture.width,
            height = texture.height,
        )
    }

    /**
     * Returns cached local-space model bounds extracted from an already loaded backend model.
     */
    override fun modelBounds(asset: AssetRef<ModelAsset>): ModelAssetBounds? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        modelBoundsCache[asset.path]?.let { return it }

        val model =
            when {
                asset.isGltf() -> gltfScene(asset)?.scene?.model
                else -> gdxModel(asset)
            } ?: return null

        val bounds = calculateModelBounds(asset.path, model) ?: return null
        modelBoundsCache[asset.path] = bounds
        return bounds
    }

    /** Unloads a tracked asset and clears any cached metadata derived from it. */
    override fun unload(asset: AssetRef<*>) {
        if (!asset.isPrimitive && manager.isLoaded(asset.path)) {
            manager.unload(asset.path)
        }
        requested -= asset.path
        missing -= asset.path
        failedLoads -= asset.path
        shaderSources -= asset.path
        modelInfos -= asset.path
        modelSkeletons -= asset.path
        modelBoundsCache -= asset.path
        modelTriangleCounts -= asset.path
        texturePreviewRegistry -= asset.path
        modelTexturePreviewKeys.remove(asset.path)?.forEach { key -> texturePreviewRegistry -= key }
        poseSampler.clear(asset.path)
    }

    /** Returns a loaded LibGDX model for non-glTF model assets. */
    fun gdxModel(asset: AssetRef<ModelAsset>): Model? {
        if (asset.isPrimitive || asset.isGltf()) return null
        return if (manager.isLoaded(asset.path)) manager.get(asset.path, Model::class.java) else null
    }

    /** Returns a loaded glTF scene asset when the asset reference points to glTF content. */
    fun gltfScene(asset: AssetRef<ModelAsset>): SceneAsset? {
        if (!asset.isGltf()) return null
        return if (manager.isLoaded(asset.path, SceneAsset::class.java)) {
            manager.get(asset.path, SceneAsset::class.java)
        } else {
            null
        }
    }

    /** Returns a loaded standalone LibGDX texture asset. */
    fun gdxTexture(asset: AssetRef<TextureAsset>): Texture? = loadedTextureAsset(asset.path)

    /** Returns a loaded standalone or model-registered LibGDX texture by path or stable id. */
    fun textureByPathOrId(pathOrId: String): Texture? {
        val texture =
            runtimeTextures[pathOrId]?.texture
                ?: texturePreviewRegistry[pathOrId]
                ?: loadedTextureAsset(pathOrId)
        if (texture == null && warnedTextureBindings.add(pathOrId)) {
            logger?.warn(GDX_ASSET_SERVICE_TAG) {
                "Texture binding unresolved id='$pathOrId' runtime=${pathOrId in runtimeTextures} " +
                    "preview=${pathOrId in texturePreviewRegistry} assetLoaded=${
                        manager.isLoaded(
                            pathOrId,
                            Texture::class.java,
                        )
                    }"
            }
        } else if (texture != null && warnedTextureBindings.add("resolved:$pathOrId")) {
            logger?.debug(GDX_ASSET_SERVICE_TAG) {
                "Texture binding resolved id='$pathOrId' source=${textureSource(pathOrId)} size=${texture.width}x${texture.height}"
            }
        }
        return texture
    }

    /** Uploads or refreshes a runtime-generated texture payload. */
    fun upsertRuntimeTexture(texture: RuntimeTextureData) {
        val existing = runtimeTextures[texture.id]
        if (existing != null && existing.revision == texture.revision) return

        val pixmap = Pixmap(texture.width, texture.height, Pixmap.Format.RGBA8888)
        try {
            var offset = 0
            for (y in 0 until texture.height) {
                for (x in 0 until texture.width) {
                    pixmap.drawPixel(x, y, texture.rgba8888[offset++])
                }
            }
            val uploaded =
                Texture(pixmap).also { gdxTexture ->
                    gdxTexture.setFilter(texture.minFilter.gdx(), texture.magFilter.gdx())
                    gdxTexture.setWrap(texture.uWrap.gdx(), texture.vWrap.gdx())
                }
            existing?.texture?.dispose()
            runtimeTextures[texture.id] = RuntimeTextureEntry(texture.revision, uploaded)
            texturePreviewRegistry[texture.id] = uploaded
            logger?.info(GDX_ASSET_SERVICE_TAG) {
                "Uploaded runtime texture id='${texture.id}' revision=${texture.revision} size=${texture.width}x${texture.height} " +
                    "filter=${texture.minFilter}/${texture.magFilter} wrap=${texture.uWrap}/${texture.vWrap}"
            }
        } finally {
            pixmap.dispose()
        }
    }

    /** Returns a loaded standalone LibGDX texture asset. */
    private fun loadedTextureAsset(path: String): Texture? =
        if (manager.isLoaded(path, Texture::class.java)) {
            manager.get(path, Texture::class.java)
        } else {
            null
        }

    private fun textureSource(pathOrId: String): String =
        when {
            pathOrId in runtimeTextures -> "runtime"
            pathOrId in texturePreviewRegistry -> "previewRegistry"
            manager.isLoaded(pathOrId, Texture::class.java) -> "asset"
            else -> "missing"
        }

    private fun logLoadedAssets() {
        requested.forEach { path ->
            if (path in loggedLoaded) return@forEach
            if (path in failedLoads) return@forEach
            val loaded =
                when {
                    manager.isLoaded(path, SceneAsset::class.java) -> "gltf-scene"
                    manager.isLoaded(path, Model::class.java) -> "model"
                    manager.isLoaded(path, Texture::class.java) -> "texture"
                    path in shaderSources -> "shader"
                    else -> null
                }
            if (loaded != null) {
                loggedLoaded += path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Asset loaded type=$loaded path='$path'" }
            }
        }
    }

    /** Disposes the underlying LibGDX asset manager. */
    fun dispose() {
        runtimeTextures.values.forEach { entry -> entry.texture.dispose() }
        runtimeTextures.clear()
        loggedLoaded.clear()
        warnedTextureBindings.clear()
        poseSampler.clear()
        manager.dispose()
    }

    /**
     * Precomputes bounds for newly loaded models during the asset update phase.
     */
    private fun cacheLoadedModelBounds() {
        requested.forEach { path ->
            if (path in modelBoundsCache) return@forEach
            if (path in failedLoads) return@forEach
            val asset = AssetRef.model(path)
            if (!isLoaded(asset)) return@forEach
            val model =
                when {
                    asset.isGltf() -> gltfScene(asset)?.scene?.model
                    else -> gdxModel(asset)
                } ?: return@forEach
            calculateModelBounds(path, model)?.let { bounds -> modelBoundsCache[path] = bounds }
        }
    }

    /**
     * Extracts render-relevant metadata from the loaded model and optional glTF scene asset.
     */
    private fun buildModelInfo(
        path: String,
        model: Model,
        sceneAsset: SceneAsset?,
    ): ModelAssetInfo {
        val nodes = collectNodes(model.nodes)
        val nodeParts = nodes.flatMap(::nodePartsOf)
        val bounds = modelBoundsCache[path] ?: calculateModelBounds(path, model)?.also { modelBoundsCache[path] = it }
        val attributeSummary = collectVertexAttributeSummary(model.meshes)
        val registeredTexturePreviewKeys = linkedSetOf<String>()
        val materialInfos =
            buildMaterialInfos(model.materials) { key, texture ->
                texturePreviewRegistry[key] = texture
                registeredTexturePreviewKeys += key
            }
        modelTexturePreviewKeys[path] = registeredTexturePreviewKeys
        val textureSlots = materialInfos.flatMap { material -> material.textureSlots }
        val textureChannels =
            textureSlots
                .mapTo(linkedSetOf()) { slot -> slot.channel }
        val textures = linkedSetOf<Texture>()
        var maxBonesPerPart = 0

        model.materials.forEach { material ->
            for (attribute in material) {
                if (attribute is TextureAttribute) {
                    textures += attribute.textureDescription.texture
                }
            }
        }

        nodeParts.forEach { part ->
            maxBonesPerPart = maxOf(maxBonesPerPart, part.bones?.size ?: 0)
        }

        val animations =
            model.animations.mapNotNull { animation ->
                animation.id?.takeIf(String::isNotBlank)?.let { id ->
                    ModelAnimationInfo(
                        name = id,
                        durationSeconds = animation.duration.takeIf { duration -> duration > 0f },
                    )
                }
            }
        val textureCount = sceneAsset?.textures?.size ?: textures.size
        return ModelAssetInfo(
            path = path,
            format = modelFormat(path),
            nodeCount = nodes.size,
            meshCount = model.meshes.size,
            meshPartCount = model.meshParts.size,
            materialCount = model.materials.size,
            vertexCount = model.meshes.sumOf { mesh -> mesh.numVertices },
            triangleCount = triangleCount(asset = AssetRef.model(path)) ?: countTrianglesInModel(model),
            size = bounds?.size(),
            boundsMin = bounds?.min,
            boundsMax = bounds?.max,
            vertexChannels = attributeSummary.vertexChannels.toList(),
            uvChannels = attributeSummary.uvChannels.toList(),
            textureChannels = textureChannels.toList(),
            textureCount = textureCount,
            textureSlotCount = textureSlots.size,
            hasSkeleton = maxBonesPerPart > 0,
            boneCount = maxBonesPerPart,
            boneWeightChannelCount = attributeSummary.boneWeightChannelCount,
            animations = animations,
            animationCount = model.animations.size,
            animationNames = animations.map(ModelAnimationInfo::name),
            meshParts = buildMeshPartInfos(nodes, model.materials),
            materials = materialInfos,
        )
    }

    /**
     * Returns a safe empty metadata snapshot when a backend model cannot be inspected.
     */
    private fun emptyModelInfo(
        path: String,
        format: String,
    ): ModelAssetInfo =
        ModelAssetInfo(
            path = path,
            format = format,
            nodeCount = 0,
            meshCount = 0,
            meshPartCount = 0,
            materialCount = 0,
            vertexCount = 0,
            triangleCount = 0,
            size = null,
            boundsMin = null,
            boundsMax = null,
            vertexChannels = emptyList(),
            uvChannels = emptyList(),
            textureChannels = emptyList(),
            textureCount = 0,
            textureSlotCount = 0,
            hasSkeleton = false,
            boneCount = 0,
            boneWeightChannelCount = 0,
            animations = emptyList(),
            animationCount = 0,
            animationNames = emptyList(),
        )

    private fun buildModelSkeletonInfo(model: Model): ModelSkeletonInfo? {
        if (!supportsSkeletonSampling(model)) return null
        val nodes = collectNodes(model.nodes)
        if (nodes.isEmpty()) return null
        val indexByNode = mutableMapOf<Node, Int>()
        nodes.forEachIndexed { index, node -> indexByNode[node] = index }
        return ModelSkeletonInfo(
            bones =
                nodes.mapIndexed { index, node ->
                    ModelBoneInfo(
                        index = index,
                        name = node.id?.takeIf(String::isNotBlank),
                        parentIndex = node.parent?.let(indexByNode::get),
                    )
                },
        )
    }

    /**
     * Calculates the asset-local model bounds once from LibGDX model data.
     */
    private fun calculateModelBounds(
        path: String,
        model: Model,
    ): ModelAssetBounds? =
        try {
            val bounds = BoundingBox()
            ModelInstance(model).calculateBoundingBox(bounds)
            if (!bounds.isValid) {
                null
            } else {
                ModelAssetBounds(
                    min = Vec3(bounds.min.x, bounds.min.y, bounds.min.z),
                    max = Vec3(bounds.max.x, bounds.max.y, bounds.max.z),
                )
            }
        } catch (error: Throwable) {
            Gdx.app.error("GdxAssetService", "Failed to calculate model bounds for '$path'", error)
            null
        }

    /** Returns the dimensions of a cached model bounds box. */
    private fun ModelAssetBounds.size(): Vec3 = Vec3(max.x - min.x, max.y - min.y, max.z - min.z)

    private fun handleAssetLoadFailure(error: GdxRuntimeException) {
        val failedPath = pendingAssetPath()
        val message = error.message ?: error::class.simpleName ?: "Unknown asset load failure"
        if (failedPath == null) {
            logger?.error(GDX_ASSET_SERVICE_TAG, error) { "Asset update failed without a resolved path: $message" }
            return
        }

        failedLoads[failedPath] = message
        modelInfos -= failedPath
        modelSkeletons -= failedPath
        modelBoundsCache -= failedPath
        modelTriangleCounts -= failedPath
        modelTexturePreviewKeys.remove(failedPath)?.forEach { key -> texturePreviewRegistry -= key }
        poseSampler.clear(failedPath)
        logger?.error(GDX_ASSET_SERVICE_TAG, error) { "Asset load failed path='$failedPath': $message" }
    }

    private fun pendingAssetPath(): String? =
        requested.firstOrNull { path ->
            path !in failedLoads &&
                path !in shaderSources &&
                !manager.isLoaded(path, SceneAsset::class.java) &&
                !manager.isLoaded(path, Model::class.java) &&
                !manager.isLoaded(path, Texture::class.java)
        }
}

/**
 * Describes the collected mesh vertex channels for one loaded model.
 */
private data class VertexAttributeSummary(
    /** Unique vertex channel labels across every mesh. */
    val vertexChannels: LinkedHashSet<String>,
    /** Unique UV channel labels across every mesh. */
    val uvChannels: LinkedHashSet<String>,
    /** Highest number of bone-weight channels available per vertex. */
    val boneWeightChannelCount: Int,
)

/**
 * Flattens the full node hierarchy into one list for logging and inspection.
 */
internal fun collectNodes(nodes: Array<Node>): List<Node> {
    val collected = mutableListOf<Node>()

    fun visit(node: Node) {
        collected += node
        for (child in node.children) {
            visit(child)
        }
    }

    for (node in nodes) {
        visit(node)
    }
    return collected
}

/**
 * Returns every node part attached to the given node.
 */
internal fun nodePartsOf(node: Node): List<NodePart> =
    buildList {
        for (part in node.parts) {
            add(part)
        }
    }

/**
 * Builds read-only mesh-part metadata for model inspection UI.
 */
private fun buildMeshPartInfos(
    nodes: List<Node>,
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
): List<ModelMeshPartInfo> =
    buildList {
        var index = 0
        nodes.forEach { node ->
            for (part in node.parts) {
                val meshPart = part.meshPart
                val materialIndex = part.material?.let { material -> materialIndexOf(materials, material) }
                add(
                    ModelMeshPartInfo(
                        index = index,
                        nodeName = node.id?.takeIf(String::isNotBlank),
                        meshId = meshPart?.mesh?.toString(),
                        partId = meshPart?.id?.takeIf(String::isNotBlank),
                        materialId = part.material?.id?.takeIf(String::isNotBlank),
                        materialIndex = materialIndex,
                        primitiveType = meshPart?.primitiveType?.let(::primitiveTypeLabel),
                        vertexCount = meshPart?.mesh?.numVertices,
                        triangleCount = meshPart?.let(::countTrianglesInMeshPart),
                    ),
                )
                index += 1
            }
        }
    }

/**
 * Builds read-only material metadata for model inspection UI.
 */
private fun buildMaterialInfos(
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
    registerTexturePreview: (String, Texture) -> Unit = { _, _ -> },
): List<ModelMaterialInfo> =
    buildList {
        materials.forEachIndexed { index, material ->
            val diffuseColor = (material.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color
            val materialId = material.id?.takeIf(String::isNotBlank)
            add(
                ModelMaterialInfo(
                    index = index,
                    id = materialId,
                    diffuseTexture = material.textureLabel(TextureAttribute.Diffuse),
                    normalTexture = material.textureLabel(TextureAttribute.Normal),
                    emissiveTexture = material.textureLabel(TextureAttribute.Emissive),
                    textureSlots = materialTextureSlots(material, index, materialId, registerTexturePreview),
                    baseColor = diffuseColor?.let { color -> Color(color.r, color.g, color.b, color.a) },
                    opacity = diffuseColor?.a,
                ),
            )
        }
    }

private fun com.badlogic.gdx.graphics.g3d.Material.textureLabel(type: Long): String? =
    ((get(type) as? TextureAttribute)?.textureDescription?.texture)
        ?.let(::textureIdentifier)

private fun materialTextureSlots(
    material: com.badlogic.gdx.graphics.g3d.Material,
    materialIndex: Int,
    materialId: String?,
    registerTexturePreview: (String, Texture) -> Unit,
): List<ModelTextureSlotInfo> =
    buildList {
        for (attribute in material) {
            if (attribute is TextureAttribute) {
                val texture = attribute.textureDescription.texture
                val texturePath = textureIdentifier(texture)
                if (texturePath != null) {
                    registerTexturePreview(texturePath, texture)
                }
                add(
                    ModelTextureSlotInfo(
                        channel = normalizeTextureChannel(attribute),
                        texturePath = texturePath,
                        uvChannel =
                            attribute.uvIndex
                                .takeIf { uvIndex -> uvIndex >= 0 }
                                ?.let { uvIndex -> "UV$uvIndex" },
                        materialIndex = materialIndex,
                        materialId = materialId,
                    ),
                )
            }
        }
    }

private fun normalizeTextureChannel(attribute: TextureAttribute): String =
    when (attribute.type) {
        PBRTextureAttribute.BaseColorTexture -> "baseColor"
        TextureAttribute.Diffuse -> "diffuse"
        PBRTextureAttribute.NormalTexture,
        TextureAttribute.Normal,
        TextureAttribute.Bump,
        -> "normal"

        PBRTextureAttribute.EmissiveTexture,
        TextureAttribute.Emissive,
        -> "emissive"

        PBRTextureAttribute.OcclusionTexture -> "occlusion"
        PBRTextureAttribute.MetallicRoughnessTexture -> "metallicRoughness"
        else -> {
            val alias = Attribute.getAttributeAlias(attribute.type)?.takeIf(String::isNotBlank)
            normalizeTextureAlias(alias)
        }
    }

private fun normalizeTextureAlias(alias: String?): String =
    when (alias?.lowercase()) {
        "basecolortexture", "basecolor", "diffusetexture" -> "baseColor"
        "diffuse" -> "diffuse"
        "normaltexture", "normal", "bump" -> "normal"
        "emissivetexture", "emissive" -> "emissive"
        "occlusiontexture", "occlusion", "ambient" -> "occlusion"
        "metallicroughnesstexture", "metallicroughness" -> "metallicRoughness"
        "alphatexture", "alpha", "opacity" -> "alpha"
        null -> "unknown"
        else -> alias
    }

private fun textureIdentifier(texture: Texture): String? {
    val fileTextureData = texture.textureData as? FileTextureData
    val filePath = fileTextureData?.fileHandle?.path()?.takeIf(String::isNotBlank)
    return filePath ?: texture.textureObjectHandle
        .takeIf { handle -> handle > 0 }
        ?.let { handle -> "texture:$handle" }
}

internal fun materialIndexOf(
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
    material: com.badlogic.gdx.graphics.g3d.Material,
): Int? {
    materials.forEachIndexed { index, candidate ->
        if (candidate === material) return index
    }
    val materialId = material.id?.takeIf(String::isNotBlank) ?: return null
    return materials
        .indexOfFirst { candidate -> candidate.id == materialId }
        .takeIf { index -> index >= 0 }
}

/**
 * Extracts vertex-channel, UV, and skin-weight information from every mesh.
 */
private fun collectVertexAttributeSummary(meshes: Array<Mesh>): VertexAttributeSummary {
    val vertexChannels = linkedSetOf<String>()
    val uvChannels = linkedSetOf<String>()
    var boneWeightChannelCount = 0

    meshes.forEach { mesh ->
        for (attribute in mesh.vertexAttributes) {
            when (attribute.usage) {
                VertexAttributes.Usage.Position -> vertexChannels += "Position"
                VertexAttributes.Usage.Normal -> vertexChannels += "Normal"
                VertexAttributes.Usage.ColorPacked,
                VertexAttributes.Usage.ColorUnpacked,
                -> vertexChannels += "Color"

                VertexAttributes.Usage.Tangent -> vertexChannels += "Tangent"
                VertexAttributes.Usage.BiNormal -> vertexChannels += "Binormal"
                VertexAttributes.Usage.TextureCoordinates -> {
                    val channel = "UV${attribute.unit}"
                    uvChannels += channel
                    vertexChannels += channel
                }

                VertexAttributes.Usage.BoneWeight -> {
                    val channel = "BoneWeight${attribute.unit}"
                    vertexChannels += channel
                    boneWeightChannelCount = maxOf(boneWeightChannelCount, attribute.unit + 1)
                }

                else -> vertexChannels += attribute.alias.ifBlank { "Usage${attribute.usage}" }
            }
        }
    }

    return VertexAttributeSummary(vertexChannels, uvChannels, boneWeightChannelCount)
}

/**
 * Formats the asset path extension as a readable model-format label.
 */
private fun modelFormat(path: String): String =
    when {
        path.endsWith(".glb", ignoreCase = true) || path.endsWith(".gltf", ignoreCase = true) -> "glTF"
        path.endsWith(".obj", ignoreCase = true) -> "OBJ"
        path.endsWith(".g3dj", ignoreCase = true) -> "G3DJ"
        path.endsWith(".g3db", ignoreCase = true) -> "G3DB"
        else -> "Model"
    }

private const val GDX_ASSET_SERVICE_TAG = "GdxAssetService"

/** Returns the total triangle count across all mesh parts in the model. */
private fun countTrianglesInModel(model: Model): Int = model.meshParts.sumOf(::countTrianglesInMeshPart)

/** Converts a mesh part's primitive topology into triangle count. */
private fun countTrianglesInMeshPart(meshPart: MeshPart): Int =
    when (meshPart.primitiveType) {
        GL20.GL_TRIANGLES -> meshPart.size / 3
        GL20.GL_TRIANGLE_STRIP, GL20.GL_TRIANGLE_FAN -> (meshPart.size - 2).coerceAtLeast(0)
        else -> 0
    }

private fun primitiveTypeLabel(primitiveType: Int): String =
    when (primitiveType) {
        GL20.GL_TRIANGLES -> "TRIANGLES"
        GL20.GL_TRIANGLE_STRIP -> "TRIANGLE_STRIP"
        GL20.GL_TRIANGLE_FAN -> "TRIANGLE_FAN"
        GL20.GL_LINES -> "LINES"
        GL20.GL_LINE_STRIP -> "LINE_STRIP"
        GL20.GL_POINTS -> "POINTS"
        else -> "GL_$primitiveType"
    }
