package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import java.text.NumberFormat

class EditorUiStage(
    private val listener: EditorSceneInterfaceListener,
    state: EditorState,
    skin: Skin
) :
    Stage(),
    EventListener {

    private var table = Table(skin)
    private var drawGridCheckBox = CheckBox("Draw grid", skin)

    private var sceneSizeLabel = Label("Scene size: ", skin)
    private var sceneSizeSelectBox = SelectBox<EditorState.SceneSize>(skin)

    private var drawAxisCheckBox = CheckBox("Draw axis", skin)

    private var cameraPositionLabel = Label("Cam.Position:", skin)
    private var cameraPositionValue = Label("", skin)

    private var targetPositionLabel = Label("Target:", skin)
    private var targetPositionValue = Label("", skin)

    private var distanceLabel = Label("Distance:", skin)
    private var distanceValue = Label("", skin)

    private var fpsLabel = Label("FPS:", skin)
    private var fpsValue = Label("", skin)

    private var generateTerrainButton = Button(Label("Generate Terrain", skin), skin)

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
        addActor(
            table.apply {
                add(Table().apply {
                    right()
                    // Draw Grid Checkbox.
                    add(drawGridCheckBox.apply {
                        isChecked = state.drawGrid
                        addListener(this@EditorUiStage)
                    }).padLeft(10f)
                    // Grid Size selector.
                    add(sceneSizeLabel)
                        .padLeft(10f)
                    add(sceneSizeSelectBox.apply {
                        items = Array(EditorState.SceneSize.values())
                        selected = state.sceneSize
                        addListener(this@EditorUiStage)
                    })
                    add(drawAxisCheckBox.apply {
                        isChecked = state.drawAxis
                        addListener(this@EditorUiStage)
                    }).padRight(10f)
                    add(generateTerrainButton).apply {
                        addListener(this@EditorUiStage)
                    }.padRight(10f)
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
            },
        )
    }

    fun draw(state: EditorState) {
        val fps = Gdx.graphics.framesPerSecond
        val cameraPosition = state.camera.position
        val intersectionPoint = state.target
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
        distanceValue.setText(floatValueFormatter.format(distance))
        super.draw()
    }

    override fun handle(event: Event?): Boolean {
        if (event !is ChangeListener.ChangeEvent) return false
        changed(event.getTarget())
        return false
    }

    /** @param actor The event target, which is the actor that emitted the change event. */
    private fun changed(actor: Actor) {
        when (actor) {
            sceneSizeSelectBox -> listener.onSceneSizeChanged(sceneSizeSelectBox.selected)
            drawAxisCheckBox -> listener.onDrawAxisChanged(drawAxisCheckBox.isChecked)
            drawGridCheckBox -> {
                sceneSizeSelectBox.isDisabled = !drawGridCheckBox.isChecked
                listener.onDrawGridChanged(drawGridCheckBox.isChecked)
            }

            generateTerrainButton -> listener.onGenerateTerrainClicked()
        }
    }

    /**
     *
     * Interface for listeners of EditorSceneInterfaceWidget events.
     */
    interface EditorSceneInterfaceListener {

        fun onSceneSizeChanged(size: EditorState.SceneSize)

        fun onDrawAxisChanged(isDrawAxis: Boolean)

        fun onDrawGridChanged(isDrawGrid: Boolean)

        fun onGenerateTerrainClicked()
    }
}
