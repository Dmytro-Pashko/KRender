package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared sampling and vector math for Environment IBL generation.
 *
 * The editor keeps this math local to the HDR generation package so diffuse and specular cubemap
 * generators stay deterministic and consistent without expanding the global engine math surface.
 */
object EnvironmentSamplingMath {
    fun dot(
        a: Vec3,
        b: Vec3,
    ): Float = a.x * b.x + a.y * b.y + a.z * b.z

    fun cross(
        a: Vec3,
        b: Vec3,
    ): Vec3 =
        Vec3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x,
        )

    fun normalize(direction: Vec3): Vec3 {
        val length =
            sqrt(
                direction.x * direction.x +
                    direction.y * direction.y +
                    direction.z * direction.z,
            ).coerceAtLeast(1e-8f)
        return Vec3(direction.x / length, direction.y / length, direction.z / length)
    }

    fun reflect(
        incident: Vec3,
        normal: Vec3,
    ): Vec3 {
        val scale = 2f * dot(incident, normal)
        return incident - normal * scale
    }

    /**
     * Builds a stable orthonormal basis around [normal].
     *
     * A world-up fallback avoids near-parallel cross products when the normal points close to the
     * pole of the initially chosen helper axis.
     */
    fun buildBasis(normal: Vec3): Basis {
        val up = if (kotlin.math.abs(normal.y) < 0.999f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val tangent = normalize(cross(up, normal))
        val bitangent = normalize(cross(normal, tangent))
        return Basis(tangent = tangent, bitangent = bitangent, normal = normal)
    }

    /**
     * Cosine-weighted hemisphere sample around [normal].
     *
     * The local sample is transformed through the orthonormal basis so Monte Carlo irradiance
     * integration matches the current cubemap texel direction.
     */
    fun cosineWeightedHemisphereDirection(
        normal: Vec3,
        sampleIndex: Int,
        sampleCount: Int,
        sequenceOffset: Vec2,
    ): Vec3 {
        val baseXi = hammersley(sampleIndex, sampleCount)
        val xi = Vec2(fract(baseXi.x + sequenceOffset.x), fract(baseXi.y + sequenceOffset.y))
        val radius = sqrt(xi.x)
        val phi = (2.0 * PI * xi.y).toFloat()
        val x = radius * cos(phi)
        val y = radius * sin(phi)
        val z = sqrt(max(0f, 1f - xi.x))
        return localToWorld(buildBasis(normal), Vec3(x, y, z))
    }

    fun sampleSequenceOffset(
        faceIndex: Int,
        x: Int,
        y: Int,
    ): Vec2 {
        val seed = hash(faceIndex * 0x9E3779B9.toInt() xor x * 0x85EBCA6B.toInt() xor y * 0xC2B2AE35.toInt())
        return Vec2(unitFloat(seed), unitFloat(hash(seed xor 0x27D4EB2D)))
    }

    /**
     * GGX importance-sampled reflection direction around [reflectionDirection].
     *
     * The current implementation uses the common `N = V = R` approximation for environment-map
     * prefiltering, which is sufficient for offline authoring of the runtime mip chain.
     */
    fun ggxImportanceSampleDirection(
        reflectionDirection: Vec3,
        roughness: Float,
        sampleIndex: Int,
        sampleCount: Int,
    ): Vec3 {
        val xi = hammersley(sampleIndex, sampleCount)
        val alpha = (roughness * roughness).coerceAtLeast(1e-4f)
        val phi = (2.0 * PI * xi.x).toFloat()
        val cosTheta =
            sqrt(
                ((1f - xi.y) / (1f + (alpha * alpha - 1f) * xi.y))
                    .coerceIn(0f, 1f),
            )
        val sinTheta = sqrt((1f - cosTheta * cosTheta).coerceAtLeast(0f))
        val halfVectorLocal =
            Vec3(
                sinTheta * cos(phi),
                sinTheta * sin(phi),
                cosTheta,
            )
        val basis = buildBasis(reflectionDirection)
        val halfVector = localToWorld(basis, halfVectorLocal)
        val incident = reflectionDirection * -1f
        return normalize(reflect(incident, halfVector))
    }

    private fun localToWorld(
        basis: Basis,
        local: Vec3,
    ): Vec3 =
        normalize(
            basis.tangent * local.x +
                basis.bitangent * local.y +
                basis.normal * local.z,
        )

    /**
     * Low-discrepancy 2D sample point used for deterministic Monte Carlo integration.
     */
    private fun hammersley(
        index: Int,
        count: Int,
    ): Vec2 = Vec2(index.toFloat() / count.coerceAtLeast(1).toFloat(), radicalInverseVdc(index))

    private fun fract(value: Float): Float = value - kotlin.math.floor(value)

    private fun hash(value: Int): Int {
        var mixed = value
        mixed = (mixed xor (mixed ushr 16)) * 0x7FEB352D
        mixed = (mixed xor (mixed ushr 15)) * 0x846CA68B.toInt()
        return mixed xor (mixed ushr 16)
    }

    private fun unitFloat(value: Int): Float = value.toUInt().toFloat() * 2.3283064e-10f

    private fun radicalInverseVdc(bits: Int): Float {
        var value = bits
        value = (value shl 16) or (value ushr 16)
        value = ((value and 0x55555555) shl 1) or ((value and 0xAAAAAAAA.toInt()) ushr 1)
        value = ((value and 0x33333333) shl 2) or ((value and 0xCCCCCCCC.toInt()) ushr 2)
        value = ((value and 0x0F0F0F0F) shl 4) or ((value and 0xF0F0F0F0.toInt()) ushr 4)
        value = ((value and 0x00FF00FF) shl 8) or ((value and 0xFF00FF00.toInt()) ushr 8)
        return value.toUInt().toFloat() * 2.3283064e-10f
    }
}

data class Basis(
    val tangent: Vec3,
    val bitangent: Vec3,
    val normal: Vec3,
)
