package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping

object EnvironmentLdrToneMapper {
    fun prepare(
        color: Vec3,
        exposure: Float,
        toneMapping: EnvironmentToneMapping,
    ): Vec3 = toneMap(color * exposure, toneMapping)

    private fun toneMap(
        color: Vec3,
        mode: EnvironmentToneMapping,
    ): Vec3 =
        when (mode) {
            EnvironmentToneMapping.None -> color
            EnvironmentToneMapping.Reinhard ->
                Vec3(
                    color.x / (1f + color.x),
                    color.y / (1f + color.y),
                    color.z / (1f + color.z),
                )
            EnvironmentToneMapping.ACES -> Vec3(aces(color.x), aces(color.y), aces(color.z))
        }

    private fun aces(value: Float): Float {
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        return ((value * (a * value + b)) / (value * (c * value + d) + e)).coerceIn(0f, 1f)
    }
}
