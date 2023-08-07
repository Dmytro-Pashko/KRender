package com.dpashko.krender.scene.editor.model

/**
 * Model that represents performance related data.
 */
class PerformanceState(
    val fps: Int = 0,
    val usedMemory: Long = 0,
    val totalMemory: Long = 0,
) {

    override fun toString(): String {
        return "PerformanceState(fps=$fps, usedMemory=$usedMemory, totalMemory=$totalMemory)"
    }
}
