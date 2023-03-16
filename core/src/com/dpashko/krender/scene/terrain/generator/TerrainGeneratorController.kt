package com.dpashko.krender.scene.terrain.generator

import com.dpashko.krender.scene.common.SceneController
import javax.inject.Inject

class TerrainGeneratorController @Inject constructor() : SceneController<TerrainGeneratorState> {

    private lateinit var state: TerrainGeneratorState

    override fun init() {
        state = TerrainGeneratorState()
        println("TerrainGeneratorController initialized.")
    }

    override fun update(deltaTime: Float) {
    }

    override fun getState(): TerrainGeneratorState = state

    override fun dispose() {
        println("TerrainGeneratorController disposed.")
    }
}
