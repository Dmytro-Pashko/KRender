package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.game.ModelViewerScene

class Main(
    modelPath: String = defaultModelPath(),
) : GdxEngineApplication(
    initialScene = {
        ModelViewerScene(
            model = AssetRef.model(modelPath),
        )
    },
) {
    companion object {
        fun defaultModelPath(): String = System.getProperty("krender.model", "model/m_actor_human_0.glb")
    }
}
