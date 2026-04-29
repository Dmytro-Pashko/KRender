package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.SceneWorld

/**
 * Editable scene document owned by the Scene Editor.
 */
class SceneEditorDocument(
    var world: SceneWorld,
    var descriptor: SceneDescriptor? = null,
)

