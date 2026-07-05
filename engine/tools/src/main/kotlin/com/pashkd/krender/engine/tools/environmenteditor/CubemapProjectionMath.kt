package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

object CubemapDirectionMath {
    fun cubemapFacePixelToDirection(
        face: EnvironmentCubemapFace,
        x: Int,
        y: Int,
        size: Int,
    ): Vec3 {
        require(size > 0) { "Cubemap face size must be positive." }
        val u = 2f * ((x + 0.5f) / size.toFloat()) - 1f
        val v = 2f * ((y + 0.5f) / size.toFloat()) - 1f
        val direction =
            when (face) {
                EnvironmentCubemapFace.PosX -> Vec3(1f, -v, -u)
                EnvironmentCubemapFace.NegX -> Vec3(-1f, -v, u)
                EnvironmentCubemapFace.PosY -> Vec3(u, 1f, v)
                EnvironmentCubemapFace.NegY -> Vec3(u, -1f, -v)
                EnvironmentCubemapFace.PosZ -> Vec3(u, -v, 1f)
                EnvironmentCubemapFace.NegZ -> Vec3(-u, -v, -1f)
            }
        return normalize(direction)
    }

    private fun normalize(direction: Vec3): Vec3 {
        val length =
            sqrt(
                direction.x * direction.x +
                    direction.y * direction.y +
                    direction.z * direction.z,
            ).coerceAtLeast(1e-8f)
        return Vec3(direction.x / length, direction.y / length, direction.z / length)
    }
}

object EquirectangularProjection {
    fun directionToEquirectangularUv(direction: Vec3): Vec2 {
        val theta = atan2(direction.z, direction.x)
        val phi = asin(direction.y.coerceIn(-1f, 1f))
        val u = 0.5f + (theta / (2f * PI.toFloat()))
        val v = 0.5f - (phi / PI.toFloat())
        return Vec2(u, v)
    }
}
