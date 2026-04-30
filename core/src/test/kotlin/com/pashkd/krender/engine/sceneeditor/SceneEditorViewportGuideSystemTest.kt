package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.SceneWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SceneEditorViewportGuideSystemTest {
    @Test
    fun `submits grid and axes from editor state`() {
        val state = SceneEditorState(
            gridHalfExtentCells = 32,
            gridCellSize = 0.5f,
        )
        val world = SceneWorld()
        world.systems.add(SceneEditorViewportGuideSystem(state))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot()
        assertEquals(2, commands.size)
        val grid = assertIs<DrawWorldGrid>(commands[0])
        val axes = assertIs<DrawWorldAxes>(commands[1])
        assertEquals(32, grid.halfExtentCells)
        assertEquals(0.5f, grid.cellSize)
        assertEquals(16f, axes.length)
    }

    @Test
    fun `respects grid and axes toggles independently`() {
        val state = SceneEditorState(showGrid = false, showAxes = true)
        val world = SceneWorld()
        world.systems.add(SceneEditorViewportGuideSystem(state))

        world.render(alpha = 0f)

        assertEquals(listOf(DrawWorldAxes::class), world.renderCommands.snapshot().map { it::class })

        state.showGrid = true
        state.showAxes = false
        world.render(alpha = 0f)

        assertEquals(listOf(DrawWorldGrid::class), world.renderCommands.snapshot().map { it::class })
    }
}
