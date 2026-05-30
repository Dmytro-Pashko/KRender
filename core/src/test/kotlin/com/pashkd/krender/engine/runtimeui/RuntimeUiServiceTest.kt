package com.pashkd.krender.engine.runtimeui

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeUiServiceTest {
    @Test
    fun `stores default layers in deterministic order`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())

        service.setLayer(RuntimeUiLayers.Overlay, screen("overlay"))
        service.setLayer(RuntimeUiLayers.Hud, screen("hud"))
        service.setLayer(RuntimeUiLayers.Modal, screen("modal"))

        assertEquals(
            listOf(RuntimeUiLayers.Hud, RuntimeUiLayers.Modal, RuntimeUiLayers.Overlay),
            service.activeLayers().map(RuntimeUiLayerState::layer),
        )
        assertEquals(
            listOf(RuntimeUiLayers.Hud, RuntimeUiLayers.Modal, RuntimeUiLayers.Overlay),
            backend.syncedLayers.last().map(RuntimeUiLayerState::layer),
        )
    }

    @Test
    fun `custom layers keep insertion order after default layers`() {
        val service = RuntimeUiService(FakeRuntimeUiBackend(), NoOpLogger())

        service.setLayer("cinematic", screen("cinematic"))
        service.setLayer(RuntimeUiLayers.Hud, screen("hud"))
        service.setLayer("debug", screen("debug"))

        assertEquals(
            listOf(RuntimeUiLayers.Hud, "cinematic", "debug"),
            service.activeLayers().map(RuntimeUiLayerState::layer),
        )
    }

    @Test
    fun `syncs layers whenever setLayer changes state`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())

        service.setLayer(RuntimeUiLayers.Hud, screen("hud"))
        service.setLayer(RuntimeUiLayers.Modal, screen("modal"))
        service.setLayer(RuntimeUiLayers.Hud, screen("hud-2"))

        assertEquals(3, backend.syncedLayers.size)
        assertEquals("hud-2", backend.syncedLayers.last().first { it.layer == RuntimeUiLayers.Hud }.screen?.id)
    }

    @Test
    fun `clearLayer removes layer and syncs backend`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())
        service.setLayer(RuntimeUiLayers.Hud, screen("hud"))

        service.clearLayer(RuntimeUiLayers.Hud)

        assertEquals(emptyList(), service.activeLayers())
        assertEquals(emptyList(), backend.syncedLayers.last())
    }

    @Test
    fun `clear clears layers and backend`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())
        service.setLayer(RuntimeUiLayers.Hud, screen("hud"))

        service.clear()

        assertEquals(emptyList(), service.activeLayers())
        assertEquals(1, backend.clearCalls)
    }

    @Test
    fun `forwards action handler to backend`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())
        val handler = RuntimeUiActionHandler { }

        service.setActionHandler(handler)

        assertSame(handler, backend.currentActionHandler)
    }

    @Test
    fun `forwards update render resize and dispose`() {
        val backend = FakeRuntimeUiBackend()
        val service = RuntimeUiService(backend, NoOpLogger())

        service.update(0.25f)
        service.render()
        service.resize(1280, 720)
        service.dispose()

        assertEquals(listOf(0.25f), backend.updates)
        assertEquals(1, backend.renderCalls)
        assertEquals(listOf(1280 to 720), backend.resizes)
        assertEquals(1, backend.disposeCalls)
    }

    @Test
    fun `setActionHandler accepts null`() {
        val backend = FakeRuntimeUiBackend().apply {
            currentActionHandler = RuntimeUiActionHandler { }
        }
        val service = RuntimeUiService(backend, NoOpLogger())

        service.setActionHandler(null)

        assertNull(backend.currentActionHandler)
    }

    private fun screen(id: String): RuntimeUiScreen = RuntimeUiScreen(id = id)

    private class FakeRuntimeUiBackend : RuntimeUiBackend {
        var currentActionHandler: RuntimeUiActionHandler? = null
        val syncedLayers = mutableListOf<List<RuntimeUiLayerState>>()
        val updates = mutableListOf<Float>()
        val resizes = mutableListOf<Pair<Int, Int>>()
        var renderCalls = 0
        var clearCalls = 0
        var disposeCalls = 0

        override fun setActionHandler(handler: RuntimeUiActionHandler?) {
            currentActionHandler = handler
        }

        override fun syncLayers(layers: List<RuntimeUiLayerState>) {
            syncedLayers += layers.map { it.copy(screen = it.screen?.copy()) }
        }

        override fun update(dt: Float) {
            updates += dt
        }

        override fun render() {
            renderCalls += 1
        }

        override fun resize(width: Int, height: Int) {
            resizes += width to height
        }

        override fun clear() {
            clearCalls += 1
        }

        override fun dispose() {
            disposeCalls += 1
        }
    }

    private class NoOpLogger : Logger {
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }
}
