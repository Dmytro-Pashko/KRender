package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig

/** Mutable camera controls that are intentionally not persisted in the Environment manifest. */
class EnvironmentPreviewState {
    var autoRotate: Boolean = false
    var cameraDistance: Float = EnvironmentEditorConfig.defaultCameraDistance
    var cameraYawDegrees: Float = EnvironmentEditorConfig.defaultCameraYawDegrees
    var cameraPitchDegrees: Float = EnvironmentEditorConfig.defaultCameraPitchDegrees
}
