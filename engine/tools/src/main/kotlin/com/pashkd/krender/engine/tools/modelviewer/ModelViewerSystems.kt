package com.pashkd.krender.engine.tools.modelviewer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.tools.sceneeditor.SceneEditorLocalBounds
import com.pashkd.krender.engine.tools.sceneeditor.transformedBoundsCorners

/**
 * Syncs ModelViewer UI state with assets, render settings, and scene actions.
 */
class ModelViewerSystem(
    private val input: InputService,
    private val assets: AssetService,
    private val logger: Logger,
    private val state: ModelViewerState,
    private val onExitRequested: () -> Unit,
) : System() {
    private var loggedModelPath: String? = null
    private var frameIndex = 0L
    private var lastAssetLoaded: Boolean? = null
    private var lastLoadingStatus: String? = null
    private var lastDisplayMode: ModelViewerDisplayMode? = null
    private var lastDebugMode: MaterialDebugMode? = null
    private var lastDebugWarning: String? = null
    private var lastMetadataAvailable: Boolean? = null
    private var lastErrorMessage: String? = null
    private var missingModelEntityLogged = false

    override fun onAdded(world: SceneWorld) {
        logger.debug(TAG) {
            "ModelViewerSystem added model='${state.model.path}' modelEntityId=${state.modelEntityId} entities=${world.all().size}"
        }
    }

    /**
     * Updates render toggles, status text, metadata, and queued scene actions.
     */
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        frameIndex += 1
        try {
            updateInternal(world, dt)
        } catch (error: Exception) {
            logger.error(TAG, error) {
                "ModelViewerSystem update failed frame=$frameIndex model='${state.model.path}' " +
                    "loaded=${state.assetLoaded} status='${state.loadingStatus}' entity=${state.modelEntityId}: ${error.message}"
            }
            throw error
        }
    }

    private fun updateInternal(
        world: SceneWorld,
        dt: Float,
    ) {
        if (frameIndex == 1L) {
            logger.debug(TAG) {
                "ModelViewer first update dt=${"%.4f".format(dt)} model='${state.model.path}' entities=${world.all().size}"
            }
        }
        val snapshot = input.snapshot()

        if (!snapshot.uiCapturesKeyboard && snapshot.wasPressed(Key.F1)) {
            val modes = ModelViewerDisplayMode.entries
            val nextIndex = (modes.indexOf(state.displayMode) + 1).floorMod(modes.size)
            state.displayMode = modes[nextIndex]
            logger.info(TAG) { "ModelViewer display mode toggled by F1 mode=${state.displayMode}" }
        }

        syncStatus(world)
        syncSelectionBounds()
        syncDebugState()
        syncAmbientLight(world)
        logLoadedModelDetails()
        handleRequests()
    }

    /**
     * Mirrors asset and render state into the shared UI state object.
     */
    private fun syncStatus(world: SceneWorld) {
        state.assetProgress = assets.progress()
        state.assetLoaded = assets.isLoaded(state.model)
        val loadFailure = assets.loadFailure(state.model)
        state.loadingStatus =
            when {
                state.assetLoaded -> "Loaded"
                loadFailure != null -> "Failed"
                else -> "Loading ${"%.0f".format(state.assetProgress * 100f)}%"
            }
        state.modelInfo = if (state.assetLoaded) assets.modelInfo(state.model) else null
        state.errorMessage =
            when {
                loadFailure != null -> loadFailure
                state.assetLoaded && state.modelInfo == null -> "Model metadata is unavailable for this asset."
                else -> null
            }
        logStatusChanges()

        val wireframe = state.displayMode == ModelViewerDisplayMode.Wireframe
        val wireframeOverlay = state.displayMode == ModelViewerDisplayMode.ShadedWireframe
        val modelComponent =
            state.modelEntityId
                ?.let(world::getEntity)
                ?.get<ModelComponent>()
        if (modelComponent == null) {
            if (!missingModelEntityLogged) {
                logger.warn(TAG) {
                    "ModelViewer model component is unavailable modelEntityId=${state.modelEntityId} entities=${world.all().size}"
                }
                missingModelEntityLogged = true
            }
            return
        }
        if (
            modelComponent.material.wireframe != wireframe ||
            modelComponent.material.wireframeOverlay != wireframeOverlay
        ) {
            modelComponent.material =
                modelComponent.material.copy(
                    wireframe = wireframe,
                    wireframeOverlay = wireframeOverlay,
                )
            if (lastDisplayMode != state.displayMode) {
                logger.info(TAG) {
                    "ModelViewer material display mode applied mode=${state.displayMode} " +
                        "wireframe=$wireframe overlay=$wireframeOverlay entity=${state.modelEntityId}"
                }
            }
            lastDisplayMode = state.displayMode
        } else if (lastDisplayMode != state.displayMode) {
            logger.info(TAG) {
                "ModelViewer display mode active mode=${state.displayMode} wireframe=$wireframe entity=${state.modelEntityId}"
            }
            lastDisplayMode = state.displayMode
        }
    }

    private fun logStatusChanges() {
        if (lastAssetLoaded != state.assetLoaded || lastLoadingStatus != state.loadingStatus) {
            logger.info(TAG) {
                "ModelViewer asset status model='${state.model.path}' loaded=${state.assetLoaded} " +
                    "progress=${"%.3f".format(state.assetProgress)} status='${state.loadingStatus}'"
            }
            lastAssetLoaded = state.assetLoaded
            lastLoadingStatus = state.loadingStatus
        }
        val metadataAvailable = state.modelInfo != null
        if (lastMetadataAvailable != metadataAvailable) {
            logger.info(TAG) {
                "ModelViewer metadata availability model='${state.model.path}' available=$metadataAvailable"
            }
            lastMetadataAvailable = metadataAvailable
        }
        if (lastErrorMessage != state.errorMessage) {
            state.errorMessage?.let { error ->
                logger.warn(TAG) { "ModelViewer state error: $error" }
            }
            lastErrorMessage = state.errorMessage
        }
    }

    /**
     * Clears stale panel selections when a new metadata snapshot has fewer rows.
     */
    private fun syncSelectionBounds() {
        val info = state.modelInfo ?: return
        state.selectedMeshPartIndex = state.selectedMeshPartIndex?.takeIf { it in info.meshParts.indices }
        state.selectedMaterialIndex = state.selectedMaterialIndex?.takeIf { it in info.materials.indices }
        val textureChannels =
            info.materials
                .flatMap { material -> material.textureSlots }
                .mapTo(linkedSetOf()) { slot -> slot.channel }
        state.selectedTextureChannel = state.selectedTextureChannel?.takeIf { channel -> channel in textureChannels }
    }

    /**
     * Keeps debug-channel selection and warnings aligned with loaded model metadata.
     */
    private fun syncDebugState() {
        val effectiveDebugMode = if (state.uvCheckerEnabled) MaterialDebugMode.UvChecker else state.debugMode
        if (lastDebugMode != effectiveDebugMode) {
            logger.info(TAG) {
                "ModelViewer debug mode changed mode=$effectiveDebugMode " +
                    "selectedMaterial=${state.selectedMaterialIndex?.toString() ?: "all"} " +
                    "uvChecker='${state.uvCheckerTexturePath}'"
            }
            lastDebugMode = effectiveDebugMode
        }

        val selectedMaterialIndex: Int? = null
        if (isModelViewerTextureDebugMode(effectiveDebugMode)) {
            val channels =
                matchingModelViewerTextureSlots(
                    state.modelInfo,
                    effectiveDebugMode,
                    selectedMaterialIndex,
                    selectedTextureChannel = null,
                ).map { slot -> slot.channel }.distinct()
            if (channels.isNotEmpty() && state.selectedTextureChannel !in channels) {
                state.selectedTextureChannel = channels.first()
            }
        }

        state.debugWarning = debugWarningFor(effectiveDebugMode, selectedMaterialIndex)
        state.pbrWarning = pbrWarningFor()
        val warning = state.debugWarning
        if (warning != null && warning != lastDebugWarning) {
            logger.warn(TAG) { warning }
        }
        lastDebugWarning = warning
    }

    private fun debugWarningFor(
        mode: MaterialDebugMode,
        selectedMaterialIndex: Int?,
    ): String? {
        val info = state.modelInfo ?: return null
        return when {
            mode == MaterialDebugMode.None -> null
            mode == MaterialDebugMode.UvChecker && info.uvChannels.isEmpty() ->
                "ModelViewer debug warning: model has no UV channels for UV checker."

            mode == MaterialDebugMode.UvChecker && !assets.isLoaded(AssetRef.texture(state.uvCheckerTexturePath)) ->
                "ModelViewer debug warning: UV checker texture '${state.uvCheckerTexturePath}' is missing or not loaded."

            isModelViewerTextureDebugMode(mode) &&
                resolvedModelViewerDebugTextureRefs(
                    info = info,
                    mode = mode,
                    selectedMaterialIndex = selectedMaterialIndex,
                    selectedTextureChannel = state.selectedTextureChannel,
                ).isEmpty() ->
                missingTextureWarning(mode, selectedMaterialIndex)

            else -> null
        }
    }

    private fun missingTextureWarning(
        mode: MaterialDebugMode,
        selectedMaterialIndex: Int?,
    ): String {
        val material = selectedMaterialIndex?.let { "Material #$it" } ?: "Selected material"
        return when (mode) {
            MaterialDebugMode.Roughness,
            MaterialDebugMode.Metallic,
            ->
                "$material has no metallicRoughnessTexture. " +
                    "Roughness and metallic are usually stored in the same metallicRoughnessTexture."

            MaterialDebugMode.MetallicRoughnessPacked ->
                "$material has no metallicRoughnessTexture."

            MaterialDebugMode.BaseColor ->
                "$material has no baseColorTexture."

            MaterialDebugMode.Normal ->
                "$material has no normalTexture."

            MaterialDebugMode.Emission ->
                "$material has no emissiveTexture."

            MaterialDebugMode.Occlusion ->
                "$material has no occlusionTexture."

            MaterialDebugMode.Alpha ->
                "$material has no alpha-capable texture."

            MaterialDebugMode.None,
            MaterialDebugMode.UvChecker,
            -> "Texture channel $mode is unavailable for $material."
        }
    }

    private fun pbrWarningFor(): String? {
        if (state.rendererMode != ModelViewerRendererMode.Pbr) return null
        return when {
            !state.model.path.isGltfPath() ->
                "PBR preview is currently available only for glTF/glb models."

            state.assetLoaded && state.modelInfo == null ->
                "PBR preview unavailable: model metadata is unavailable."

            else -> null
        }
    }

    private fun syncAmbientLight(world: SceneWorld) {
        val ambientLight =
            state.ambientLightEntityId
                ?.let(world::getEntity)
                ?.get<LightComponent>()
                ?: return
        if (ambientLight.type != LightType.Ambient) return
        val ambientIntensity = state.ambientLightIntensity.coerceAtLeast(0f)
        state.ambientLightIntensity = ambientIntensity
        val effectiveIntensity = if (state.rendererMode == ModelViewerRendererMode.LibGdx) ambientIntensity else 0f
        if (ambientLight.intensity != effectiveIntensity) {
            ambientLight.intensity = effectiveIntensity
        }
        ambientLight.color.copyFrom(state.legacyAmbientLightColor)
        syncLegacyDirectionalLight(world)
    }

    private fun syncLegacyDirectionalLight(world: SceneWorld) {
        val light =
            state.legacyDirectionalLightEntityId
                ?.let(world::getEntity)
                ?.get<LightComponent>()
                ?: return
        light.intensity =
            if (state.rendererMode == ModelViewerRendererMode.LibGdx && state.legacyDirectionalLightEnabled) {
                state.legacyDirectionalLightIntensity.coerceAtLeast(0f)
            } else {
                0f
            }
        light.color.copyFrom(state.legacyDirectionalLightColor)
        val direction =
            modelViewerLightDirection(
                state.legacyDirectionalLightYawDegrees,
                state.legacyDirectionalLightPitchDegrees,
            )
        light.direction.set(direction.x, direction.y, direction.z)
    }

    /**
     * Emits one structured metadata dump when the active model finishes loading.
     */
    private fun logLoadedModelDetails() {
        if (!state.assetLoaded) {
            loggedModelPath = null
            return
        }
        if (loggedModelPath == state.model.path) return

        val info = state.modelInfo
        if (info == null) {
            logger.warn(TAG) { "Model '${state.model.path}' is loaded but no metadata snapshot is available." }
            loggedModelPath = state.model.path
            return
        }

        logger.info(TAG) {
            "Loaded model '${info.path}' (${info.format}) size=${formatSize(info)} " +
                "nodes=${info.nodeCount} meshes=${info.meshCount} meshParts=${info.meshPartCount} materials=${info.materialCount}"
        }
        logger.info(TAG) {
            "Geometry: vertices=${info.vertexCount} triangles=${info.triangleCount} " +
                "channels=${formatList(info.vertexChannels)} uv=${formatList(info.uvChannels)}"
        }
        logger.info(TAG) {
            "Textures: unique=${info.textureCount} slots=${info.textureSlotCount} channels=${formatList(info.textureChannels)}"
        }
        logger.info(TAG) {
            "Rig: skeleton=${if (info.hasSkeleton) "yes" else "no"} bones=${info.boneCount} " +
                "weights=${info.boneWeightChannelCount} animations=${info.animationCount} names=${formatList(info.animationNames)}"
        }
        loggedModelPath = state.model.path
    }

    /**
     * Executes deferred actions requested by the UI.
     */
    private fun handleRequests() {
        if (state.reloadRequested) {
            state.reloadRequested = false
            assets.unload(state.model)
            assets.queue(state.model)
            state.assetLoaded = false
            state.assetProgress = 0f
            state.loadingStatus = "Reloading"
            state.errorMessage = null
            state.modelInfo = null
            state.statusMessage = "Reloading model from disk..."
            state.selectedMeshPartIndex = null
            state.selectedMaterialIndex = null
            state.selectedTextureChannel = null
            loggedModelPath = null
            lastAssetLoaded = null
            lastLoadingStatus = null
            lastMetadataAvailable = null
            lastErrorMessage = null
            logger.info(TAG) { "ModelViewer re-queued model='${state.model.path}' for reload" }
        }
        if (state.exitRequested) {
            logger.warn(TAG) { "ModelViewer exit requested from UI state model='${state.model.path}'" }
            state.exitRequested = false
            onExitRequested()
        }
    }

    private fun formatSize(info: ModelAssetInfo): String = info.size?.let(::formatSize) ?: "unknown"

    private fun formatSize(size: Vec3): String = "%.2f x %.2f x %.2f".format(size.x, size.y, size.z)

    private fun formatList(values: List<String>): String = values.ifEmpty { listOf("none") }.joinToString(", ")

    companion object {
        private const val TAG = "ModelViewerSystem"
    }
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun String.isGltfPath(): Boolean = endsWith(".gltf", ignoreCase = true) || endsWith(".glb", ignoreCase = true)

