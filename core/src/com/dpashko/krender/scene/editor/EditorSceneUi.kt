package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align

class EditorSceneUi(state: EditorSceneState, skin: Skin) : Stage() {

    private var table = Table(skin)
    private var gridSizeLabel = Label("Grid size: ", skin)
    private var fpsLabel = Label("FPS : ", skin)
    private var fpsValue = Label("", skin)
    private var drawGridCheckBox = CheckBox("Draw grid", skin)
    private var drawAxisCheckBox = CheckBox("Draw axis", skin)
    private var wireFrameModeCheckBox = CheckBox("Wireframe Mode", skin)

    init {
        addActor(initUI(state))
        Gdx.input.inputProcessor = this
    }

    private fun initUI(state: EditorSceneState) =
        table.apply {
            add(Table().apply {
                right()
                add(drawGridCheckBox.apply {
                    isChecked = false
                }).padLeft(10f)
                add(gridSizeLabel)
                    .padLeft(10f)
                add(wireFrameModeCheckBox.apply {
                    isChecked = false
                }).padLeft(10f)
                add(drawAxisCheckBox.apply {
                    isChecked = false
                }).padLeft(10f).padRight(10f)
            }).fillX().align(Align.top).expand()
            row()
            add(Table().apply {
                right()
                add(fpsLabel).padRight(10f)
                add(fpsValue).padRight(10f)
            }).fillX().align(Align.bottom).expand()
            setFillParent(true)
        }

    override fun draw() {
        fpsValue.setText(Gdx.graphics.framesPerSecond)
        super.draw()
    }
}
