package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets

/**
 * Editor tool scene for inspecting, editing, and previewing Environment assets.
 *
 * Opened from Asset Browser for `.environment.json` manifests. Full panel wiring
 * is added incrementally in later steps.
 */
class EnvironmentEditorScene(
    val environmentPath: String,
) : Scene("environment_editor") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    override fun show() {
        engine.logger.info(TAG) { "EnvironmentEditor show environmentPath='$environmentPath'" }
    }

    companion object {
        private const val TAG = "EnvironmentEditorScene"
    }
}
