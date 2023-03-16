package com.dpashko.krender.scene

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.editor.EditorScene
import com.dpashko.krender.scene.terrain.generator.TerrainGeneratorScene
import javax.inject.Provider

class SceneFactory(
    private val sceneProviders: MutableMap<Class<*>, Provider<BaseScene<*, *>>>
) {

    fun getEditorScene(): BaseScene<*, *> {
        return sceneProviders[EditorScene::class.java]!!.get()
    }

    fun getTerrainGeneratorScene(): BaseScene<*, *> {
        return sceneProviders[TerrainGeneratorScene::class.java]!!.get()
    }
}
