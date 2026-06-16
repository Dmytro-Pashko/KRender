package com.pashkd.krender.engine.tools.uicomposer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UiComposerCanvasRectTest {
    @Test
    internal fun `canvas rect is invalid when size is empty`() {
        assertFalse(UiComposerCanvasRect(width = 0f, height = 100f).isValid)
        assertFalse(UiComposerCanvasRect(width = 100f, height = 0f).isValid)
    }

    @Test
    internal fun `canvas rect contains points inside bounds`() {
        val rect = UiComposerCanvasRect(x = 10f, y = 20f, width = 100f, height = 50f)

        assertTrue(rect.contains(10f, 20f))
        assertTrue(rect.contains(60f, 40f))
        assertTrue(rect.contains(110f, 70f))
    }

    @Test
    internal fun `canvas rect rejects points outside bounds`() {
        val rect = UiComposerCanvasRect(x = 10f, y = 20f, width = 100f, height = 50f)

        assertFalse(rect.contains(9f, 20f))
        assertFalse(rect.contains(111f, 20f))
        assertFalse(rect.contains(10f, 19f))
        assertFalse(rect.contains(10f, 71f))
    }

    @Test
    internal fun `preview rect matches panel for same aspect ratio`() {
        val panel = UiComposerCanvasRect(0f, 0f, 1600f, 900f)

        val rect = computePreviewRect(panel, logicalWidth = 1920, logicalHeight = 1080)

        assertEquals(1600f, rect.width)
        assertEquals(900f, rect.height)
        assertEquals(0f, rect.x)
        assertEquals(0f, rect.y)
    }

    @Test
    internal fun `preview rect letterboxes horizontally when panel is too wide`() {
        val panel = UiComposerCanvasRect(0f, 0f, 1200f, 600f)

        val rect = computePreviewRect(panel, logicalWidth = 1920, logicalHeight = 1080)

        assertTrue(rect.width < panel.width)
        assertEquals(panel.height, rect.height)
        assertTrue(rect.x > panel.x)
    }

    @Test
    internal fun `preview rect letterboxes vertically when panel is too tall`() {
        val panel = UiComposerCanvasRect(0f, 0f, 600f, 800f)

        val rect = computePreviewRect(panel, logicalWidth = 1920, logicalHeight = 1080)

        assertEquals(panel.width, rect.width)
        assertTrue(rect.height < panel.height)
        assertTrue(rect.y > panel.y)
    }

    @Test
    internal fun `FitPanel preset uses panel size`() {
        val resolution =
            UiComposerPreviewResolutionPreset.FitPanel.defaultResolution(
                customWidth = 1,
                customHeight = 1,
                panelWidth = 900,
                panelHeight = 500,
            )

        assertEquals(900, resolution.width)
        assertEquals(500, resolution.height)
    }

    @Test
    internal fun `FullHD preset uses 1920x1080`() {
        val resolution =
            UiComposerPreviewResolutionPreset.FullHD_1920x1080.defaultResolution(
                customWidth = 1,
                customHeight = 1,
                panelWidth = 900,
                panelHeight = 500,
            )

        assertEquals(1920, resolution.width)
        assertEquals(1080, resolution.height)
    }

    @Test
    internal fun `Custom preset clamps to positive size`() {
        val resolution =
            UiComposerPreviewResolutionPreset.Custom.defaultResolution(
                customWidth = 0,
                customHeight = -10,
                panelWidth = 900,
                panelHeight = 500,
            )

        assertEquals(1, resolution.width)
        assertEquals(1, resolution.height)
    }

    @Test
    internal fun `preview zoom clamps to supported range`() {
        assertEquals(UiComposerPreviewMinZoom, clampPreviewZoom(0f))
        assertEquals(UiComposerPreviewMaxZoom, clampPreviewZoom(100f))
        assertEquals(1f, clampPreviewZoom(Float.NaN))
        assertEquals(2f, clampPreviewZoom(2f))
    }

    @Test
    internal fun `world units per screen pixel accounts for zoom`() {
        assertEquals(
            2f,
            previewWorldUnitsPerScreenPixel(logicalSize = 1000, screenSize = 500f, zoom = 1f),
        )
        assertEquals(
            1f,
            previewWorldUnitsPerScreenPixel(logicalSize = 1000, screenSize = 500f, zoom = 2f),
        )
    }
}
