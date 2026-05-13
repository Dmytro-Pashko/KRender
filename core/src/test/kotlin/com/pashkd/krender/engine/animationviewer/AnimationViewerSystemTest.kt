package com.pashkd.krender.engine.animationviewer
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAnimationInfo
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelBoneInfo
import com.pashkd.krender.engine.api.ModelBonePose
import com.pashkd.krender.engine.api.ModelSkeletonInfo
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
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
    fun `playback clamps and pauses at clip end when loop is disabled`() {
        val model = AssetRef.model("models/animated.glb")
        val assets = FakeAssetService(
            loaded = true,
            modelInfo = modelInfo(
                animations = listOf(ModelAnimationInfo("Walk", durationSeconds = 1f)),
                hasSkeleton = true,
                boneCount = 4,
            ),
            skeletonInfo = ModelSkeletonInfo(listOf(ModelBoneInfo(0, "Root", null))),
        )
        val state = AnimationViewerState(model = model).apply {
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
        val assets = FakeAssetService(
            loaded = true,
            modelInfo = modelInfo(
                animations = listOf(ModelAnimationInfo("Walk", durationSeconds = null)),
                hasSkeleton = true,
                boneCount = 4,
            ),
            skeletonInfo = ModelSkeletonInfo(listOf(ModelBoneInfo(0, "Root", null))),
        )
        val state = AnimationViewerState(model = model).apply {
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
        val assets = FakeAssetService(
            loaded = true,
            modelInfo = modelInfo(hasSkeleton = true, boneCount = 2),
            skeletonInfo = ModelSkeletonInfo(
                bones = listOf(
                    ModelBoneInfo(0, "Root", null),
                    ModelBoneInfo(1, "Child", 0),
                ),
            ),
            skeletonPose = listOf(
                ModelBonePose(0, "Root", null, Vec3(0f, 0f, 0f)),
                ModelBonePose(1, "Child", 0, Vec3(0f, 1f, 0f)),
            ),
        )
        val state = AnimationViewerState(model = model).apply {
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
        val skeletonInfo = ModelSkeletonInfo(
            bones = listOf(
                ModelBoneInfo(0, "Root", null),
                ModelBoneInfo(1, "Child", 0),
            ),
        )
        val assets = FakeAssetService(
            loaded = true,
            modelInfo = modelInfo(hasSkeleton = true, boneCount = 2),
            skeletonInfo = skeletonInfo,
        )
        val state = AnimationViewerState(model = model).apply {
            viewMode = AnimationViewerViewMode.Model
            this.skeletonInfo = skeletonInfo
            sampledSkeletonPose = listOf(
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
    fun `skeleton render emits parent child bone lines from cached pose`() {
        val model = AssetRef.model("models/animated.glb")
        val state = AnimationViewerState(model = model).apply {
            assetLoaded = true
            viewMode = AnimationViewerViewMode.Skeleton
            skeletonInfo = ModelSkeletonInfo(
                bones = listOf(
                    ModelBoneInfo(0, "Root", null),
                    ModelBoneInfo(1, "Child", 0),
                ),
            )
            sampledSkeletonPose = listOf(
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
    fun `unknown-duration animation preview command disables loop`() {
        val model = AssetRef.model("models/animated.glb")
        val state = AnimationViewerState(model = model).apply {
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
        val assets = FakeAssetService(
            loaded = true,
            modelInfo = modelInfo(animations = listOf(ModelAnimationInfo("Walk", durationSeconds = null))),
        )
        val state = AnimationViewerState(model = model).apply {
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
        val state = AnimationViewerState(model = AssetRef.model("models/animated.glb")).apply {
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
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }
    private class CollectingLogger : Logger {
        val entries = mutableListOf<LoggedEntry>()
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) {
            entries += LoggedEntry(level, tag, message())
        }
    }
    private data class LoggedEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
    )
    companion object {
        private const val UnknownDurationPreviewWindowSeconds = AnimationViewerState.UnknownDurationPreviewWindowSeconds
    }
}
