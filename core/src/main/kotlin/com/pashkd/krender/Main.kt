package com.pashkd.krender

import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.game.DemoScene

class Main : GdxEngineApplication(
    initialScene = { DemoScene() },
)
