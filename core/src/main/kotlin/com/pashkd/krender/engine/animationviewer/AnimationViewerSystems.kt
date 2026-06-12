package com.pashkd.krender.engine.animationviewer

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.math.transformLocalPoint
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.sceneeditor.SceneEditorLocalBounds
import com.pashkd.krender.engine.sceneeditor.transformedBoundsCorners

/**
 * Syncs Animation Viewer UI state with assets, playback, render settings, and scene actions.
 */
class AnimationViewerSystem(
    private val assets: AssetService,
    private val logger: Logger,
    private val state: AnimationViewerState,
    private val onExitRequested: () -> Unit,
) : System() {
    private var frameIndex = 0L
    private var loggedModelPath: String? = null
    private var lastAssetLoaded: Boolean? = null
    private var lastSelectionName: String? = null
    private var lastPlaying: Boolean? = null
    private var lastLoop: Boolean? = null
    private var lastViewMode: AnimationViewerViewMode? = null
    private var lastAnimationWarning: String? = null
    private var lastSkeletonWarning: String? = null
    private var missingModelEntityLogged = false

    override fun onAdded(world: SceneWorld) {
        logger.debug(TAG) {
            "AnimationViewerSystem added model='${state.model.path}' modelEntityId=${state.modelEntityId} entities=${world.all().size}"
        }
    }

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        frameIndex += 1
        try {
            updateInternal(world, dt)
        } catch (error: Exception) {
            logger.error(TAG, error) {
                "AnimationViewerSystem update failed frame=$frameIndex model='${state.model.path}' loaded=${state.assetLoaded} " +
                    "selection='${state.selectedAnimationName}' time=${"%.3f".format(state.currentTimeSeconds)}: ${error.message}"
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
                "AnimationViewer first update dt=${"%.4f".format(dt)} model='${state.model.path}' entities=${world.all().size}"
            }
        }
        syncStatus(world)
        syncSelectedBone()
        syncAnimationSelection()
        updatePlayback(dt)
        syncPreviewSupport()
        syncSampledSkeletonPose()
        syncWarnings()
        syncAmbientLight(world)
        logLoadedModelDetails()
        handleRequests()
        logStateChanges()
    }

    private fun syncStatus(world: SceneWorld) {
        state.assetProgress = assets.progress()
        state.assetLoaded = assets.isLoaded(state.model)
        state.loadingStatus =
            if (state.assetLoaded) {
                "Loaded"
            } else {
                "Loading ${"%.0f".format(state.assetProgress * 100f)}%"
            }
        state.modelInfo = if (state.assetLoaded) assets.modelInfo(state.model) else null
        state.skeletonInfo = if (state.assetLoaded) assets.modelSkeleton(state.model) else null
        state.errorMessage =
            if (state.assetLoaded && state.modelInfo == null) {
                "Model metadata is unavailable for this asset."
            } else {
                null
            }
        if (!state.assetLoaded) {
            state.animationPreviewStatus = AnimationPreviewStatus.Unsupported
            state.skeletonPreviewStatus = SkeletonPreviewStatus.Unsupported
            clearSampledSkeletonPose()
        }
        applyWireframeMaterial(world)
        if (lastAssetLoaded != state.assetLoaded) {
            logger.info(TAG) {
                "AnimationViewer asset status model='${state.model.path}' loaded=${state.assetLoaded} progress=${
                    "%.3f".format(
                        state.assetProgress,
                    )
                }"
            }
            lastAssetLoaded = state.assetLoaded
        }
    }

    private fun applyWireframeMaterial(world: SceneWorld) {
        val modelComponent =
            state.modelEntityId
                ?.let(world::getEntity)
                ?.get<ModelComponent>()
        if (modelComponent == null) {
            if (!missingModelEntityLogged) {
                logger.warn(TAG) {
                    "AnimationViewer model component is unavailable modelEntityId=${state.modelEntityId} entities=${world.all().size}"
                }
                missingModelEntityLogged = true
            }
            return
        }
        missingModelEntityLogged = false
        if (modelComponent.material.wireframe != state.wireframe || modelComponent.material.wireframeOverlay) {
            modelComponent.material =
                modelComponent.material.copy(
                    wireframe = state.wireframe,
                    wireframeOverlay = false,
                )
        }
    }

    private fun syncSelectedBone() {
        val bones = state.skeletonInfo?.bones.orEmpty()
        if (state.hoveredBoneIndex != null && bones.none { bone -> bone.index == state.hoveredBoneIndex }) {
            state.hoveredBoneIndex = null
        }

        val selected = state.selectedBoneIndex ?: return
        val selectedBone = bones.firstOrNull { bone -> bone.index == selected }
        if (selectedBone == null) {
            state.selectedBoneIndex = null
            state.selectedBoneName = null
            state.hoveredBoneIndex = null
            state.statusMessage = "Selected bone cleared because skeleton changed."
            logger.info(TAG) {
                "AnimationViewer bone selection cleared because index=$selected is unavailable for model='${state.model.path}'"
            }
            return
        }
        state.selectedBoneName = selectedBone.name?.takeIf(String::isNotBlank) ?: "Bone #${selectedBone.index}"
    }

    private fun syncAnimationSelection() {
        val info = state.modelInfo
        val animations = info?.animations.orEmpty()
        state.animationNames =
            if (animations.isNotEmpty()) {
                animations.map { animation -> animation.name }
            } else {
                info?.animationNames.orEmpty()
            }

        val previousSelection = state.selectedAnimationName
        state.selectedAnimationIndex = state.selectedAnimationName
            ?.let(state.animationNames::indexOf)
            ?.takeIf { it >= 0 }
            ?: state.selectedAnimationIndex?.takeIf { it in state.animationNames.indices }

        state.selectedAnimationName = state.selectedAnimationIndex?.let(state.animationNames::get)
        state.durationSeconds =
            state.selectedAnimationName?.let { selected ->
                info?.animations?.firstOrNull { animation -> animation.name == selected }?.durationSeconds
            }
        state.currentTimeSeconds = clampAnimationTime(state.currentTimeSeconds, state.durationSeconds)

        if (previousSelection != null && state.selectedAnimationName == null) {
            state.currentTimeSeconds = 0f
            state.isPlaying = false
            state.statusMessage = "Selected animation is no longer available."
        }
    }

    private fun updatePlayback(dt: Float) {
        val animationName = state.selectedAnimationName
        if (!state.isPlaying || animationName.isNullOrBlank()) return
        val speed = state.playbackSpeed.coerceIn(MinPlaybackSpeed, MaxPlaybackSpeed)
        state.currentTimeSeconds = clampAnimationTime(state.currentTimeSeconds + dt * speed, state.durationSeconds)
        val duration = state.durationSeconds
        if (duration == null || duration <= 0f) {
            if (state.currentTimeSeconds < state.unknownDurationPreviewWindowSeconds) return
            state.currentTimeSeconds = state.unknownDurationPreviewWindowSeconds
            state.isPlaying = false
            state.statusMessage = "Playback reached the unknown-duration preview limit."
            logger.info(TAG) {
                "AnimationViewer playback reached unknown-duration preview limit animation='$animationName'"
            }
            return
        }
        if (state.currentTimeSeconds < duration) return

        if (state.loop) {
            state.currentTimeSeconds = if (duration > 0f) state.currentTimeSeconds % duration else 0f
        } else {
            state.currentTimeSeconds = duration
            state.isPlaying = false
            state.statusMessage = "Playback reached the end of '$animationName'."
            logger.info(TAG) { "AnimationViewer playback reached end animation='$animationName'" }
        }
    }

    private fun syncPreviewSupport() {
        state.animationPreviewStatus =
            when {
                !state.assetLoaded || !state.animationMetadataAvailable -> AnimationPreviewStatus.Unsupported
                state.selectedAnimationName.isNullOrBlank() -> AnimationPreviewStatus.MetadataOnly
                state.selectedAnimationName !in state.animationNames -> AnimationPreviewStatus.MetadataOnly
                state.viewMode == AnimationViewerViewMode.Skeleton -> AnimationPreviewStatus.MetadataOnly
                else -> AnimationPreviewStatus.PreviewRequested
            }
    }

    private fun syncSampledSkeletonPose() {
        if (!state.assetLoaded) {
            state.skeletonPreviewStatus = SkeletonPreviewStatus.Unsupported
            clearSampledSkeletonPose()
            return
        }
        if (!state.hasSkeletonData) {
            state.skeletonPreviewStatus = SkeletonPreviewStatus.Unsupported
            clearSampledSkeletonPose()
            return
        }
        if (state.viewMode == AnimationViewerViewMode.Model) {
            state.skeletonPreviewStatus = SkeletonPreviewStatus.Inactive
            clearSampledSkeletonPose()
            return
        }

        val poses =
            assets.modelSkeletonPose(
                asset = state.model,
                animationName = state.selectedAnimationName,
                timeSeconds = state.currentTimeSeconds,
                loop = state.canLoopSelectedAnimation && state.loop,
            )
        state.sampledSkeletonPose = poses
        state.sampledSkeletonPoseAnimationName = state.selectedAnimationName
        state.sampledSkeletonPoseTimeSeconds = state.currentTimeSeconds
        state.skeletonPreviewStatus =
            if (poses.isNotEmpty()) {
                SkeletonPreviewStatus.PreviewAvailable
            } else {
                SkeletonPreviewStatus.MetadataOnly
            }
    }

    private fun clearSampledSkeletonPose() {
        state.sampledSkeletonPose = emptyList()
        state.sampledSkeletonPoseAnimationName = null
        state.sampledSkeletonPoseTimeSeconds = 0f
    }

    private fun syncWarnings() {
        state.animationWarning =
            when {
                state.selectedAnimationName != null && !state.hasKnownSelectedAnimationDuration ->
                    "Unknown animation duration. Preview is limited to 10.000 s."

                else -> null
            }
        state.skeletonWarning =
            when (state.skeletonPreviewStatus) {
                SkeletonPreviewStatus.Inactive,
                SkeletonPreviewStatus.PreviewAvailable,
                    -> null

                SkeletonPreviewStatus.Unsupported -> "Skeleton metadata is unavailable for this model/backend."
                SkeletonPreviewStatus.MetadataOnly -> "Skeleton pose preview is unavailable for this model/backend."
            }
    }

    private fun syncAmbientLight(world: SceneWorld) {
        val ambientLight =
            state.ambientLightEntityId
                ?.let(world::getEntity)
                ?.get<LightComponent>()
                ?: return
        if (ambientLight.type != LightType.Ambient) return
        val intensity = state.ambientLightIntensity.coerceAtLeast(0f)
        state.ambientLightIntensity = intensity
        if (ambientLight.intensity != intensity) {
            ambientLight.intensity = intensity
        }
    }

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
            "Loaded animation-viewer model '${info.path}' (${info.format}) nodes=${info.nodeCount} meshes=${info.meshCount} " +
                "materials=${info.materialCount} animations=${info.animationCount} skeleton=${info.hasSkeleton} bones=${info.boneCount}"
        }
        loggedModelPath = state.model.path
    }

    private fun handleRequests() {
        if (state.exitRequested) {
            logger.warn(TAG) { "AnimationViewer exit requested from UI state model='${state.model.path}'" }
            state.exitRequested = false
            onExitRequested()
        }
    }

    private fun logStateChanges() {
        if (lastSelectionName != state.selectedAnimationName) {
            state.selectedAnimationName?.let { selected ->
                logger.info(TAG) {
                    "AnimationViewer animation selected name='$selected' duration=${state.durationSeconds}"
                }
            }
            lastSelectionName = state.selectedAnimationName
        }
        if (lastPlaying != state.isPlaying) {
            logger.info(TAG) {
                "AnimationViewer playing=${state.isPlaying} animation='${state.selectedAnimationName}' time=${
                    "%.3f".format(
                        state.currentTimeSeconds,
                    )
                }"
            }
            lastPlaying = state.isPlaying
        }
        if (lastLoop != state.loop) {
            logger.info(TAG) { "AnimationViewer loop=${state.loop}" }
            lastLoop = state.loop
        }
        if (lastViewMode != state.viewMode) {
            logger.info(TAG) { "AnimationViewer viewMode=${state.viewMode}" }
            lastViewMode = state.viewMode
        }
        if (lastAnimationWarning != state.animationWarning) {
            state.animationWarning?.let { warning -> logger.warn(TAG) { warning } }
            lastAnimationWarning = state.animationWarning
        }
        if (lastSkeletonWarning != state.skeletonWarning) {
            state.skeletonWarning?.let { warning -> logger.warn(TAG) { warning } }
            lastSkeletonWarning = state.skeletonWarning
        }
    }

    companion object {
        private const val TAG = "AnimationViewerSystem"
        private const val MinPlaybackSpeed = 0f
        private const val MaxPlaybackSpeed = 4f
    }
}

