package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorConfig

class EnvironmentPreviewState {
    var showSkybox: Boolean = true
    var autoRotate: Boolean = false
    var cameraDistance: Float = EnvironmentEditorConfig.defaultCameraDistance
    var cameraYawDegrees: Float = EnvironmentEditorConfig.defaultCameraYawDegrees
    var cameraPitchDegrees: Float = EnvironmentEditorConfig.defaultCameraPitchDegrees
    var previewStatusMessage: String? = null
}
