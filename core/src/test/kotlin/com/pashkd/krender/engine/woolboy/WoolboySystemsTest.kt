package com.pashkd.krender.engine.woolboy

import com.pashkd.krender.engine.animation.AnimationComponent
import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.ModelAnimationInfo
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.api.VelocityComponent
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WoolboySystemsTest {
    @Test
    fun `animation component resets time when clip changes`() {
        val animation = AnimationComponent(currentAnimation = WoolboyAnimations.Idle, timeSeconds = 1.2f)

        animation.play(WoolboyAnimations.Walk, loop = true)

        assertEquals(WoolboyAnimations.Walk, animation.currentAnimation)
        assertEquals(0f, animation.timeSeconds)
    }

    @Test
    fun `character animation selects idle when grounded and still`() {
        val animation = updateAnimationFor(
            controller = PlayerControllerComponent(isGrounded = true),
            velocity = VelocityComponent(Vec3.zero()),
        )

        assertEquals(WoolboyAnimations.Idle, animation.currentAnimation)
        assertTrue(animation.loop)
    }

    @Test
    fun `character animation selects walk when horizontal velocity is non-zero`() {
        val animation = updateAnimationFor(
            controller = PlayerControllerComponent(isGrounded = true),
            velocity = VelocityComponent(Vec3(1f, 0f, 0f)),
        )

        assertEquals(WoolboyAnimations.Walk, animation.currentAnimation)
        assertTrue(animation.loop)
    }

    @Test
    fun `character animation selects jump while airborne`() {
        val animation = updateAnimationFor(
            controller = PlayerControllerComponent(isGrounded = false),
            velocity = VelocityComponent(Vec3(1f, 0f, 0f)),
        )

        assertEquals(WoolboyAnimations.Jump, animation.currentAnimation)
        assertFalse(animation.loop)
    }

    @Test
    fun `greeting animation is non-looping and unlocks after duration`() {
        val world = animationWorld(
            controller = PlayerControllerComponent(isGrounded = true, greetingRequested = true),
            velocity = VelocityComponent(Vec3.zero()),
        )
        val animation = world.query<AnimationComponent>().single().get<AnimationComponent>()!!
        val system = CharacterAnimationSystem(
            assets = FakeAssetService(durationByAnimation = mapOf(WoolboyAnimations.Greeting to 0.2f)),
            logger = NoopLogger,
        )

        system.update(world, dt = 0.1f)

        assertEquals(WoolboyAnimations.Greeting, animation.currentAnimation)
        assertFalse(animation.loop)
        assertTrue(animation.lockedUntilFinished)

        system.update(world, dt = 0.2f)

        assertFalse(animation.playing)
        assertFalse(animation.lockedUntilFinished)

        system.update(world, dt = 0f)

        assertEquals(WoolboyAnimations.Idle, animation.currentAnimation)
    }

    @Test
    fun `player controller clamps movement inside terrain bounds`() {
        val world = SceneWorld()
        val player = world.createEntity("Player")
        player.transform.position.set(31.9f, 0f, 31.9f)
        player.add(PlayerControllerComponent())
        player.add(VelocityComponent())
        val input = FakeInputService(
            InputSnapshot(keysDown = setOf(Key.W, Key.A)),
        )

        PlayerControllerSystem(input, NoopLogger).update(world, dt = 1f)

        assertEquals(32f, player.transform.position.x)
        assertEquals(32f, player.transform.position.z)
    }

    @Test
    fun `player controller moves forward relative to active camera look direction`() {
        val world = SceneWorld()
        val player = world.createEntity("Player")
        player.add(PlayerControllerComponent())
        player.add(VelocityComponent())
        val camera = world.createEntity("Camera")
        camera.transform.position.set(10f, 4f, 0f)
        camera.add(PerspectiveCameraComponent(lookAt = Vec3.zero()))
        camera.add(ActiveCameraComponent())
        val input = FakeInputService(
            InputSnapshot(keysDown = setOf(Key.W)),
        )

        PlayerControllerSystem(input, NoopLogger).update(world, dt = 1f)

        assertEquals(-PlayerControllerSystem.MoveSpeed, player.transform.position.x)
        assertEquals(0f, player.transform.position.z, absoluteTolerance = 0.0001f)
        assertEquals(-90f, player.transform.eulerDegrees.y, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `player controller strafes right relative to active camera look direction`() {
        val world = SceneWorld()
        val player = world.createEntity("Player")
        player.add(PlayerControllerComponent())
        player.add(VelocityComponent())
        val camera = world.createEntity("Camera")
        camera.transform.position.set(0f, 4f, -10f)
        camera.add(PerspectiveCameraComponent(lookAt = Vec3.zero()))
        camera.add(ActiveCameraComponent())
        val input = FakeInputService(
            InputSnapshot(keysDown = setOf(Key.D)),
        )

        PlayerControllerSystem(input, NoopLogger).update(world, dt = 1f)

        assertEquals(-PlayerControllerSystem.MoveSpeed, player.transform.position.x, absoluteTolerance = 0.0001f)
        assertEquals(0f, player.transform.position.z, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `animated model render system forwards animation playback view`() {
        val world = SceneWorld()
        val entity = world.createEntity("Animated Model")
        entity.add(ModelComponent(AssetRef.model("model/wool_boy_animated.glb"), Material()))
        entity.add(AnimationComponent(currentAnimation = WoolboyAnimations.Walk, timeSeconds = 0.4f, loop = true))

        AnimatedModelRenderSystem().render(world, alpha = 0f)

        val command = assertIs<DrawModel>(world.renderCommands.snapshot().single())
        assertEquals(WoolboyAnimations.Walk, command.animation?.animationName)
        assertEquals(0.4f, command.animation?.timeSeconds)
        assertTrue(command.animation?.loop == true)
    }

    @Test
    fun `third person camera orbits on right mouse and zooms with wheel`() {
        val world = SceneWorld()
        val player = world.createEntity("Player")
        player.add(PlayerControllerComponent())
        val camera = world.createEntity("Camera")
        camera.add(PerspectiveCameraComponent())
        camera.add(ActiveCameraComponent())
        val input = FakeInputService(
            InputSnapshot(
                mouseButtonsDown = setOf(MouseButton.Right),
                mouseDelta = Vec2(12f, -8f),
                scrollDelta = 1f,
            ),
        )

        ThirdPersonCameraFollowSystem(input, NoopLogger).lateUpdate(world, dt = 0f)

        val lookAt = camera.get<PerspectiveCameraComponent>()?.lookAt
        assertEquals(0f, lookAt?.x)
        assertEquals(ThirdPersonCameraFollowSystem.CameraLookAtHeight, lookAt?.y)
        assertEquals(0f, lookAt?.z)
        assertFalse(input.cursorCaptured)
        assertEquals(6.16f, distance(camera.transform.position, lookAt!!), absoluteTolerance = 0.02f)
    }

    @Test
    fun `third person camera initializes from authored camera instead of default orbit`() {
        val world = SceneWorld()
        val player = world.createEntity("Player")
        player.add(PlayerControllerComponent())
        val camera = world.createEntity("Camera")
        camera.transform.position.set(5f, 4f, -8f)
        camera.add(PerspectiveCameraComponent())
        camera.add(ActiveCameraComponent())

        ThirdPersonCameraFollowSystem(FakeInputService(InputSnapshot()), NoopLogger).lateUpdate(world, dt = 0f)

        val lookAt = camera.get<PerspectiveCameraComponent>()?.lookAt!!
        assertEquals(distance(Vec3(5f, 4f, -8f), lookAt), distance(camera.transform.position, lookAt), absoluteTolerance = 0.02f)
    }

    private fun updateAnimationFor(
        controller: PlayerControllerComponent,
        velocity: VelocityComponent,
    ): AnimationComponent {
        val world = animationWorld(controller, velocity)
        CharacterAnimationSystem(FakeAssetService(), NoopLogger).update(world, dt = 0.1f)
        return world.query<AnimationComponent>().single().get<AnimationComponent>()!!
    }

    private fun animationWorld(
        controller: PlayerControllerComponent,
        velocity: VelocityComponent,
    ): SceneWorld {
        val world = SceneWorld()
        val entity = world.createEntity("Woolboy")
        entity.add(ModelComponent(AssetRef.model("model/wool_boy_animated.glb"), Material()))
        entity.add(AnimationComponent(currentAnimation = WoolboyAnimations.Idle, loop = true))
        entity.add(controller)
        entity.add(velocity)
        return world
    }

    private class FakeInputService(
        private val snapshot: InputSnapshot,
    ) : InputService {
        var cursorCaptured: Boolean = false
            private set

        override fun beginFrame() = Unit
        override fun snapshot(): InputSnapshot = snapshot
        override fun endFrame() = Unit
        override fun setCursorCaptured(captured: Boolean) {
            cursorCaptured = captured
        }
        override fun isActionPressed(action: Action): Boolean = false
        override fun isActionJustPressed(action: Action): Boolean = false
        override fun axis(axis: Axis): Float = 0f
    }

    private class FakeAssetService(
        private val durationByAnimation: Map<String, Float> = emptyMap(),
    ) : AssetService {
        override fun queue(asset: AssetRef<*>) = Unit
        override fun update(budgetMs: Int): Float = 1f
        override fun isLoaded(asset: AssetRef<*>): Boolean = true
        override fun <T : Any> get(asset: AssetRef<T>): T = error("Not used")
        override fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo =
            ModelAssetInfo(
                path = asset.path,
                format = "glTF",
                nodeCount = 1,
                meshCount = 1,
                meshPartCount = 1,
                materialCount = 1,
                vertexCount = 1,
                triangleCount = 1,
                size = null,
                vertexChannels = emptyList(),
                uvChannels = emptyList(),
                textureChannels = emptyList(),
                textureCount = 0,
                textureSlotCount = 0,
                hasSkeleton = true,
                boneCount = 1,
                boneWeightChannelCount = 4,
                animations = durationByAnimation.map { (name, duration) ->
                    ModelAnimationInfo(name = name, durationSeconds = duration)
                },
            )
        override fun unload(asset: AssetRef<*>) = Unit
    }

    private object NoopLogger : Logger {
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }

    private fun distance(a: Vec3, b: Vec3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
