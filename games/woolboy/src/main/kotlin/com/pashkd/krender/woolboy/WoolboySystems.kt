package com.pashkd.krender.woolboy

import com.pashkd.krender.engine.animation.AnimationComponent
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.api.VelocityComponent
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal MVP player controller for the Woolboy sandbox.
 *
 * The controller intentionally stays simple: it reads direct keyboard input,
 * moves the player on the horizontal X/Z plane, uses a parabolic jump instead
 * of physics, and clamps movement to the known flat terrain bounds. Movement is
 * camera-relative so WASD follows the current third-person view direction.
 */
class PlayerControllerSystem(
    private val input: InputService,
    private val logger: Logger,
    private val inputEnabled: () -> Boolean = { true },
) : System() {
    companion object {
        private const val TAG = "PlayerControllerSystem"
        const val MoveSpeed = 4.0f
        const val JumpHeight = 1.5f
        const val JumpDurationSeconds = 0.55f
        const val TerrainHalfExtent = 32f
        const val PlayerBaseY = 0f

        /**
         * Fallback basis used before a camera exists. A/D are intentionally
         * inverted for this prototype based on current scene controls feedback.
         */
        private val DefaultCameraMovementBasis =
            CameraMovementBasis(
                forward = Vec3(0f, 0f, 1f),
                right = Vec3(-1f, 0f, 0f),
            )
    }

    private var hasLoggedFirstUpdate = false

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val player =
            world
                .query<TransformComponent, PlayerControllerComponent, VelocityComponent>()
                .firstOrNull { entity -> entity.get<PlayerControllerComponent>()?.isActive == true }
                ?: return
        val transform = player.get<TransformComponent>() ?: return
        val controller = player.get<PlayerControllerComponent>() ?: return
        val velocity = player.get<VelocityComponent>() ?: return

        if (!inputEnabled()) {
            velocity.value = Vec3.zero()
            return
        }

        val snapshot = input.snapshot()

        if (!hasLoggedFirstUpdate) {
            logger.info(TAG) { "Woolboy player controller active entityId=${player.id}" }
            hasLoggedFirstUpdate = true
        }

        var inputX = 0f
        var inputZ = 0f
        if (!snapshot.uiCapturesKeyboard) {
            if (snapshot.isDown(Key.W)) inputZ += 1f
            if (snapshot.isDown(Key.S)) inputZ -= 1f
            if (snapshot.isDown(Key.A)) inputX -= 1f
            if (snapshot.isDown(Key.D)) inputX += 1f
        }

        val moving = inputX * inputX + inputZ * inputZ > 0f
        if (moving) {
            val inputLength = sqrt(inputX * inputX + inputZ * inputZ)
            inputX /= inputLength
            inputZ /= inputLength

            val cameraBasis = cameraMovementBasis(world)
            val moveX = cameraBasis.forward.x * inputZ + cameraBasis.right.x * inputX
            val moveZ = cameraBasis.forward.z * inputZ + cameraBasis.right.z * inputX
            val moveLength = sqrt(moveX * moveX + moveZ * moveZ).coerceAtLeast(0.0001f)
            val normalizedMoveX = moveX / moveLength
            val normalizedMoveZ = moveZ / moveLength
            transform.position.x += moveX * MoveSpeed * dt
            transform.position.z += moveZ * MoveSpeed * dt
            transform.eulerDegrees.y = yawDegrees(normalizedMoveX, normalizedMoveZ)
            velocity.value.x = normalizedMoveX * MoveSpeed
            velocity.value.z = normalizedMoveZ * MoveSpeed
            controller.greetingRequested = false
        } else {
            velocity.value.x = 0f
            velocity.value.z = 0f
        }

        if (!snapshot.uiCapturesKeyboard && snapshot.wasPressed(Key.Space) && controller.isGrounded) {
            controller.isGrounded = false
            controller.jumpTimeSeconds = 0f
            velocity.value.y = 0f
            controller.greetingRequested = false
            logger.debug(TAG) { "Woolboy jump triggered entityId=${player.id}" }
        }

        if (!snapshot.uiCapturesKeyboard && snapshot.wasPressed(Key.F) && controller.isGrounded && !moving) {
            controller.greetingRequested = true
            logger.debug(TAG) { "Woolboy greeting requested entityId=${player.id}" }
        }

        updateJump(transform, controller, velocity, dt)
        clampToTerrain(transform, velocity)
    }

    private fun updateJump(
        transform: TransformComponent,
        controller: PlayerControllerComponent,
        velocity: VelocityComponent,
        dt: Float,
    ) {
        if (controller.isGrounded) {
            transform.position.y = PlayerBaseY
            velocity.value.y = 0f
            return
        }

        controller.jumpTimeSeconds += dt
        val t = (controller.jumpTimeSeconds / JumpDurationSeconds).coerceIn(0f, 1f)
        val previousY = transform.position.y
        transform.position.y = PlayerBaseY + JumpHeight * 4f * t * (1f - t)
        velocity.value.y = if (dt > 0f) (transform.position.y - previousY) / dt else 0f

        if (t >= 1f) {
            controller.isGrounded = true
            controller.jumpTimeSeconds = 0f
            transform.position.y = PlayerBaseY
            velocity.value.y = 0f
        }
    }

    private fun clampToTerrain(
        transform: TransformComponent,
        velocity: VelocityComponent,
    ) {
        val clampedX = transform.position.x.coerceIn(-TerrainHalfExtent, TerrainHalfExtent)
        val clampedZ = transform.position.z.coerceIn(-TerrainHalfExtent, TerrainHalfExtent)
        if (clampedX != transform.position.x) velocity.value.x = 0f
        if (clampedZ != transform.position.z) velocity.value.z = 0f
        transform.position.x = clampedX
        transform.position.z = clampedZ
    }

    private fun cameraMovementBasis(world: SceneWorld): CameraMovementBasis {
        val cameraEntity =
            world.query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>().firstOrNull()
                ?: world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
                ?: return DefaultCameraMovementBasis
        val transform = cameraEntity.get<TransformComponent>() ?: return DefaultCameraMovementBasis
        val camera = cameraEntity.get<PerspectiveCameraComponent>() ?: return DefaultCameraMovementBasis
        val lookAt = camera.lookAt

        val forward =
            if (lookAt != null) {
                Vec3(
                    x = lookAt.x - transform.position.x,
                    y = 0f,
                    z = lookAt.z - transform.position.z,
                ).normalizedHorizontalOrNull()
            } else {
                horizontalForwardFromEuler(transform.eulerDegrees.y)
            } ?: return DefaultCameraMovementBasis

        return CameraMovementBasis(
            forward = forward,
            right = Vec3(-forward.z, 0f, forward.x),
        )
    }

    private fun horizontalForwardFromEuler(yawDegrees: Float): Vec3 {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        return Vec3(
            x = sin(yaw).toFloat(),
            y = 0f,
            z = cos(yaw).toFloat(),
        ).normalizedHorizontalOrNull() ?: Vec3(0f, 0f, 1f)
    }

    private fun Vec3.normalizedHorizontalOrNull(): Vec3? {
        val length = sqrt(x * x + z * z)
        if (length <= 0.0001f) return null
        return Vec3(x / length, 0f, z / length)
    }

    private fun yawDegrees(
        x: Float,
        z: Float,
    ): Float = (atan2(x.toDouble(), z.toDouble()) * 180.0 / PI).toFloat()

    private data class CameraMovementBasis(
        val forward: Vec3,
        val right: Vec3,
    )
}

