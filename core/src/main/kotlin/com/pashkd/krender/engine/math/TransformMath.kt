package com.pashkd.krender.engine.math

import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Transforms one local-space point by the engine transform component using the same
 * scale -> Euler rotation -> translation order used by current editor/debug helpers.
 */
fun transformLocalPoint(point: Vec3, transform: TransformComponent): Vec3 {
    val scaled = Vec3(
        point.x * transform.scale.x,
        point.y * transform.scale.y,
        point.z * transform.scale.z,
    )
    val rotated = rotateEulerDegrees(scaled, transform.eulerDegrees)
    return transform.position + rotated
}

private fun rotateEulerDegrees(point: Vec3, eulerDegrees: Vec3): Vec3 {
    val pitch = Math.toRadians(eulerDegrees.x.toDouble()).toFloat()
    val yaw = Math.toRadians(eulerDegrees.y.toDouble()).toFloat()
    val roll = Math.toRadians(eulerDegrees.z.toDouble()).toFloat()

    val cosX = cos(pitch)
    val sinX = sin(pitch)
    val afterX = Vec3(
        point.x,
        point.y * cosX - point.z * sinX,
        point.y * sinX + point.z * cosX,
    )

    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val afterY = Vec3(
        afterX.x * cosY + afterX.z * sinY,
        afterX.y,
        -afterX.x * sinY + afterX.z * cosY,
    )

    val cosZ = cos(roll)
    val sinZ = sin(roll)
    return Vec3(
        afterY.x * cosZ - afterY.y * sinZ,
        afterY.x * sinZ + afterY.y * cosZ,
        afterY.z,
    )
}

