package com.pashkd.krender.engine.runtimeui

import com.pashkd.krender.engine.viewport.calculateRuntimeViewport
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeUiLayoutResolverTest {
    private val resolver = RuntimeUiLayoutResolver()

    @Test
    fun `root fills viewport logical size`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(id = "root", type = RuntimeUiNodeType.Stack),
        )

        assertApproximately(1920f, resolved.rect.width)
        assertApproximately(1080f, resolved.rect.height)
    }

    @Test
    fun `top left fixed child resolves at origin`() {
        val resolved = resolveWithChild(
            RuntimeUiNode(
                id = "child",
                layout = RuntimeUiLayout(
                    width = UiSizeValue.fixed(200f),
                    height = UiSizeValue.fixed(100f),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(0f, child.rect.x)
        assertApproximately(0f, child.rect.y)
    }

    @Test
    fun `top left child with margin resolves at margin`() {
        val resolved = resolveWithChild(
            RuntimeUiNode(
                id = "child",
                layout = RuntimeUiLayout(
                    width = UiSizeValue.fixed(200f),
                    height = UiSizeValue.fixed(100f),
                    margin = UiSpacing(left = 16f, top = 24f),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(16f, child.rect.x)
        assertApproximately(24f, child.rect.y)
    }

    @Test
    fun `center fixed panel resolves to viewport center`() {
        val resolved = resolveWithChild(
            RuntimeUiNode(
                id = "dialog",
                type = RuntimeUiNodeType.Panel,
                layout = RuntimeUiLayout(
                    anchor = UiAnchor.Center,
                    width = UiSizeValue.fixed(400f),
                    height = UiSizeValue.fixed(200f),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately((1920f - 400f) * 0.5f, child.rect.x)
        assertApproximately((1080f - 200f) * 0.5f, child.rect.y)
    }

    @Test
    fun `bottom center dialog resolves correctly`() {
        val resolved = resolve(
            viewportWidth = 2560,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Stack,
                children = listOf(
                    RuntimeUiNode(
                        id = "hint",
                        type = RuntimeUiNodeType.Text,
                        text = "WASD Move",
                        layout = RuntimeUiLayout(
                            anchor = UiAnchor.BottomCenter,
                            width = UiSizeValue.fixed(300f),
                            height = UiSizeValue.fixed(40f),
                            margin = UiSpacing(bottom = 32f),
                        ),
                    ),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(2560f, resolved.rect.width)
        assertApproximately(1080f, resolved.rect.height)
        assertApproximately((2560f - 300f) * 0.5f, child.rect.x)
        assertApproximately(1080f - 32f - 40f, child.rect.y)
    }

    @Test
    fun `stretch child fills parent content rect`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Panel,
                layout = RuntimeUiLayout(padding = UiSpacing.all(10f)),
                children = listOf(
                    RuntimeUiNode(
                        id = "child",
                        layout = RuntimeUiLayout(anchor = UiAnchor.Stretch),
                    ),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(10f, child.rect.x)
        assertApproximately(10f, child.rect.y)
        assertApproximately(1900f, child.rect.width)
        assertApproximately(1060f, child.rect.height)
    }

    @Test
    fun `parent padding affects child placement`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Panel,
                layout = RuntimeUiLayout(padding = UiSpacing(left = 20f, top = 30f)),
                children = listOf(
                    RuntimeUiNode(
                        id = "child",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(20f, child.rect.x)
        assertApproximately(30f, child.rect.y)
    }

    @Test
    fun `stack resolves children with independent anchors`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Stack,
                children = listOf(
                    RuntimeUiNode(
                        id = "topLeft",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                    RuntimeUiNode(
                        id = "bottomRight",
                        layout = RuntimeUiLayout(
                            anchor = UiAnchor.BottomRight,
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                ),
            ),
        )

        val topLeft = resolved.children.first { it.id == "topLeft" }
        val bottomRight = resolved.children.first { it.id == "bottomRight" }
        assertApproximately(0f, topLeft.rect.x)
        assertApproximately(0f, topLeft.rect.y)
        assertApproximately(1820f, bottomRight.rect.x)
        assertApproximately(1030f, bottomRight.rect.y)
    }

    @Test
    fun `column lays out children top to bottom with gap`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Column,
                layout = RuntimeUiLayout(gap = 10f),
                children = listOf(
                    RuntimeUiNode(
                        id = "first",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                    RuntimeUiNode(
                        id = "second",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(60f),
                            margin = UiSpacing(top = 5f),
                        ),
                    ),
                ),
            ),
        )

        val first = resolved.children.first { it.id == "first" }
        val second = resolved.children.first { it.id == "second" }
        assertApproximately(0f, first.rect.y)
        assertApproximately(65f, second.rect.y)
    }

    @Test
    fun `row lays out children left to right with gap`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Row,
                layout = RuntimeUiLayout(gap = 12f),
                children = listOf(
                    RuntimeUiNode(
                        id = "first",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(100f),
                            height = UiSizeValue.fixed(50f),
                        ),
                    ),
                    RuntimeUiNode(
                        id = "second",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.fixed(80f),
                            height = UiSizeValue.fixed(50f),
                            margin = UiSpacing(left = 5f),
                        ),
                    ),
                ),
            ),
        )

        val first = resolved.children.first { it.id == "first" }
        val second = resolved.children.first { it.id == "second" }
        assertApproximately(0f, first.rect.x)
        assertApproximately(117f, second.rect.x)
    }

    @Test
    fun `invisible child is skipped`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Stack,
                children = listOf(
                    RuntimeUiNode(id = "visible"),
                    RuntimeUiNode(id = "hidden", visible = false),
                ),
            ),
        )

        assertEquals(listOf("visible"), resolved.children.map { it.id })
    }

    @Test
    fun `percent width and height resolve against parent content size`() {
        val resolved = resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Panel,
                layout = RuntimeUiLayout(padding = UiSpacing.all(10f)),
                children = listOf(
                    RuntimeUiNode(
                        id = "child",
                        layout = RuntimeUiLayout(
                            width = UiSizeValue.percent(0.5f),
                            height = UiSizeValue.percent(0.25f),
                        ),
                    ),
                ),
            ),
        )

        val child = resolved.children.single()
        assertApproximately(950f, child.rect.width)
        assertApproximately(265f, child.rect.height)
    }

    @Test
    fun `scale by height ultrawide viewport produces wider root rect`() {
        val viewport = calculateRuntimeViewport(2560, 1080)
        val resolved = resolver.resolve(
            RuntimeUiDocument(
                id = "hud",
                root = RuntimeUiNode(id = "root", type = RuntimeUiNodeType.Stack),
            ),
            viewport,
        )

        assertApproximately(2560f, resolved.rect.width)
        assertApproximately(1080f, resolved.rect.height)
    }

    private fun resolveWithChild(child: RuntimeUiNode): RuntimeUiResolvedNode =
        resolve(
            viewportWidth = 1920,
            viewportHeight = 1080,
            root = RuntimeUiNode(
                id = "root",
                type = RuntimeUiNodeType.Stack,
                children = listOf(child),
            ),
        )

    private fun resolve(
        viewportWidth: Int,
        viewportHeight: Int,
        root: RuntimeUiNode,
    ): RuntimeUiResolvedNode =
        resolver.resolve(
            RuntimeUiDocument(id = "doc", root = root),
            calculateRuntimeViewport(viewportWidth, viewportHeight),
        )

    private fun assertApproximately(
        expected: Float,
        actual: Float,
        tolerance: Float = 0.0001f,
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected <$expected> but was <$actual>")
    }
}