/**
 * Emits ModelViewer viewport guide draw commands from runtime display state.
 */
class ModelViewerViewportGuideSystem(
    private val state: ModelViewerState,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        val halfExtentCells = state.gridHalfExtentCells.coerceAtLeast(1)
        val cellSize = state.gridCellSize.coerceAtLeast(MinCellSize)
        if (state.showGrid) {
            world.renderCommands.submit(
                DrawWorldGrid(
                    halfExtentCells = halfExtentCells,
                    cellSize = cellSize,
                ),
            )
        }
        if (state.showAxes) {
            world.renderCommands.submit(DrawWorldAxes(length = halfExtentCells * cellSize))
        }
    }

    companion object {
        private const val MinCellSize = 0.01f
    }
}

/**
 * Emits the inspected model draw command with ModelViewer-specific mesh-part isolation.
 */
class ModelViewerModelRenderSystem(
    private val state: ModelViewerState,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        val entity = state.modelEntityId?.let(world::getEntity) ?: return
        val model = entity.get<ModelComponent>() ?: return
        val transform = entity.get<TransformComponent>() ?: return
        val visibleMeshPartIndices =
            state.selectedMeshPartIndex
                ?.takeIf { state.isolateSelectedMeshPart }
                ?.let(::setOf)

        val debugView = state.materialDebugView()
        world.renderCommands.submit(
            DrawModel(
                entityId = entity.id,
                model = model.model,
                transform = transform.snapshot(),
                material = model.material,
                visibleMeshPartIndices = visibleMeshPartIndices,
                debugView = debugView,
                pbrPreview = state.pbrPreviewView(debugView),
            ),
        )
    }

    private fun ModelViewerState.materialDebugView(): MaterialDebugView? {
        val mode = if (uvCheckerEnabled) MaterialDebugMode.UvChecker else debugMode
        if (mode == MaterialDebugMode.None) return null
        return MaterialDebugView(
            mode = mode,
            selectedMaterialIndex = null,
            selectedMaterialId = null,
            selectedTextureChannel = selectedTextureChannel,
            textureRefs =
                resolvedModelViewerDebugTextureRefs(
                    info = modelInfo,
                    mode = mode,
                    selectedMaterialIndex = null,
                    selectedTextureChannel = selectedTextureChannel,
                ),
            uvCheckerTexture =
                MaterialTextureRef(
                    id = uvCheckerTexturePath,
                    channel = "uvChecker",
                    uvChannel = uvCheckerUvChannel,
                ),
            uvChannel = uvCheckerUvChannel,
            uvScale = uvCheckerScale.coerceAtLeast(MinUvCheckerScale),
            culling = debugCullingMode,
        )
    }

    private fun ModelViewerState.pbrPreviewView(debugView: MaterialDebugView?): PbrPreviewView? {
        if (rendererMode != ModelViewerRendererMode.Pbr) return null
        return PbrPreviewView(
            enabled = debugView?.active != true,
            environmentPreset = pbrEnvironmentPreset,
            exposure = pbrExposure.coerceAtLeast(0f),
            showSkybox = pbrShowSkybox,
            environmentIntensity = pbrEnvironmentIntensity.coerceAtLeast(0f),
            environmentRotationDegrees = pbrEnvironmentRotationDegrees,
            toneMapping = pbrToneMapping,
            gammaCorrection = pbrGammaCorrection,
            srgbTextures = pbrSrgbTextures,
            directionalLightEnabled = pbrDirectionalLightEnabled,
            directionalLightIntensity = pbrDirectionalLightIntensity.coerceAtLeast(0f),
            directionalLightColor = pbrDirectionalLightColor.copy(),
            directionalLightYawDegrees = pbrDirectionalLightYawDegrees,
            directionalLightPitchDegrees = pbrDirectionalLightPitchDegrees,
        )
    }

    companion object {
        private const val MinUvCheckerScale = 0.01f
    }
}

