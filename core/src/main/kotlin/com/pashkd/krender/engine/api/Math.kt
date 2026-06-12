package com.pashkd.krender.engine.api

/**
 * Mutable two-dimensional float vector used for screen-space math and UI coordinates.
 */
data class Vec2(
    /** Horizontal component. */
    var x: Float = 0f,
    /** Vertical component. */
    var y: Float = 0f,
) {
    companion object {
        /** Returns a zero vector. */
        fun zero(): Vec2 = Vec2()
    }
}

/**
 * Mutable three-dimensional float vector used for world-space math.
 */
data class Vec3(
    /** X component. */
    var x: Float = 0f,
    /** Y component. */
    var y: Float = 0f,
    /** Z component. */
    var z: Float = 0f,
) {
    /** Replaces all three vector components at once. */
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    /** Returns the component-wise sum of two vectors. */
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    /** Returns the component-wise difference of two vectors. */
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    /** Returns this vector scaled by a scalar factor. */
    operator fun times(scale: Float): Vec3 = Vec3(x * scale, y * scale, z * scale)

    companion object {
        /** Returns a zero vector. */
        fun zero(): Vec3 = Vec3()

        /** Returns a vector with every component set to one. */
        fun one(): Vec3 = Vec3(1f, 1f, 1f)
    }
}

/**
 * Mutable quaternion used to represent orientation.
 */
data class Quat(
    /** X imaginary component. */
    var x: Float = 0f,
    /** Y imaginary component. */
    var y: Float = 0f,
    /** Z imaginary component. */
    var z: Float = 0f,
    /** Real component. */
    var w: Float = 1f,
) {
    companion object {
        /** Returns the identity quaternion. */
        fun identity(): Quat = Quat()
    }
}

/**
 * Mutable RGBA color in normalized float space.
 */
data class Color(
    /** Red channel. */
    var r: Float = 1f,
    /** Green channel. */
    var g: Float = 1f,
    /** Blue channel. */
    var b: Float = 1f,
    /** Alpha channel. */
    var a: Float = 1f,
) {
    companion object {
        /** Returns opaque white. */
        fun white(): Color = Color(1f, 1f, 1f, 1f)
    }
}
