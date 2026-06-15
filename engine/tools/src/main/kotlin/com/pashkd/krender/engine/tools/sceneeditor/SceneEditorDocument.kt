package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.scene.SceneDescriptor

/**
 * Editable scene document owned by the Scene Editor.
 */
class SceneEditorDocument(
    var world: SceneWorld,
    var descriptor: SceneDescriptor? = null,
)
