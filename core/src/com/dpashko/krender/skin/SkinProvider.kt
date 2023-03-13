package com.dpashko.krender.skin

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Skin

object SkinProvider {

    //Default UI skin.
    private var ui_default_skin_fnt = Gdx.files.internal("ui/default/default.fnt")
    private var ui_default_skin_atlas = Gdx.files.internal("ui/default/uiskin.atlas")
    private var ui_default_skin_json = Gdx.files.internal("ui/default/uiskin.json")
    private var ui_default_skin_png = Gdx.files.internal("ui/default/uiskin.png")

    val default: Skin
        get() = Skin(ui_default_skin_json)
}
