package com.dpashko.krender.scene.terrain.generator

import com.dpashko.krender.scene.common.SceneState

class TerrainGeneratorState : SceneState() {

    override fun getObjectForPersistence(): ByteArray {
        return ByteArray(0)
    }
}
