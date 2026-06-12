package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import kotlin.math.max
import kotlin.math.sqrt

/**
 * ModelViewer UI operations. Keeps panel callbacks out of direct scene mutation details.
 */
class ModelViewerOperations(
    private val state: ModelViewerState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(ModelViewerUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "UI layout saved."
            context.logger.info(TAG) {
                "ModelViewer UI layout saved path='${ModelViewerUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save ModelViewer UI layout path='${ModelViewerUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(ModelViewerUiLayoutDefaults.config)
        state.statusMessage = "UI layout reset to default."
        context.logger.info(TAG) {
            "ModelViewer UI layout reset to default panels=${ModelViewerUiLayoutDefaults.config.panels.size}"
        }
    }

    fun resetCamera() {
        state.camera.pendingPosition = DefaultCameraPosition.copy()
        state.camera.pendingEulerDegrees = DefaultCameraEulerDegrees.copy()
        state.camera.position = DefaultCameraPosition.copy()
        state.camera.eulerDegrees = DefaultCameraEulerDegrees.copy()
        state.statusMessage = "Camera reset."
        context.logger.info(TAG) { "ModelViewer camera reset" }
    }

    fun frameModel() {
        val bounds = context.assets.modelBounds(state.model)
        if (bounds == null) {
            state.statusMessage = "Frame model failed: bounds are not available yet."
            context.logger.warn(TAG) { "Frame model failed because bounds are unavailable model='${state.model.path}'" }
            return
        }

        val scale = state.modelScale.coerceAtLeast(MinScale)
        val center =
            Vec3(
                x = ((bounds.min.x + bounds.max.x) * 0.5f) * scale,
                y = ((bounds.min.y + bounds.max.y) * 0.5f) * scale,
                z = ((bounds.min.z + bounds.max.z) * 0.5f) * scale,
            )
        val size =
            Vec3(
                x = (bounds.max.x - bounds.min.x) * scale,
                y = (bounds.max.y - bounds.min.y) * scale,
                z = (bounds.max.z - bounds.min.z) * scale,
            )
        val radius = sqrt(size.x * size.x + size.y * size.y + size.z * size.z) * 0.5f
        val distance = max(radius * FrameDistanceMultiplier, MinFrameDistance)
        val position =
            Vec3(
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
        context.logger.info(TAG) { "ModelViewer framed model='${state.model.path}' radius=${"%.3f".format(radius)}" }
    }

    companion object {
        private const val TAG = "ModelViewerOperations"
        private const val MinScale = 0.0001f
        private const val FrameDistanceMultiplier = 2.5f
        private const val MinFrameDistance = 2f
        private const val FrameHeightOffsetRatio = 0.15f
        private const val MinFrameHeightOffset = 0.25f
        private val DefaultCameraPosition = Vec3(0f, 2f, 6f)
        private val DefaultCameraEulerDegrees = Vec3(-10f, 180f, 0f)
    }
}
