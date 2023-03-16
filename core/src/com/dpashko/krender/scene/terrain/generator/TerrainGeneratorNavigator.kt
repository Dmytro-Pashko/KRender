package com.dpashko.krender.scene.terrain.generator

import com.dpashko.krender.scene.editor.EditorResult
import com.dpashko.krender.scene.navigator.Navigator
import javax.inject.Inject

class TerrainGeneratorNavigator @Inject constructor(
    private val globalNavigator: Navigator<Any>
) {

    fun exit() {
        globalNavigator.navigateTo(EditorResult.GENERATE_TERRAIN)
    }
}