class CharacterAnimationSystem(
    private val assets: AssetService,
    private val logger: Logger,
) : System() {
    private var warnedMissingDuration = false

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        world.query<ModelComponent, AnimationComponent, PlayerControllerComponent>().forEach { entity ->
            val model = entity.get<ModelComponent>() ?: return@forEach
            val animation = entity.get<AnimationComponent>() ?: return@forEach
            val controller = entity.get<PlayerControllerComponent>() ?: return@forEach
            val velocity = entity.get<VelocityComponent>()
            val moving = velocity?.let { horizontalSpeedSquared(it) > WalkThreshold * WalkThreshold } ?: false
            val target = selectAnimation(animation, controller, moving)

            animation.play(
                name = target.name,
                loop = target.loop,
                restart = target.restart,
                lockedUntilFinished = target.locked,
            )
            if (target.name == WoolboyAnimations.Greeting) {
                controller.greetingRequested = false
            }

            advanceAnimation(animation, durationFor(model, animation), dt)
        }
    }

    private fun selectAnimation(
        animation: AnimationComponent,
        controller: PlayerControllerComponent,
        moving: Boolean,
    ): AnimationTarget =
        when {
            !controller.isGrounded -> AnimationTarget(WoolboyAnimations.Jump, loop = false)
            moving -> AnimationTarget(WoolboyAnimations.Walk, loop = true)
            controller.greetingRequested ->
                AnimationTarget(
                    name = WoolboyAnimations.Greeting,
                    loop = false,
                    restart = true,
                    locked = true,
                )

            animation.currentAnimation == WoolboyAnimations.Greeting && animation.lockedUntilFinished ->
                AnimationTarget(WoolboyAnimations.Greeting, loop = false, locked = true)

            else -> AnimationTarget(WoolboyAnimations.Idle, loop = true)
        }

    private fun advanceAnimation(
        animation: AnimationComponent,
        durationSeconds: Float?,
        dt: Float,
    ) {
        if (!animation.playing) return
        animation.timeSeconds = (animation.timeSeconds + dt * animation.speed.coerceAtLeast(0f)).coerceAtLeast(0f)
        val duration = durationSeconds ?: return
        if (duration <= 0f) return

        if (animation.loop) {
            animation.timeSeconds %= duration
            return
        }

        if (animation.timeSeconds >= duration) {
            animation.timeSeconds = duration
            animation.playing = false
            animation.lockedUntilFinished = false
        }
    }

    private fun durationFor(
        model: ModelComponent,
        animation: AnimationComponent,
    ): Float? {
        val animationName = animation.currentAnimation ?: return null
        val metadataDuration =
            assets
                .modelInfo(model.model)
                ?.animations
                ?.firstOrNull { info -> info.name == animationName }
                ?.durationSeconds
        if (metadataDuration != null) return metadataDuration

        if (!warnedMissingDuration && (animationName == WoolboyAnimations.Greeting || animationName == WoolboyAnimations.Jump)) {
            logger.log(LogLevel.Debug, TAG) { "Using fallback duration for animation='$animationName'" }
            warnedMissingDuration = true
        }
        return when (animationName) {
            WoolboyAnimations.Greeting -> GreetingFallbackDuration
            WoolboyAnimations.Jump -> PlayerControllerSystem.JumpDurationSeconds
            else -> null
        }
    }

    private fun horizontalSpeedSquared(
        velocity: VelocityComponent,
    ): Float = velocity.value.x * velocity.value.x + velocity.value.z * velocity.value.z

    private data class AnimationTarget(
        val name: String,
        val loop: Boolean,
        val restart: Boolean = false,
        val locked: Boolean = false,
    )

    companion object {
        private const val TAG = "CharacterAnimationSystem"
        private const val WalkThreshold = 0.01f
        private const val GreetingFallbackDuration = 1.5f
    }
}

object WoolboyAnimations {
    const val Idle = "wait_action"
    const val Walk = "walk_action"
    const val Jump = "jump_action-movement"
    const val Greeting = "greeting_action"
}
