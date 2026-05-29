package com.pashkd.krender.engine.runtimeui

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.viewport.RuntimeViewportService
import com.pashkd.krender.engine.viewport.calculateRuntimeViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeUiSystemTest {
    @Test
    fun `register document and resolve active layer`() {
        val system = RuntimeUiSystem(
            viewport = RuntimeViewportService(),
            logger = CollectingLogger(),
        )
        val document = document("hud")

        system.register(document)
        system.setLayer("hud", "hud")

        val resolved = system.resolveActive()

        assertEquals(listOf("hud"), resolved.map { it.id })
    }

    @Test
    fun `clearing layer removes document from active resolved output`() {
        val system = RuntimeUiSystem(
            viewport = RuntimeViewportService(),
            logger = CollectingLogger(),
        )
        system.register(document("hud"))
        system.setLayer("hud", "hud")

        system.clearLayer("hud")

        assertTrue(system.resolveActive().isEmpty())
    }

    @Test
    fun `missing document does not crash and warns once`() {
        val logger = CollectingLogger()
        val system = RuntimeUiSystem(
            viewport = RuntimeViewportService(),
            logger = logger,
        )
        system.setLayer("hud", "missing")

        assertTrue(system.resolveActive().isEmpty())
        assertTrue(system.resolveActive().isEmpty())
        assertEquals(
            1,
            logger.entries.count { it.level == LogLevel.Warn && it.message == "Runtime UI document 'missing' is missing for an active layer." },
        )
    }

    @Test
    fun `multiple layers resolve in insertion order`() {
        val viewport = RuntimeViewportService()
        viewport.resize(2560, 1080)
        val system = RuntimeUiSystem(
            viewport = viewport,
            logger = CollectingLogger(),
        )
        system.register(document("menu"))
        system.register(document("hud"))
        system.setLayer("background", "menu")
        system.setLayer("foreground", "hud")

        val resolved = system.resolveActive()

        assertEquals(listOf("menu", "hud"), resolved.map { it.id })
        assertEquals(2560f, resolved.first().rect.width)
    }

    @Test
    fun `viewport current is used during active resolution`() {
        val viewport = RuntimeViewportService()
        viewport.resize(3440, 1440)
        val system = RuntimeUiSystem(
            viewport = viewport,
            logger = CollectingLogger(),
        )
        system.register(document("hud"))
        system.setLayer("hud", "hud")

        val resolved = system.resolveActive().single()

        assertEquals(calculateRuntimeViewport(3440, 1440).logicalWidth, resolved.rect.width)
        assertEquals(1080f, resolved.rect.height)
    }

    private fun document(id: String): RuntimeUiDocument =
        RuntimeUiDocument(
            id = id,
            root = RuntimeUiNode(
                id = id,
                type = RuntimeUiNodeType.Stack,
                children = listOf(
                    RuntimeUiNode(
                        id = "$id-child",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                ),
            ),
        )

    private class CollectingLogger : Logger {
        val entries = mutableListOf<LoggedEntry>()

        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) {
            entries += LoggedEntry(level, tag, message())
        }
    }

    private data class LoggedEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
    )
}
