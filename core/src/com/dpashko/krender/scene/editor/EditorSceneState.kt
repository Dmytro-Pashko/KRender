/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender.scene.editor

import com.dpashko.krender.scene.common.SceneState

class EditorSceneState(
    val isLoading: Boolean = false
) : SceneState() {

    override fun getObjectForPersistence(): ByteArray {
        TODO("Not yet implemented")
    }
}
