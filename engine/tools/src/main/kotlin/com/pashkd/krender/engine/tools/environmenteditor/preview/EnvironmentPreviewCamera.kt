package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig
import kotlin.math.cos
import kotlin.math.sin

object EnvironmentPreviewCamera {
    val FocusTarget = Vec3(0f, 0.65f, 0.1f)

    fun reset(state: EnvironmentPreviewState) {
        state.cameraDistance = EnvironmentEditorConfig.defaultCameraDistance
        state.cameraYawDegrees = EnvironmentEditorConfig.defaultCameraYawDegrees
        state.cameraPitchDegrees = EnvironmentEditorConfig.defaultCameraPitchDegrees
    }

    fun orbitPosition(state: EnvironmentPreviewState): Vec3 {
        val yaw = Math.toRadians(state.cameraYawDegrees.toDouble())
        val pitch = Math.toRadians(state.cameraPitchDegrees.toDouble())
        val distance = state.cameraDistance.coerceIn(MinDistance, MaxDistance)
        val offsetX = (sin(yaw) * cos(pitch)).toFloat() * distance
        val offsetY = sin(pitch).toFloat() * distance
        val offsetZ = (cos(yaw) * cos(pitch)).toFloat() * distance
        return Vec3(
            FocusTarget.x + offsetX,
            FocusTarget.y + offsetY,
            FocusTarget.z + offsetZ,
        )
    }

    private const val MinDistance = 2f
    private const val MaxDistance = 20f
}
