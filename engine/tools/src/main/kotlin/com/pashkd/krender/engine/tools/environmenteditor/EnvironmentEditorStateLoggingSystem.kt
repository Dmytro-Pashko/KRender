package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset

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
    val loadError: String?,
    val backgroundMode: String?,
    val backgroundColor: String?,
    val autoRotate: Boolean,
    val exposure: Float?,
    val rotationDegrees: Float?,
    val diffuseIntensity: Float?,
    val specularIntensity: Float?,
) {
    fun describe(): String =
        "dirty=$dirty loadError=$loadError backgroundMode=$backgroundMode " +
            "backgroundColor=$backgroundColor autoRotate=$autoRotate exposure=$exposure " +
            "rotation=$rotationDegrees diffuse=$diffuseIntensity specular=$specularIntensity"

    fun diff(previous: EnvironmentEditorLogSnapshot): String =
        "dirty ${previous.dirty} -> $dirty; " +
            "backgroundMode ${previous.backgroundMode} -> $backgroundMode; " +
            "backgroundColor ${previous.backgroundColor} -> $backgroundColor; " +
            "autoRotate ${previous.autoRotate} -> $autoRotate; " +
            "exposure ${previous.exposure} -> $exposure; " +
            "rotation ${previous.rotationDegrees} -> $rotationDegrees; " +
            "diffuse ${previous.diffuseIntensity} -> $diffuseIntensity; " +
            "specular ${previous.specularIntensity} -> $specularIntensity; " +
            "loadError ${previous.loadError} -> $loadError"

    companion object {
        fun capture(state: EnvironmentEditorState): EnvironmentEditorLogSnapshot {
            val environment = state.environment
            return EnvironmentEditorLogSnapshot(
                dirty = state.dirty,
                loadError = state.loadError,
                backgroundMode = environment?.settings?.backgroundMode?.name,
                backgroundColor = environment.backgroundColorString(),
                autoRotate = state.previewState.autoRotate,
                exposure = environment?.settings?.exposure,
                rotationDegrees = environment?.settings?.rotationDegrees,
                diffuseIntensity = environment?.settings?.diffuseIntensity,
                specularIntensity = environment?.settings?.specularIntensity,
            )
        }
    }
}

private fun EnvironmentAsset?.backgroundColorString(): String? {
    val color = this?.settings?.backgroundColor ?: return null
    return "(${color.r},${color.g},${color.b},${color.a})"
}
