package com.pashkd.krender.engine.viewport

import com.pashkd.krender.engine.api.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeViewportTest {
    @Test
    fun `scale by height uses native design size for 1080p 16 by 9`() {
        val viewport = calculateRuntimeViewport(1920, 1080)

        assertApproximately(1f, viewport.scale)
        assertApproximately(1920f, viewport.logicalWidth)
        assertApproximately(1080f, viewport.logicalHeight)
        assertApproximately(0f, viewport.offsetX)
        assertApproximately(0f, viewport.offsetY)
    }

    @Test
    fun `scale by height keeps design logical size for 720p 16 by 9`() {
        val viewport = calculateRuntimeViewport(1280, 720)

        assertApproximately(0.6666667f, viewport.scale)
        assertApproximately(1920f, viewport.logicalWidth)
        assertApproximately(1080f, viewport.logicalHeight)
    }

    @Test
    fun `scale by height expands logical width for 2560 by 1080 21 by 9`() {
        val viewport = calculateRuntimeViewport(2560, 1080)

        assertApproximately(1f, viewport.scale)
        assertApproximately(2560f, viewport.logicalWidth)
        assertApproximately(1080f, viewport.logicalHeight)
    }

    @Test
    fun `scale by height expands logical width for 3440 by 1440 ultrawide`() {
        val viewport = calculateRuntimeViewport(3440, 1440)

        assertApproximately(1.333333f, viewport.scale)
        assertApproximately(2580f, viewport.logicalWidth)
        assertApproximately(1080f, viewport.logicalHeight)
    }

    @Test
    fun `fit keeps full design visible in 4 by 3 with vertical letterbox offsets`() {
        val viewport =
            calculateRuntimeViewport(
                pixelWidth = 1024,
                pixelHeight = 768,
                config = RuntimeViewportConfig(scalePolicy = UiScalePolicy.Fit),
            )

        assertApproximately(1024f / 1920f, viewport.scale)
        assertApproximately(1920f, viewport.logicalWidth)
        assertApproximately(1080f, viewport.logicalHeight)
        assertApproximately(0f, viewport.offsetX)
        assertApproximately(96f, viewport.offsetY)
    }

    @Test
    fun `fill covers ultrawide window and crops vertically`() {
        val viewport =
            calculateRuntimeViewport(
                pixelWidth = 2560,
                pixelHeight = 1080,
                config = RuntimeViewportConfig(scalePolicy = UiScalePolicy.Fill),
            )

        assertApproximately(2560f / 1920f, viewport.scale)
        assertApproximately(0f, viewport.offsetX)
        assertApproximately(-180f, viewport.offsetY)
    }

    @Test
    fun `pixel perfect uses physical pixels as logical size`() {
        val viewport =
            calculateRuntimeViewport(
                pixelWidth = 1280,
                pixelHeight = 720,
                config = RuntimeViewportConfig(scalePolicy = UiScalePolicy.PixelPerfect),
            )

        assertApproximately(1f, viewport.scale)
        assertApproximately(1280f, viewport.logicalWidth)
        assertApproximately(720f, viewport.logicalHeight)
    }

    @Test
    fun `coordinate conversion roundtrips through screen space`() {
        val viewport =
            calculateRuntimeViewport(
                pixelWidth = 1024,
                pixelHeight = 768,
                config = RuntimeViewportConfig(scalePolicy = UiScalePolicy.Fit),
            )
        val logical = Vec2(320f, 180f)

        val screen = viewport.logicalToScreen(logical)
        val back = viewport.screenToLogical(screen)

        assertApproximately(logical.x, back.x)
        assertApproximately(logical.y, back.y)
    }

    @Test
    fun `zero pixel size is coerced to valid viewport`() {
        val viewport = calculateRuntimeViewport(0, 0)

        assertEquals(1, viewport.pixelWidth)
        assertEquals(1, viewport.pixelHeight)
        assertTrue(viewport.scale > 0f)
    }

    private fun assertApproximately(
        expected: Float,
        actual: Float,
        tolerance: Float = 0.0001f,
    ) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected <$expected> but was <$actual> with tolerance <$tolerance>",
        )
    }
}