/**
 * Emits Animation Viewer viewport guide draw commands from runtime display state.
 */
class AnimationViewerViewportGuideSystem(
    private val state: AnimationViewerState,
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
 * Draws the inspected model's cached local bounds as a viewport overlay.
 */
class AnimationViewerBoundingBoxSystem(
    private val state: AnimationViewerState,
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

/**
 * Emits the inspected model draw command with Animation Viewer playback state.
 */
class AnimationViewerModelRenderSystem(
    private val state: AnimationViewerState,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        if (state.viewMode == AnimationViewerViewMode.Skeleton) return
        val entity = state.modelEntityId?.let(world::getEntity) ?: return
        val model = entity.get<ModelComponent>() ?: return
        val transform = entity.get<TransformComponent>() ?: return
        world.renderCommands.submit(
            DrawModel(
                entityId = entity.id,
                model = model.model,
                transform = transform.snapshot(),
                material = model.material,
                animation = state.animationPreview(),
            ),
        )
    }

    private fun AnimationViewerState.animationPreview(): AnimationPlaybackView? {
        val animationName = selectedAnimationName?.takeIf(String::isNotBlank) ?: return null
        return AnimationPlaybackView(
            animationName = animationName,
            timeSeconds = currentTimeSeconds,
            loop = loop && canLoopSelectedAnimation,
            playing = isPlaying,
            playbackSpeed = playbackSpeed,
        )
    }
}

/**
 * Draws a backend-neutral skeleton overlay using bone-parent line segments.
 */
class AnimationViewerSkeletonRenderSystem(
    private val state: AnimationViewerState,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        if (state.viewMode == AnimationViewerViewMode.Model || !state.assetLoaded) return
        if (!state.hasSkeletonData || state.sampledSkeletonPose.isEmpty()) return
        val entity = state.modelEntityId?.let(world::getEntity) ?: return
        val transform = entity.get<TransformComponent>() ?: return
        val poses = state.sampledSkeletonPose
        val poseByIndex = poses.associateBy { pose -> pose.boneIndex }
        val hoveredBoneIndex = state.hoveredBoneIndex
        val selectedBoneIndex = state.selectedBoneIndex
        val connectedBoneIndices = if (state.highlightConnectedBones) state.connectedBoneIndices() else emptySet()
        val jointHalfSize = state.skeletonJointSize.coerceAtLeast(MinJointHalfSize)

        poses.forEach { pose ->
            if (state.showSkeletonJoints) {
                submitJoint(
                    world = world,
                    transform = transform,
                    position = pose.worldPosition,
                    size = if (pose.boneIndex == selectedBoneIndex) jointHalfSize * SelectedJointSizeMultiplier else jointHalfSize,
                    color = jointColor(pose.boneIndex, hoveredBoneIndex, selectedBoneIndex, connectedBoneIndices),
                )
            }

            val parent = pose.parentIndex?.let(poseByIndex::get) ?: return@forEach
            world.renderCommands.submit(
                DrawLine(
                    from = transformLocalPoint(parent.worldPosition, transform),
                    to = transformLocalPoint(pose.worldPosition, transform),
                    color =
                        lineColor(
                            parent.boneIndex,
                            pose.boneIndex,
                            hoveredBoneIndex,
                            selectedBoneIndex,
                            connectedBoneIndices,
                        ),
                ),
            )
        }
    }

    private fun lineColor(
        parentBoneIndex: Int,
        boneIndex: Int,
        hoveredBoneIndex: Int?,
        selectedBoneIndex: Int?,
        connectedBoneIndices: Set<Int>,
    ): Color =
        when {
            boneIndex == hoveredBoneIndex -> HoveredBoneColor
            boneIndex == selectedBoneIndex -> SelectedBoneColor
            state.highlightConnectedBones &&
                (
                    parentBoneIndex == selectedBoneIndex ||
                        boneIndex in connectedBoneIndices
                    ) -> ConnectedBoneColor

            else -> SkeletonColor
        }

    private fun jointColor(
        boneIndex: Int,
        hoveredBoneIndex: Int?,
        selectedBoneIndex: Int?,
        connectedBoneIndices: Set<Int>,
    ): Color =
        when {
            boneIndex == hoveredBoneIndex -> HoveredJointColor
            boneIndex == selectedBoneIndex -> SelectedJointColor
            state.highlightConnectedBones && boneIndex in connectedBoneIndices -> ConnectedJointColor
            else -> JointColor
        }

    private fun submitJoint(
        world: SceneWorld,
        transform: TransformComponent,
        position: Vec3,
        size: Float,
        color: Color,
    ) {
        submitJointAxis(world, transform, position, Vec3(size, 0f, 0f), color)
        submitJointAxis(world, transform, position, Vec3(0f, size, 0f), color)
        submitJointAxis(world, transform, position, Vec3(0f, 0f, size), color)
    }

    private fun submitJointAxis(
        world: SceneWorld,
        transform: TransformComponent,
        position: Vec3,
        axisOffset: Vec3,
        color: Color,
    ) {
        world.renderCommands.submit(
            DrawLine(
                from = transformLocalPoint(position - axisOffset, transform),
                to = transformLocalPoint(position + axisOffset, transform),
                color = color,
            ),
        )
    }

    companion object {
        private val SkeletonColor = Color(0.35f, 0.95f, 1f, 1f)
        private val HoveredBoneColor = Color(0.55f, 1f, 0.45f, 1f)
        private val SelectedBoneColor = Color(1f, 0.35f, 0.15f, 1f)
        private val ConnectedBoneColor = Color(1f, 0.85f, 0.2f, 1f)
        private val JointColor = Color(0.7f, 0.95f, 1f, 1f)
        private val HoveredJointColor = Color(0.55f, 1f, 0.45f, 1f)
        private val ConnectedJointColor = Color(1f, 0.85f, 0.2f, 1f)
        private val SelectedJointColor = Color(1f, 0.45f, 0.2f, 1f)
        private const val MinJointHalfSize = 0.001f
        private const val SelectedJointSizeMultiplier = 1.35f
    }
}
