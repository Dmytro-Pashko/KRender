package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH = "materials/terrain_materials.json"
private const val MAX_MATERIAL_PREVIEW_RESOLUTION = 8192

/**
 * Keeps file-backed terrain asset entities ready for the shared dynamic terrain renderer.
 */
class TerrainAssetSyncSystem(
    private val logger: com.pashkd.krender.engine.api.Logger? = null,
    materialLibraryPath: String = DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH,
) : System() {
    private val sync = TerrainAssetRuntimeSync(logger, materialLibraryPath)

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        sync.update(world)
    }
}

/**
 * Reusable terrain asset loader used by both runtime worlds and embedded editor document worlds.
 */
class TerrainAssetRuntimeSync(
    private val logger: com.pashkd.krender.engine.api.Logger? = null,
    materialLibraryPath: String = DEFAULT_TERRAIN_MATERIAL_LIBRARY_PATH,
) {
    private val terrainPersistence = TerrainPersistence(logger)
    private val materialLibrary =
        TerrainMaterialLibrary(logger).also { library ->
            library.load(materialLibraryPath)
        }
    private val bakeService = TerrainMaterialBakeService(materialLibrary, logger)
    private val failedPaths = mutableSetOf<String>()

    fun update(world: SceneWorld) {
        world.query<TransformComponent, TerrainComponent>().forEach { entity ->
            if (!entity.active) return@forEach
            val component = entity.get<TerrainComponent>() ?: return@forEach
            val path =
                component.terrain.path
                    .trim()
                    .replace('\\', '/')
            if (path.isBlank()) return@forEach
            val renderer = entity.get<TerrainRendererComponent>()
            val previewMode = sceneTerrainPreviewMode(component.previewMode)
            val bakedTextureResolution = component.bakedTextureResolution.coerceIn(2, MAX_MATERIAL_PREVIEW_RESOLUTION)
            if (renderer?.isSyncedForSceneTerrain(path, previewMode, bakedTextureResolution) == true) {
                return@forEach
            }

            try {
                val data = terrainPersistence.load(path)
                val usesTexturePreview = previewMode == TerrainPreviewMode.MaterialTexture
                val mesh =
                    TerrainMeshBuilder.build(
                        data = data,
                        materialColorResolver = { null },
                        blendMode = TerrainLayerBlendMode.OrderedAlpha,
                        enableLayerColorPreview = !usesTexturePreview,
                    )
                val nextRenderer =
                    renderer ?: TerrainRendererComponent(
                        modelId = modelId(path),
                        material = Material(),
                    ).also(entity::add)
                nextRenderer.modelId = modelId(path)
                nextRenderer.meshRevision += 1L
                nextRenderer.model =
                    com.pashkd.krender.engine.api.DynamicModel(
                        id = nextRenderer.modelId,
                        mesh = mesh.toDynamicMesh(),
                        revision = nextRenderer.meshRevision,
                    )
                nextRenderer.vertexCount = mesh.vertexCount
                nextRenderer.triangleCount = mesh.triangleCount
                nextRenderer.previewMode = previewMode
                nextRenderer.previewResolution = if (usesTexturePreview) bakedTextureResolution else 0
                if (usesTexturePreview) {
                    val texture =
                        bakeService.bakeFinalSplatTexture(
                            terrain = data,
                            resolution = bakedTextureResolution,
                            textureId = "runtime:scene-terrain-preview:${nextRenderer.modelId}",
                            revision = nextRenderer.meshRevision * 31L + bakedTextureResolution,
                            blendMode = TerrainLayerBlendMode.OrderedAlpha,
                        )
                    nextRenderer.replacePreviewDiffuseTexture(texture)
                    nextRenderer.material =
                        Material(
                            baseColor = Color.white(),
                            diffuseTextureRef =
                                MaterialTextureRef(
                                    id = texture.id,
                                    channel = "diffuse",
                                    uvChannel = 0,
                                ),
                        )
                } else {
                    nextRenderer.replacePreviewDiffuseTexture(null)
                    nextRenderer.material = Material()
                }
                failedPaths.remove(path)
                logger?.info(TAG) {
                    "Loaded terrain asset '$path' for entityId=${entity.id} previewMode=$previewMode " +
                        "bakedTextureResolution=${if (usesTexturePreview) bakedTextureResolution else "<none>"}"
                }
            } catch (error: Exception) {
                if (failedPaths.add(path)) {
                    logger?.warn(TAG) { "Failed to load terrain asset '$path' for entityId=${entity.id}: ${error.message}" }
                }
            }
        }
    }

    private fun TerrainRendererComponent.isSyncedForSceneTerrain(
        path: String,
        previewMode: TerrainPreviewMode,
        bakedTextureResolution: Int,
    ): Boolean {
        if (model == null || modelId != modelId(path) || this.previewMode != previewMode) {
            return false
        }
        return if (previewMode == TerrainPreviewMode.MaterialTexture) {
            previewDiffuseTexture != null && previewResolution == bakedTextureResolution
        } else {
            previewDiffuseTexture == null
        }
    }

    private fun sceneTerrainPreviewMode(mode: TerrainPreviewMode): TerrainPreviewMode =
        if (mode == TerrainPreviewMode.MaterialTexture) {
            TerrainPreviewMode.MaterialTexture
        } else {
            TerrainPreviewMode.LayerColor
        }

    private fun modelId(path: String): String = "terrain_asset_" + path.replace(Regex("[^A-Za-z0-9_\\-]+"), "_")

    companion object {
        private const val TAG = "TerrainAssetRuntimeSync"
    }
}

