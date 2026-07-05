package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Shared cubemap direction helpers used by Environment-generation workflows.
 *
 * The main responsibility of this object is to convert a pixel coordinate on one cubemap face into
 * a normalized 3D lookup direction. Keeping this math in one place ensures that skybox, irradiance,
 * and radiance generators all project faces consistently.
 */
object CubemapDirectionMath {
    /**
     * Converts a pixel on a cubemap face into a normalized world-space direction vector.
     *
     * The pixel center is first remapped into the canonical `[-1, 1]` face-local range and then
     * projected onto the requested cubemap face. The resulting vector is normalized so later
     * sampling code can safely interpret it as a direction.
     */
    fun cubemapFacePixelToDirection(
        face: EnvironmentCubemapFace,
        x: Int,
        y: Int,
        size: Int,
    ): Vec3 {
        require(size > 0) { "Cubemap face size must be positive." }

        // Sample at the center of the target texel so the projection stays symmetric and avoids
        // a half-pixel shift at the cubemap face borders.
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

    /**
     * Returns a unit-length direction vector.
     *
     * A tiny epsilon is used when computing the reciprocal length so accidental zero-length input
     * does not produce `NaN` or `Infinity` and destabilize later UV projection math.
     */
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

/**
 * Helpers for mapping normalized directions into an equirectangular texture domain.
 *
 * Environment HDR/EXR files are commonly authored in latitude-longitude layout, so generators use
 * this conversion after cubemap lookup directions are produced.
 */
object EquirectangularProjection {
    /**
     * Converts a normalized direction into equirectangular UV coordinates.
     *
     * `theta` is the horizontal longitude angle around the horizon and `phi` is the vertical
     * latitude angle. Those spherical angles are then remapped into the standard `[0, 1]` texture
     * coordinate range used by equirectangular environment maps.
     */
    fun directionToEquirectangularUv(direction: Vec3): Vec2 {
        val theta = atan2(direction.z, direction.x)
        val phi = asin(direction.y.coerceIn(-1f, 1f))
        val u = 0.5f + (theta / (2f * PI.toFloat()))
        val v = 0.5f - (phi / PI.toFloat())
        return Vec2(u, v)
    }
}
