package com.pashkd.krender.engine.scene

import com.pashkd.krender.game.AssetBrowserScene
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneConfigPresetsTest {
    @Test
    fun `default scene config uses runtime 1920x1080`() {
        val config = SceneConfig()

        assertEquals(1920f, config.viewport.designWidth)
        assertEquals(1080f, config.viewport.designHeight)
        assertEquals(1920, config.window.resolution.width)
        assertEquals(1080, config.window.resolution.height)
    }

    @Test
    fun `runtime preset uses runtime 1920x1080`() {
        val config = SceneConfigPresets.RuntimeGame16By9

        assertEquals(1920f, config.viewport.designWidth)
        assertEquals(1080f, config.viewport.designHeight)
        assertEquals(1920, config.window.resolution.width)
        assertEquals(1080, config.window.resolution.height)
    }

    @Test
    fun `editor preset uses 1920x1280`() {
        val config = SceneConfigPresets.EditorTool

        assertEquals(1920f, config.viewport.designWidth)
        assertEquals(1080f, config.viewport.designHeight)
        assertEquals(1920, config.window.resolution.width)
        assertEquals(1280, config.window.resolution.height)
    }


    @Test
    fun `editor tool scenes use editor preset`() {
        assertEquals(SceneConfigPresets.AssetBrowser, AssetBrowserScene().config)
    }
}
