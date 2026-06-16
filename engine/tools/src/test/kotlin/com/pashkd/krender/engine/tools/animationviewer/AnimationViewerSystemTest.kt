package com.pashkd.krender.engine.tools.animationviewer
import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.EventBus
import com.pashkd.krender.engine.api.FrameProfilerService
import com.pashkd.krender.engine.api.FrameRuntimeStatsService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.LogService
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAnimationInfo
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelBoneInfo
import com.pashkd.krender.engine.api.ModelBonePose
import com.pashkd.krender.engine.api.ModelSkeletonInfo
import com.pashkd.krender.engine.api.ProfilerService
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.SceneManager
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.NoOpAssetRegistryService
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.NoOpUiService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.window.InMemoryWindowService
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.test.newTestRuntimeUiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimationViewerSystemTest {
    @Test
    fun `clampAnimationTime keeps time inside known duration`() {
        assertEquals(0f, clampAnimationTime(-0.5f, 2f))
        assertEquals(1.25f, clampAnimationTime(1.25f, 2f))
        assertEquals(2f, clampAnimationTime(3f, 2f))
    }

    @Test
    fun `clampAnimationTime uses preview window when duration is unknown`() {
        assertEquals(0f, clampAnimationTime(-0.5f, null))
        assertEquals(4.5f, clampAnimationTime(4.5f, null))
        assertEquals(AnimationViewerState.UnknownDurationPreviewWindowSeconds, clampAnimationTime(12f, null))
        assertEquals(4.5f, clampAnimationTime(4.5f, 0f))
    }

    @Test
    fun `selecting a valid bone updates state`() {
        val state =
            AnimationViewerState(model = AssetRef.model("models/animated.glb")).apply {
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Hips", 0),
                            ),
                    )
            }
        val logger = CollectingLogger()
        val operations =
            AnimationViewerOperations(
                state = state,
                context = TestEngineContext(logger = logger),
                layoutTracker = ImGuiLayoutRuntimeTracker(ImGuiLayoutConfig(emptyMap())),
            )

        operations.selectBone(1)

        assertEquals(1, state.selectedBoneIndex)
        assertEquals("Hips", state.selectedBoneName)
        assertEquals("Selected bone: Hips", state.statusMessage)
        assertTrue(
            logger.entries.any { entry ->
                entry.level == LogLevel.Info && entry.message == "AnimationViewer bone selected index=1 name='Hips'"
            },
        )
    }

    @Test
    fun `selecting an invalid bone clears selection without crashing`() {
        val state =
            AnimationViewerState(model = AssetRef.model("models/animated.glb")).apply {
                skeletonInfo = ModelSkeletonInfo(bones = listOf(ModelBoneInfo(0, "Root", null)))
                selectedBoneIndex = 0
                selectedBoneName = "Root"
            }
        val operations =
            AnimationViewerOperations(
                state = state,
                context = TestEngineContext(),
                layoutTracker = ImGuiLayoutRuntimeTracker(ImGuiLayoutConfig(emptyMap())),
            )

        operations.selectBone(99)

        assertNull(state.selectedBoneIndex)
        assertNull(state.selectedBoneName)
        assertEquals("Selected bone is unavailable.", state.statusMessage)
    }

    @Test
    fun `connected bone indices include direct parent and children only`() {
        val state =
            AnimationViewerState(model = AssetRef.model("models/animated.glb")).apply {
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Hips", 0),
                                ModelBoneInfo(2, "Spine", 1),
                                ModelBoneInfo(3, "LeftLeg", 1),
                                ModelBoneInfo(4, "Chest", 2),
                            ),
                    )
                selectedBoneIndex = 1
            }

        assertEquals(setOf(0, 2, 3), state.connectedBoneIndices())
    }

    @Test
    fun `animation viewer state hides joints and bounding box by default`() {
        val state = AnimationViewerState(model = AssetRef.model("models/animated.glb"))

        assertFalse(state.showSkeletonJoints)
        assertFalse(state.showBoundingBox)
    }

    @Test
    fun `playback clamps and pauses at clip end when loop is disabled`() {
        val model = AssetRef.model("models/animated.glb")
        val assets =
            FakeAssetService(
                loaded = true,
                modelInfo =
                    modelInfo(
                        animations = listOf(ModelAnimationInfo("Walk", durationSeconds = 1f)),
                        hasSkeleton = true,
                        boneCount = 4,
                    ),
                skeletonInfo = ModelSkeletonInfo(listOf(ModelBoneInfo(0, "Root", null))),
            )
        val state =
            AnimationViewerState(model = model).apply {
                selectedAnimationName = "Walk"
                selectedAnimationIndex = 0
                currentTimeSeconds = 0.9f
                durationSeconds = 1f
                isPlaying = true
                loop = false
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = assets,
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.2f)
        assertTrue(state.assetLoaded)
        assertEquals(1.0f, state.currentTimeSeconds)
        assertFalse(state.isPlaying)
        assertEquals("Walk", state.selectedAnimationName)
    }

    @Test
    fun `unknown-duration playback stops at the preview window limit`() {
        val model = AssetRef.model("models/animated.glb")
        val assets =
            FakeAssetService(
                loaded = true,
                modelInfo =
                    modelInfo(
                        animations = listOf(ModelAnimationInfo("Walk", durationSeconds = null)),
                        hasSkeleton = true,
                        boneCount = 4,
                    ),
                skeletonInfo = ModelSkeletonInfo(listOf(ModelBoneInfo(0, "Root", null))),
            )
        val state =
            AnimationViewerState(model = model).apply {
                selectedAnimationName = "Walk"
                selectedAnimationIndex = 0
                currentTimeSeconds = UnknownDurationPreviewWindowSeconds - 0.1f
                durationSeconds = null
                isPlaying = true
                loop = true
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = assets,
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.2f)
        assertEquals(UnknownDurationPreviewWindowSeconds, state.currentTimeSeconds)
        assertFalse(state.isPlaying)
        assertEquals(AnimationPreviewStatus.PreviewRequested, state.animationPreviewStatus)
    }

    @Test
    fun `skeleton pose is sampled during update and cached in state`() {
        val model = AssetRef.model("models/animated.glb")
        val assets =
            FakeAssetService(
                loaded = true,
                modelInfo = modelInfo(hasSkeleton = true, boneCount = 2),
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    ),
                skeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    ),
            )
        val state =
            AnimationViewerState(model = model).apply {
                viewMode = AnimationViewerViewMode.Skeleton
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = assets,
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.016f)
        assertEquals(1, assets.modelSkeletonPoseCallCount)
        assertEquals(2, state.sampledSkeletonPose.size)
        assertEquals(SkeletonPreviewStatus.PreviewAvailable, state.skeletonPreviewStatus)
        assertEquals(AnimationViewerViewMode.Skeleton, state.viewMode)
        assertNull(state.sampledSkeletonPoseAnimationName)
        assertEquals(0f, state.sampledSkeletonPoseTimeSeconds)
    }

    @Test
    fun `switching to model view clears cached skeleton preview state`() {
        val model = AssetRef.model("models/animated.glb")
        val skeletonInfo =
            ModelSkeletonInfo(
                bones =
                    listOf(
                        ModelBoneInfo(0, "Root", null),
                        ModelBoneInfo(1, "Child", 0),
                    ),
            )
        val assets =
            FakeAssetService(
                loaded = true,
                modelInfo = modelInfo(hasSkeleton = true, boneCount = 2),
                skeletonInfo = skeletonInfo,
            )
        val state =
            AnimationViewerState(model = model).apply {
                viewMode = AnimationViewerViewMode.Model
                this.skeletonInfo = skeletonInfo
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
                skeletonPreviewStatus = SkeletonPreviewStatus.PreviewAvailable
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = assets,
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.016f)
        assertTrue(state.sampledSkeletonPose.isEmpty())
        assertEquals(SkeletonPreviewStatus.Inactive, state.skeletonPreviewStatus)
        assertEquals(0, assets.modelSkeletonPoseCallCount)
    }

    @Test
    fun `switching to a model without skeleton clears selected bone`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                selectedBoneIndex = 1
                selectedBoneName = "Hips"
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets =
                    FakeAssetService(
                        loaded = true,
                        modelInfo = modelInfo(hasSkeleton = false, boneCount = 0),
                        skeletonInfo = null,
                    ),
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )

        world.update(0.016f)

        assertNull(state.selectedBoneIndex)
        assertNull(state.selectedBoneName)
        assertEquals("Selected bone cleared because skeleton changed.", state.statusMessage)
    }

    @Test
    fun `skeleton render emits parent child bone lines from cached pose`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = false
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))
        world.render(alpha = 0f)
        val commands = world.renderCommands.snapshot()
        assertEquals(1, commands.size)
        val line = assertIs<DrawLine>(commands.single())
        assertEquals(Vec3(0f, 0f, 0f), line.from)
        assertEquals(Vec3(0f, 1f, 0f), line.to)
    }

    @Test
    fun `skeleton render highlights selected bone and connected child with different colors`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = false
                highlightConnectedBones = true
                selectedBoneIndex = 1
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                                ModelBoneInfo(2, "GrandChild", 1),
                                ModelBoneInfo(3, "Sibling", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                        ModelBonePose(2, "GrandChild", 1, Vec3(0f, 2f, 0f)),
                        ModelBonePose(3, "Sibling", 0, Vec3(1f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot().filterIsInstance<DrawLine>()
        assertEquals(3, commands.size)
        assertTrue(
            commands.any { command ->
                command.from == Vec3(0f, 0f, 0f) &&
                    command.to == Vec3(0f, 1f, 0f) &&
                    command.color == SelectedBoneColor
            },
        )
        assertTrue(
            commands.any { command ->
                command.from == Vec3(0f, 1f, 0f) &&
                    command.to == Vec3(0f, 2f, 0f) &&
                    command.color == ConnectedBoneColor
            },
        )
        assertTrue(
            commands.any { command ->
                command.from == Vec3(0f, 0f, 0f) &&
                    command.to == Vec3(1f, 1f, 0f) &&
                    command.color == SkeletonColor
            },
        )
    }

    @Test
    fun `skeleton render highlights hovered bone line with hover color`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = false
                hoveredBoneIndex = 1
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot().filterIsInstance<DrawLine>()
        assertTrue(
            commands.any { command ->
                command.from == Vec3(0f, 0f, 0f) &&
                    command.to == Vec3(0f, 1f, 0f) &&
                    command.color == HoveredBoneColor
            },
        )
    }

    @Test
    fun `skeleton render highlights hovered joint with hover color when enabled`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = true
                hoveredBoneIndex = 1
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot().filterIsInstance<DrawLine>()
        assertTrue(
            commands.any { command ->
                command.color == HoveredJointColor &&
                    (
                        command.from == Vec3(-0.06f, 1f, 0f) &&
                            command.to == Vec3(0.06f, 1f, 0f) ||
                            command.from == Vec3(0f, 0.94f, 0f) &&
                            command.to == Vec3(0f, 1.06f, 0f) ||
                            command.from == Vec3(0f, 1f, -0.06f) &&
                            command.to == Vec3(0f, 1f, 0.06f)
                    )
            },
        )
    }

    @Test
    fun `skeleton render emits joint cross lines when enabled`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = true
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))

        world.render(alpha = 0f)

        assertEquals(
            7,
            world.renderCommands
                .snapshot()
                .filterIsInstance<DrawLine>()
                .size,
        )
    }

    @Test
    fun `skeleton render does not emit joint cross lines when disabled`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Skeleton
                showSkeletonJoints = false
                skeletonInfo =
                    ModelSkeletonInfo(
                        bones =
                            listOf(
                                ModelBoneInfo(0, "Root", null),
                                ModelBoneInfo(1, "Child", 0),
                            ),
                    )
                sampledSkeletonPose =
                    listOf(
                        ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                        ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
                    )
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerSkeletonRenderSystem(state))

        world.render(alpha = 0f)

        assertEquals(
            1,
            world.renderCommands
                .snapshot()
                .filterIsInstance<DrawLine>()
                .size,
        )
    }

    @Test
    fun `unknown-duration animation preview command disables loop`() {
        val model = AssetRef.model("models/animated.glb")
        val state =
            AnimationViewerState(model = model).apply {
                assetLoaded = true
                viewMode = AnimationViewerViewMode.Model
                selectedAnimationName = "Walk"
                currentTimeSeconds = 1.5f
                durationSeconds = null
                loop = true
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(AnimationViewerModelRenderSystem(state))
        world.render(alpha = 0f)
        val command = assertIs<DrawModel>(world.renderCommands.snapshot().single())
        assertEquals(false, command.animation?.loop)
    }

    @Test
    fun `warning logs are de-duplicated across unchanged updates`() {
        val model = AssetRef.model("models/animated.glb")
        val logger = CollectingLogger()
        val assets =
            FakeAssetService(
                loaded = true,
                modelInfo = modelInfo(animations = listOf(ModelAnimationInfo("Walk", durationSeconds = null))),
            )
        val state =
            AnimationViewerState(model = model).apply {
                selectedAnimationName = "Walk"
                selectedAnimationIndex = 0
            }
        val world = SceneWorld()
        val entity = world.createEntity("Model")
        entity.add(ModelComponent(model))
        state.modelEntityId = entity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = assets,
                logger = logger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.016f)
        world.update(0.016f)
        assertEquals(
            1,
            logger.entries.count { entry ->
                entry.level == LogLevel.Warn && entry.message == "Unknown animation duration. Preview is limited to 10.000 s."
            },
        )
    }

    @Test
    fun `ambient light sync clamps negative intensity to zero`() {
        val state =
            AnimationViewerState(model = AssetRef.model("models/animated.glb")).apply {
                ambientLightIntensity = -2f
            }
        val world = SceneWorld()
        val lightEntity = world.createEntity("Ambient")
        lightEntity.add(LightComponent(type = LightType.Ambient, intensity = 1f))
        state.ambientLightEntityId = lightEntity.id
        world.systems.add(
            AnimationViewerSystem(
                assets = FakeAssetService(),
                logger = NoopLogger,
                state = state,
                onExitRequested = {},
            ),
        )
        world.update(0.016f)
        assertEquals(0f, state.ambientLightIntensity)
        assertEquals(0f, lightEntity.get<LightComponent>()?.intensity)
    }

    private fun modelInfo(
        animations: List<ModelAnimationInfo> = emptyList(),
        hasSkeleton: Boolean = false,
        boneCount: Int = 0,
    ): ModelAssetInfo =
        ModelAssetInfo(
            path = "models/animated.glb",
            format = "glTF",
            nodeCount = 1,
            meshCount = 1,
            meshPartCount = 1,
            materialCount = 1,
            vertexCount = 3,
            triangleCount = 1,
            size = null,
            vertexChannels = emptyList(),
            uvChannels = emptyList(),
            textureChannels = emptyList(),
            textureCount = 0,
            textureSlotCount = 0,
            hasSkeleton = hasSkeleton,
            boneCount = boneCount,
            boneWeightChannelCount = 4,
            animations = animations,
            animationCount = animations.size,
            animationNames = animations.map(ModelAnimationInfo::name),
        )

    private class FakeAssetService(
        private val loaded: Boolean = false,
        private val modelInfo: ModelAssetInfo? = null,
        private val skeletonInfo: ModelSkeletonInfo? = null,
        private val skeletonPose: List<ModelBonePose> = emptyList(),
    ) : AssetService {
        var modelSkeletonPoseCallCount: Int = 0

        override fun queue(asset: AssetRef<*>) = Unit

        override fun update(budgetMs: Int): Float = if (loaded) 1f else 0f

        override fun progress(): Float = if (loaded) 1f else 0f

        override fun isLoaded(asset: AssetRef<*>): Boolean = loaded

        override fun <T : Any> get(asset: AssetRef<T>): T = error("Not used in test")

        override fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo? = modelInfo

        override fun modelSkeleton(asset: AssetRef<ModelAsset>): ModelSkeletonInfo? = skeletonInfo

        override fun modelSkeletonPose(
            asset: AssetRef<ModelAsset>,
            animationName: String?,
            timeSeconds: Float,
            loop: Boolean,
        ): List<ModelBonePose> {
            modelSkeletonPoseCallCount += 1
            return skeletonPose
        }

        override fun unload(asset: AssetRef<*>) = Unit
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            error: Throwable?,
            message: () -> String,
        ) = Unit
    }

    private class CollectingLogger : Logger {
        val entries = mutableListOf<LoggedEntry>()

        override fun log(
            level: LogLevel,
            tag: String,
            error: Throwable?,
            message: () -> String,
        ) {
            entries += LoggedEntry(level, tag, message())
        }
    }

    private class TestEngineContext(
        override val logger: Logger = NoopLogger,
        override val assets: AssetService = FakeAssetService(),
    ) : EngineContext {
        override val scenes: SceneManager = SceneManager()
        override val assetRegistry: AssetRegistryService = NoOpAssetRegistryService()
        override val sceneFiles: SceneFileService =
            object : SceneFileService {
                override fun writeText(
                    path: String,
                    text: String,
                ) = Unit

                override fun readText(path: String): String = error("unused")

                override fun ensureDirectories(path: String) = Unit

                override fun exists(path: String): Boolean = false

                override fun describeReadableSource(path: String): String = "test"
            }
        override val runtimeLauncher: RuntimeWindowLauncher = UnsupportedRuntimeWindowLauncher
        override val editorToolLauncher: EditorToolLauncher = UnsupportedEditorToolLauncher
        override val input: InputService =
            object : InputService {
                override fun beginFrame() = Unit

                override fun snapshot(): InputSnapshot = InputSnapshot()

                override fun endFrame() = Unit

                override fun setCursorCaptured(captured: Boolean) = Unit

                override fun isActionPressed(action: Action): Boolean = false

                override fun isActionJustPressed(action: Action): Boolean = false

                override fun axis(axis: Axis): Float = 0f
            }
        override val ui: UiService = NoOpUiService()
        override val runtimeUi = newTestRuntimeUiService(logger)
        override val events: EventBus = EventBus()
        override val logs: LogService =
            object : LogService {
                override val recentEntries: List<com.pashkd.krender.engine.api.LogEntry> = emptyList()
                override var minLevel: LogLevel = LogLevel.Trace

                override fun record(entry: com.pashkd.krender.engine.api.LogEntry) = Unit

                override fun clear() = Unit

                override fun addSink(sink: com.pashkd.krender.engine.api.LogSink) = Unit

                override fun removeSink(sink: com.pashkd.krender.engine.api.LogSink) = Unit
            }
        override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
        override val profiler: ProfilerService = FrameProfilerService()
        override val tasks: TaskService =
            object : TaskService {
                override val inFlightJobs: Int = 0

                override fun launchBackground(
                    name: String,
                    block: suspend CoroutineScope.() -> Unit,
                ): Job = Job()

                override suspend fun <T> onBackground(block: suspend () -> T): T = block()

                override suspend fun <T> onIo(block: suspend () -> T): T = block()

                override suspend fun <T> onMain(block: suspend () -> T): T = block()

                override fun postToMain(block: () -> Unit) = block()

                override fun flushMainThreadQueue() = Unit

                override fun dispose() = Unit
            }
        override val viewport: RuntimeViewportService = RuntimeViewportService()
        override val window: WindowService = InMemoryWindowService()
        override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null

        override fun requestExit() = Unit
    }

    private data class LoggedEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
    )

    companion object {
        private const val UnknownDurationPreviewWindowSeconds = AnimationViewerState.UnknownDurationPreviewWindowSeconds
        private val SkeletonColor = Color(0.35f, 0.95f, 1f, 1f)
        private val HoveredBoneColor = Color(0.55f, 1f, 0.45f, 1f)
        private val SelectedBoneColor = Color(1f, 0.35f, 0.15f, 1f)
        private val ConnectedBoneColor = Color(1f, 0.85f, 0.2f, 1f)
        private val HoveredJointColor = Color(0.55f, 1f, 0.45f, 1f)
    }
}
