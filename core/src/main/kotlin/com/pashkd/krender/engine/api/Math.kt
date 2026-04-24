package com.pashkd.krender.engine.api

data class Vec2(
    var x: Float = 0f,
    var y: Float = 0f,
) {
    companion object {
        fun zero(): Vec2 = Vec2()
    }
}

data class Vec3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
) {
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    companion object {
        fun zero(): Vec3 = Vec3()
        fun one(): Vec3 = Vec3(1f, 1f, 1f)
    }
}

data class Quat(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f,
) {
    companion object {
        fun identity(): Quat = Quat()
    }
}

data class Color(
    var r: Float = 1f,
    var g: Float = 1f,
    var b: Float = 1f,
    var a: Float = 1f,
) {
    companion object {
        fun white(): Color = Color(1f, 1f, 1f, 1f)
    }
}
