package com.dpashko.krender.scene.terrain.generator

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table

class TerrainGeneratorUiStage(
    state: TerrainGeneratorState,
    skin: Skin
) : Stage() {
    private var generateButton = Button(Label("Generate", skin), skin)
    private var exitButton = Button(Label("Exit", skin), skin)

    init {
        addActor(
            Table(skin).apply {
                row()
                add(generateButton).space(20f)
                add(exitButton)
                debugAll()
                setFillParent(true)
            })
    }
}
