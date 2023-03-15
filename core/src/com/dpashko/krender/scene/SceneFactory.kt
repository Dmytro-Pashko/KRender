package com.dpashko.krender.scene

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.editor.EditorScene
import javax.inject.Provider

class SceneFactory(
    private val sceneProviders: MutableMap<Class<*>, Provider<BaseScene<*>>>
) {

    fun getEntryPointScene(): BaseScene<*> {
        return sceneProviders[EditorScene::class.java]!!.get()
    }
}
