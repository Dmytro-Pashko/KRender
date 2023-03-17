package com.dpashko.krender.scene.terrain.generator

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

class TerrainGeneratorUiStage(
    private val listener: TerrainGeneratorInterfaceListener,
    skin: Skin
) : Stage(), EventListener {
    private var generateButton = Button(Label("Generate", skin), skin)
    private var exitButton = Button(Label("Exit", skin), skin)

    init {
        addActor(
            Table(skin).apply {
                add(generateButton.apply {
                    addListener(this@TerrainGeneratorUiStage)
                }).space(20f)
                add(exitButton).apply {
                    addListener(this@TerrainGeneratorUiStage)
                }
                debugAll()
                setFillParent(true)
            })
    }

    override fun handle(event: Event?): Boolean {
        if (event !is ChangeListener.ChangeEvent) return false
        changed(event.getTarget())
        return false
    }

    /** @param actor The event target, which is the actor that emitted the change event. */
    private fun changed(actor: Actor) {
        when (actor) {
            exitButton -> listener.onExitClicked()
        }
    }
}

interface TerrainGeneratorInterfaceListener {

    fun onExitClicked()
}
