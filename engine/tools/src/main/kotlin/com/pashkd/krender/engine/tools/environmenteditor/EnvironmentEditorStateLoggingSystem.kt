package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.assets.environment.Environment

/**
 * Logs semantic editor-state transitions once, rather than emitting per-frame noise.
 *
 * A compact immutable snapshot makes changes comparable even though Environment assets
 * are replaced with immutable copies while the user edits settings.
 */
class EnvironmentEditorStateLoggingSystem(
    private val state: EnvironmentEditorState,
    private val logger: Logger,
) : System() {
    private var lastSnapshot: EnvironmentEditorLogSnapshot? = null

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val snapshot = EnvironmentEditorLogSnapshot.capture(state)
        val previous = lastSnapshot
        if (snapshot == previous) return
        lastSnapshot = snapshot
        if (previous == null) {
            logger.info(TAG) { "Environment Editor state initialized ${snapshot.describe()}" }
        } else {
            logger.info(TAG) { "Environment Editor state changed ${snapshot.diff(previous)}" }
        }
    }

    companion object {
        private const val TAG = "EnvironmentEditorState"
    }
}

private data class EnvironmentEditorLogSnapshot(
    val dirty: Boolean,
    val environmentCacheRevision: Long,
    val loadError: String?,
    val validationStatus: String?,
    val backgroundMode: String?,
    val backgroundColor: String?,
    val autoRotate: Boolean,
    val exposure: Float?,
    val rotationDegrees: Float?,
    val diffuseIntensity: Float?,
    val specularIntensity: Float?,
    val hasSkybox: Boolean,
    val skyboxFaceCount: Int,
    val hasIrradiance: Boolean,
    val radianceMipCount: Int,
    val hasBrdfLut: Boolean,
) {
    fun describe(): String =
        "dirty=$dirty cacheRevision=$environmentCacheRevision loadError=$loadError validation=$validationStatus backgroundMode=$backgroundMode " +
            "backgroundColor=$backgroundColor autoRotate=$autoRotate exposure=$exposure " +
            "rotation=$rotationDegrees diffuse=$diffuseIntensity specular=$specularIntensity " +
            "hasSkybox=$hasSkybox skyboxFaceCount=$skyboxFaceCount hasIrradiance=$hasIrradiance radianceMipCount=$radianceMipCount hasBrdfLut=$hasBrdfLut"

    fun diff(previous: EnvironmentEditorLogSnapshot): String =
        "dirty ${previous.dirty} -> $dirty; " +
            "cacheRevision ${previous.environmentCacheRevision} -> $environmentCacheRevision; " +
            "validation ${previous.validationStatus} -> $validationStatus; " +
            "backgroundMode ${previous.backgroundMode} -> $backgroundMode; " +
            "backgroundColor ${previous.backgroundColor} -> $backgroundColor; " +
            "autoRotate ${previous.autoRotate} -> $autoRotate; " +
            "exposure ${previous.exposure} -> $exposure; " +
            "rotation ${previous.rotationDegrees} -> $rotationDegrees; " +
            "diffuse ${previous.diffuseIntensity} -> $diffuseIntensity; " +
            "specular ${previous.specularIntensity} -> $specularIntensity; " +
            "hasSkybox ${previous.hasSkybox} -> $hasSkybox; " +
            "skyboxFaceCount ${previous.skyboxFaceCount} -> $skyboxFaceCount; " +
            "hasIrradiance ${previous.hasIrradiance} -> $hasIrradiance; " +
            "radianceMipCount ${previous.radianceMipCount} -> $radianceMipCount; " +
            "hasBrdfLut ${previous.hasBrdfLut} -> $hasBrdfLut; " +
            "loadError ${previous.loadError} -> $loadError"

    companion object {
        fun capture(state: EnvironmentEditorState): EnvironmentEditorLogSnapshot {
            val environment = state.environment
            return EnvironmentEditorLogSnapshot(
                dirty = state.dirty,
                environmentCacheRevision = state.environmentCacheRevision,
                loadError = state.loadError,
                validationStatus = state.validation?.status?.name,
                backgroundMode = environment?.settings?.backgroundMode?.name,
                backgroundColor = environment.backgroundColorString(),
                autoRotate = state.previewState.autoRotate,
                exposure = environment?.settings?.exposure,
                rotationDegrees = environment?.settings?.rotationDegrees,
                diffuseIntensity = environment?.settings?.diffuseIntensity,
                specularIntensity = environment?.settings?.specularIntensity,
                hasSkybox = environment?.skybox != null,
                skyboxFaceCount = environment?.skybox?.faces?.size ?: 0,
                hasIrradiance = environment?.irradiance != null,
                radianceMipCount = environment?.radiance?.mips?.size ?: 0,
                hasBrdfLut = environment?.brdfLut != null,
            )
        }
    }
}

private fun Environment?.backgroundColorString(): String? {
    val color = this?.settings?.backgroundColor ?: return null
    return "(${color.r},${color.g},${color.b},${color.a})"
}
