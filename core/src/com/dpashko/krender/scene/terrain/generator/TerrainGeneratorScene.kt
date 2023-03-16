package com.dpashko.krender.scene.terrain.generator

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.skin.SkinProvider
import javax.inject.Inject

class TerrainGeneratorScene @Inject constructor(
    controller: TerrainGeneratorController,
    private val navigator: TerrainGeneratorNavigator,
) :
    BaseScene<TerrainGeneratorController, TerrainGeneratorResult>(controller) {

    private lateinit var ui: TerrainGeneratorUiStage

    override fun create() {
        super.create()
        ui = TerrainGeneratorUiStage(
            state = controller.getState(),
            skin = SkinProvider.default
        )
        println("TerrainGenerator initialized.")
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        ui.act(deltaTime)
    }

    override fun render() {
        ui.draw()
    }

    override fun pause() {
        println("TerrainGenerator paused.")
    }

    override fun resume() {
        println("TerrainGenerator resumed.")
    }

    override fun resize(width: Int, height: Int) {
        println("TerrainGenerator scene resized: w=$width, h=$height")
    }
}