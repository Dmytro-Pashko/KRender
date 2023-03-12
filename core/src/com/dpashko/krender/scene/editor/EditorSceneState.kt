/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender.scene.editor

import com.badlogic.gdx.math.Vector2
import com.dpashko.krender.scene.common.SceneState

class EditorSceneState(
    var screenWidth: Int,
    var screenHeight: Int
) : SceneState() {

    var position = Vector2(0f, 0f)
    val direction: Vector2 = Vector2().setToRandomDirection()
    var imageSize = 100f
    val velocity = 150f

    override fun getObjectForPersistence(): ByteArray {
        TODO("Not yet implemented")
    }
}
