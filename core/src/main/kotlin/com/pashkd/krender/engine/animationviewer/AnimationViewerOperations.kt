package com.pashkd.krender.engine.animationviewer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Animation Viewer UI operations. Keeps panel callbacks out of direct scene mutation details.
 */
class AnimationViewerOperations(
    private val state: AnimationViewerState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(AnimationViewerUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "UI layout saved."
            context.logger.info(TAG) {
                "AnimationViewer UI layout saved path='${AnimationViewerUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save AnimationViewer UI layout path='${AnimationViewerUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(AnimationViewerUiLayoutDefaults.config)
        state.statusMessage = "UI layout reset to default."
        context.logger.info(TAG) {
            "AnimationViewer UI layout reset to default panels=${AnimationViewerUiLayoutDefaults.config.panels.size}"
        }
    }

    fun resetCamera() {
        state.camera.pendingPosition = DefaultCameraPosition.copy()
        state.camera.pendingEulerDegrees = DefaultCameraEulerDegrees.copy()
        state.camera.position = DefaultCameraPosition.copy()
        state.camera.eulerDegrees = DefaultCameraEulerDegrees.copy()
        state.statusMessage = "Camera reset."
        context.logger.info(TAG) { "AnimationViewer camera reset" }
    }

    fun frameModel() {
        val bounds = context.assets.modelBounds(state.model)
        if (bounds == null) {
            state.statusMessage = "Frame model failed: bounds are not available yet."
            context.logger.warn(TAG) { "Frame model failed because bounds are unavailable model='${state.model.path}'" }
            return
        }

        val scale = state.modelScale.coerceAtLeast(MinScale)
        val center = Vec3(
            x = ((bounds.min.x + bounds.max.x) * 0.5f) * scale,
            y = ((bounds.min.y + bounds.max.y) * 0.5f) * scale,
            z = ((bounds.min.z + bounds.max.z) * 0.5f) * scale,
        )
        val size = Vec3(
            x = (bounds.max.x - bounds.min.x) * scale,
            y = (bounds.max.y - bounds.min.y) * scale,
            z = (bounds.max.z - bounds.min.z) * scale,
        )
        val radius = sqrt(size.x * size.x + size.y * size.y + size.z * size.z) * 0.5f
        val distance = max(radius * FrameDistanceMultiplier, MinFrameDistance)
        val position = Vec3(
            x = center.x,
            y = center.y + max(size.y * FrameHeightOffsetRatio, MinFrameHeightOffset),
            z = center.z + distance,
        )
        val eulerDegrees = Vec3(-10f, 180f, 0f)

        state.camera.pendingPosition = position
        state.camera.pendingEulerDegrees = eulerDegrees
        state.camera.position = position.copy()
        state.camera.eulerDegrees = eulerDegrees.copy()
        state.statusMessage = "Framed model."
        context.logger.info(TAG) { "AnimationViewer framed model='${state.model.path}' radius=${"%.3f".format(radius)}" }
    }

    fun selectAnimation(index: Int) {
        val names = state.animationNames
        if (index !in names.indices) return
        state.selectedAnimationIndex = index
        state.selectedAnimationName = names[index]
        state.durationSeconds = state.selectedAnimationName?.let { selected ->
            state.modelInfo?.animations?.firstOrNull { animation -> animation.name == selected }?.durationSeconds
        }
        state.currentTimeSeconds = 0f
        state.statusMessage = "Selected animation: ${state.selectedAnimationName}"
        context.logger.info(TAG) {
            "AnimationViewer animation selected index=$index name='${state.selectedAnimationName}' duration=${state.durationSeconds}"
        }
    }

    fun play() {
        if (!state.hasAnimations) {
            state.statusMessage = "No animations found for this model."
            context.logger.warn(TAG) { "AnimationViewer play requested with no animations model='${state.model.path}'" }
            return
        }
        if (state.selectedAnimationIndex == null) {
            selectAnimation(0)
        }
        if (state.selectedAnimationName == null) {
            state.statusMessage = "Select an animation first."
            return
        }
        state.isPlaying = true
        state.statusMessage = "Playback started."
        context.logger.info(TAG) {
            "AnimationViewer playback started animation='${state.selectedAnimationName}' loop=${state.loop} speed=${state.playbackSpeed}"
        }
    }

    fun pause() {
        state.isPlaying = false
        state.statusMessage = "Playback paused."
        context.logger.info(TAG) {
            "AnimationViewer playback paused animation='${state.selectedAnimationName}' time=${"%.3f".format(state.currentTimeSeconds)}"
        }
    }

    fun stop() {
        state.isPlaying = false
        state.currentTimeSeconds = 0f
        state.statusMessage = "Playback stopped."
        context.logger.info(TAG) {
            "AnimationViewer playback stopped animation='${state.selectedAnimationName}'"
        }
    }

    fun toggleLoop() {
        state.loop = !state.loop
        state.statusMessage = if (state.loop) "Loop enabled." else "Loop disabled."
        context.logger.info(TAG) { "AnimationViewer loop toggled enabled=${state.loop}" }
    }

    fun scrubTo(timeSeconds: Float) {
        state.currentTimeSeconds = clampAnimationTime(timeSeconds, state.durationSeconds)
        state.statusMessage = "Scrubbed animation time."
        context.logger.debug(TAG) {
            "AnimationViewer scrub time=${"%.3f".format(state.currentTimeSeconds)} animation='${state.selectedAnimationName}'"
        }
    }

    fun stepBy(deltaSeconds: Float) {
        scrubTo(state.currentTimeSeconds + deltaSeconds)
    }

    fun setAmbientLightIntensity(intensity: Float) {
        state.ambientLightIntensity = intensity.coerceIn(MinAmbientLightIntensity, MaxAmbientLightIntensity)
    }

    fun resetAmbientLight() {
        state.ambientLightIntensity = DefaultAmbientLightIntensity
        state.statusMessage = "Ambient light reset."
        context.logger.info(TAG) { "AnimationViewer ambient light reset intensity=${state.ambientLightIntensity}" }
    }

    companion object {
        private const val TAG = "AnimationViewerOperations"
        private const val MinScale = 0.0001f
        private const val FrameDistanceMultiplier = 2.5f
        private const val MinFrameDistance = 2f
        private const val FrameHeightOffsetRatio = 0.15f
        private const val MinFrameHeightOffset = 0.25f
        private const val MinAmbientLightIntensity = 0f
        private const val MaxAmbientLightIntensity = 3f
        private const val DefaultAmbientLightIntensity = 0.8f
        private val DefaultCameraPosition = Vec3(0f, 2f, 6f)
        private val DefaultCameraEulerDegrees = Vec3(-10f, 180f, 0f)
    }
}

internal fun clampAnimationTime(timeSeconds: Float, durationSeconds: Float?): Float {
    val duration = durationSeconds
    return if (duration != null && duration > 0f) {
        timeSeconds.coerceIn(0f, duration)
    } else {
        timeSeconds.coerceIn(0f, AnimationViewerState.UnknownDurationPreviewWindowSeconds)
    }
}

