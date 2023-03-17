package com.dpashko.krender.scene.terrain.generator

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.skin.SkinProvider
import javax.inject.Inject

class TerrainGeneratorScene @Inject constructor(
    controller: TerrainGeneratorController,
    private val navigator: TerrainGeneratorNavigator,
) :
    BaseScene<TerrainGeneratorController, TerrainGeneratorResult>(controller),
    TerrainGeneratorInterfaceListener {

    private lateinit var ui: TerrainGeneratorUiStage

    override fun create() {
        super.create()
        println("TerrainGenerator initialization.")
        ui = TerrainGeneratorUiStage(
            skin = SkinProvider.default,
            listener = this
        )
        Gdx.input.inputProcessor = InputMultiplexer().apply {
            addProcessor(ui)
        }
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

    override fun onExitClicked() {
        println("Exit clicked.")
        navigator.exit()
    }

    override fun dispose() {
        ui.dispose()
        super.dispose()
    }
}