private fun modelViewerLightDirection(
    yawDegrees: Float,
    pitchDegrees: Float,
): Vec3 {
    val yaw = Math.toRadians(yawDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.toDouble())
    val x = (kotlin.math.cos(pitch) * kotlin.math.cos(yaw)).toFloat()
    val y = kotlin.math.sin(pitch).toFloat()
    val z = (kotlin.math.cos(pitch) * kotlin.math.sin(yaw)).toFloat()
    val length = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001f)
    return Vec3(-x / length, -y / length, -z / length)
}

private fun Color.copyFrom(source: Color) {
    r = source.r
    g = source.g
    b = source.b
    a = source.a
}

/**
 * Draws the inspected model's cached local bounds as a viewport overlay.
 */
class ModelViewerBoundingBoxSystem(
    private val state: ModelViewerState,
    private val assets: AssetService,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        if (!state.showBoundingBox || !state.assetLoaded) return
        val entity = state.modelEntityId?.let(world::getEntity) ?: return
        val transform = entity.get<TransformComponent>() ?: return
        val bounds = assets.modelBounds(state.model)?.let { SceneEditorLocalBounds(it.min, it.max) } ?: return
        val corners = transformedBoundsCorners(bounds, transform)
        BoxEdges.forEach { (fromIndex, toIndex) ->
            world.renderCommands.submit(
                DrawLine(
                    from = corners[fromIndex],
                    to = corners[toIndex],
                    color = BoundingBoxColor,
                ),
            )
        }
    }

    companion object {
        private val BoundingBoxColor = Color(1f, 0.85f, 0.1f, 1f)
        private val BoxEdges =
            listOf(
                0 to 1,
                1 to 2,
                2 to 3,
                3 to 0,
                4 to 5,
                5 to 6,
                6 to 7,
                7 to 4,
                0 to 4,
                1 to 5,
                2 to 6,
                3 to 7,
            )
    }
}
