package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneAlign
import com.pashkd.krender.engine.ui.scene.UiSceneSpacing
import kotlin.test.Test
import kotlin.test.assertEquals

internal class UiComposerVisualGuidesTest {
    @Test
    internal fun `alignment anchor maps corners and center`() {
        val bounds = UiComposerGuideRect(x = 10f, y = 20f, width = 100f, height = 50f)

        assertEquals(UiComposerGuidePoint(10f, 70f), computeAlignmentAnchor(bounds, UiSceneAlign.TopLeft))
        assertEquals(UiComposerGuidePoint(60f, 45f), computeAlignmentAnchor(bounds, UiSceneAlign.Center))
        assertEquals(UiComposerGuidePoint(110f, 20f), computeAlignmentAnchor(bounds, UiSceneAlign.BottomRight))
    }

    @Test
    internal fun `alignment anchor maps edge centers`() {
        val bounds = UiComposerGuideRect(x = 10f, y = 20f, width = 100f, height = 50f)

        assertEquals(UiComposerGuidePoint(60f, 70f), computeAlignmentAnchor(bounds, UiSceneAlign.Top))
        assertEquals(UiComposerGuidePoint(10f, 45f), computeAlignmentAnchor(bounds, UiSceneAlign.Left))
        assertEquals(UiComposerGuidePoint(110f, 45f), computeAlignmentAnchor(bounds, UiSceneAlign.Right))
        assertEquals(UiComposerGuidePoint(60f, 20f), computeAlignmentAnchor(bounds, UiSceneAlign.Bottom))
    }

    @Test
    internal fun `padding inner rect applies bottom-up scene coordinates`() {
        val bounds = UiComposerGuideRect(x = 100f, y = 200f, width = 300f, height = 150f)
        val padding = UiSceneSpacing(left = 10f, top = 20f, right = 30f, bottom = 40f)

        val inner = computePaddingInnerRect(bounds, padding)

        assertEquals(UiComposerGuideRect(x = 110f, y = 240f, width = 260f, height = 90f), inner)
    }

    @Test
    internal fun `padding inner rect clamps inverted dimensions`() {
        val bounds = UiComposerGuideRect(x = 0f, y = 0f, width = 20f, height = 10f)
        val padding = UiSceneSpacing(left = 15f, top = 9f, right = 15f, bottom = 9f)

        val inner = computePaddingInnerRect(bounds, padding)

        assertEquals(UiComposerGuideRect(x = 15f, y = 9f, width = 0f, height = 0f), inner)
    }
}