/**
 * Shared terrain draw-command emission for runtime worlds and editor document worlds.
 */
object TerrainRenderCommands {
    fun submit(
        world: SceneWorld,
        submit: (DrawDynamicModel) -> Unit,
    ) {
        world.query<TransformComponent, TerrainRendererComponent>().forEach { entity ->
            if (!entity.active) return@forEach
            val terrainAsset = entity.get<TerrainComponent>()
            if (terrainAsset != null && !terrainAsset.visible) return@forEach
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            val model = renderer.model ?: return@forEach
            val textureForMaterial = renderer.finalSplatTexture ?: renderer.previewDiffuseTexture
            val material =
                if (textureForMaterial != null) {
                    renderer.material.copy(
                        baseColor = Color.white(),
                        diffuseTextureRef =
                            MaterialTextureRef(
                                id = textureForMaterial.id,
                                channel = "baseColor",
                                uvChannel = 0,
                            ),
                    )
                } else if (model.mesh.colors != null) {
                    renderer.material.copy(
                        baseColor = Color.white(),
                        diffuseTextureRef = null,
                    )
                } else {
                    renderer.material.copy(diffuseTextureRef = null)
                }
            submit(
                DrawDynamicModel(
                    entityId = entity.id,
                    model = model,
                    transform = transform.snapshot(),
                    material = material,
                    runtimeTextures = listOfNotNull(textureForMaterial),
                ),
            )
        }
    }
}

/**
 * Submits terrain dynamic mesh draw commands to the render pipeline.
 */
class TerrainRenderSystem : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        TerrainRenderCommands.submit(world, world.renderCommands::submit)
    }
}

/**
 * Camera controller for terrain editor and runtime terrain viewports.
 */
class TerrainCameraControllerSystem(
    private val input: InputService,
    private val viewportFocusProvider: (() -> Boolean)? = null,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val cameraEntity =
            world
                .query<TransformComponent, PerspectiveCameraComponent, TerrainCameraControllerComponent>()
                .firstOrNull() ?: return
        val transform = cameraEntity.get<TransformComponent>() ?: return
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return
        val controller = cameraEntity.get<TerrainCameraControllerComponent>() ?: return
        val lookAt = camera.lookAt ?: Vec3.zero().also { camera.lookAt = it }
        val snapshot = input.snapshot()
        if (viewportFocusProvider != null && !viewportFocusProvider.invoke() && snapshot.isCapturedByUI()) return
        val forward = horizontalForward(transform.position, lookAt)
        val right = Vec3(-forward.z, 0f, forward.x)

        val panX =
            when {
                snapshot.isDown(Key.A) -> -1f
                snapshot.isDown(Key.D) -> 1f
                else -> 0f
            }
        val panZ =
            when {
                snapshot.isDown(Key.W) -> 1f
                snapshot.isDown(Key.S) -> -1f
                else -> 0f
            }
        val panY =
            when {
                snapshot.isDown(Key.R) -> 1f
                snapshot.isDown(Key.F) -> -1f
                else -> 0f
            }

        if (panX != 0f || panY != 0f || panZ != 0f) {
            val speed = controller.panSpeed * dt
            val deltaX = (right.x * panX + forward.x * panZ) * speed
            val deltaY = panY * speed
            val deltaZ = (right.z * panX + forward.z * panZ) * speed
            transform.position.x += deltaX
            transform.position.y += deltaY
            transform.position.z += deltaZ
            lookAt.x += deltaX
            lookAt.y += deltaY
            lookAt.z += deltaZ
        }

        val rotationInput =
            when {
                snapshot.isDown(Key.Q) -> -1f
                snapshot.isDown(Key.E) -> 1f
                else -> 0f
            }

        if (rotationInput != 0f) {
            rotateAroundLookAt(transform, lookAt, controller.rotationSpeedDegrees * rotationInput * dt)
        }
    }

    private fun rotateAroundLookAt(
        transform: TransformComponent,
        lookAt: Vec3,
        deltaDegrees: Float,
    ) {
        val offsetX = transform.position.x - lookAt.x
        val offsetZ = transform.position.z - lookAt.z
        val radius =
            sqrt(offsetX * offsetX + offsetZ * offsetZ)
                .coerceIn(1e-4f, Float.MAX_VALUE)
        val currentAngle = atan2(offsetZ, offsetX)
        val nextAngle = currentAngle + Math.toRadians(deltaDegrees.toDouble()).toFloat()

        transform.position.x = lookAt.x + cos(nextAngle) * radius
        transform.position.z = lookAt.z + sin(nextAngle) * radius
    }

    private fun horizontalForward(
        position: Vec3,
        lookAt: Vec3,
    ): Vec3 {
        val x = lookAt.x - position.x
        val z = lookAt.z - position.z
        val length = sqrt(x * x + z * z)
        if (length <= 1e-6f) return Vec3(0f, 0f, -1f)
        return Vec3(x / length, 0f, z / length)
    }
}
