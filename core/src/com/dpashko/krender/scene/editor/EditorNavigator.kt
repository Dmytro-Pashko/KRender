package com.dpashko.krender.scene.editor

import com.dpashko.krender.scene.editor.model.EditorResult
import com.dpashko.krender.scene.navigator.Navigator
import javax.inject.Inject

class EditorNavigator @Inject constructor(
    private val globalNavigator: Navigator<Any>
) {

    fun generateTerrain() {
        globalNavigator.navigateTo(EditorResult.GENERATE_TERRAIN)
    }
}
