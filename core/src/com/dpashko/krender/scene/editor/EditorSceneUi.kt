package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import java.text.NumberFormat

class EditorSceneUi(private val controller: EditorSceneController, skin: Skin) : Stage() {

    private var table = Table(skin)
    private var gridSizeLabel = Label("Grid size: ", skin)
    private var fpsLabel = Label("FPS:", skin)
    private var cameraPositionLabel = Label("Cam.Position:", skin)
    private var cameraPositionValue = Label("", skin)
    private var targetPositionLabel = Label("Target:", skin)
    private var targetPositionValue = Label("", skin)
    private var distanceLabel = Label("Distance:", skin)
    private var distanceValue = Label("", skin)
    private var fpsValue = Label("", skin)
    private var drawGridCheckBox = CheckBox("Draw grid", skin)
    private var drawAxisCheckBox = CheckBox("Draw axis", skin)
    private var wireFrameModeCheckBox = CheckBox("Wireframe Mode", skin)

    private var hintLabel =
        Label(
            "Navigation: \nW=forward\nS=backward\nA=left\nD=right\nMid.Mouse=rotate\nWheel=Zoom",
            skin
        )

    private val floatValueFormatter = NumberFormat.getInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    init {
        addActor(initUI(controller.getState()))
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
            }).fillX().top()

            row()
            add(Table().apply {
                add(hintLabel)
            }).expand().align(Align.right)

            row()
            add(Table().apply {
                left()
                // Camera Position.
                add(cameraPositionLabel)
                add(cameraPositionValue).padRight(10f)
                // Camera Target Point.
                add(targetPositionLabel)
                add(targetPositionValue).padRight(10f)
                // Distance from Camera to Target Point.
                add(distanceLabel)
                add(distanceValue).padRight(10f)
                add().expand()
                // FPS value.
                add(fpsLabel)
                add(fpsValue).padRight(10f)
            }).fillX().bottom()
            setFillParent(true)
        }

    override fun draw() {
        val fps = Gdx.graphics.framesPerSecond
        val cameraPosition = controller.getState().camera.position
        val intersectionPoint = controller.getState().intersectionPoint
        val distance = intersectionPoint.dst(cameraPosition)
        fpsValue.setText(fps)
        cameraPositionValue.setText(
            "[${floatValueFormatter.format(cameraPosition.x)}:" +
                "${floatValueFormatter.format(cameraPosition.y)}:" +
                "${floatValueFormatter.format(cameraPosition.z)}]"
        )
        targetPositionValue.setText(
            "[${floatValueFormatter.format(intersectionPoint.x)}:" +
                "${floatValueFormatter.format(intersectionPoint.y)}:" +
                "${floatValueFormatter.format(intersectionPoint.z)}]"
        )
        distanceValue.setText("${floatValueFormatter.format(distance)}")
        super.draw()
    }
}
