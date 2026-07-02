package com.pashkd.krender.engine.tools.environmenteditor.preview

class EnvironmentPreviewState {
    var mode: EnvironmentPreviewMode = EnvironmentPreviewMode.MaterialSpheres
    var showSkybox: Boolean = true
    var showGround: Boolean = true
    var autoRotate: Boolean = false
    var cameraDistance: Float = 6f
    var cameraYawDegrees: Float = 35f
    var cameraPitchDegrees: Float = 20f
    var previewStatusMessage: String? = null
}
