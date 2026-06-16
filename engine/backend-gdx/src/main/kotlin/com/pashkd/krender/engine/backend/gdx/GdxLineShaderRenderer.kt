package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.RenderCommand
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.api.Color as EngineColor

/**
 * Minimal shader-based line renderer used for debug grids, axes, and wireframes.
 */
class GdxLineShaderRenderer {
    private val shader =
        ShaderProgram(
            GdxShaderSources.read("shaders/line.vert"),
            GdxShaderSources.read("shaders/line.frag"),
        )

    private var mesh: Mesh? = null
    private var vertexCapacity: Int = 0

    init {
        check(shader.isCompiled) { shader.log }
    }

    /** Renders non-overlay world debug lines such as grids and axes. */
    fun render(
        commands: List<RenderCommand>,
        camera: Camera,
    ) {
        val vertices = mutableListOf<Float>()
        commands.forEach { command ->
            when (command) {
                is DrawWorldGrid -> appendGrid(vertices, command)
                is DrawWorldAxes -> appendAxes(vertices, command)
                else -> Unit
            }
        }

        renderVertices(vertices, camera)
    }

    /** Renders explicit overlay lines without depth testing. */
    fun renderOverlayLines(
        commands: List<RenderCommand>,
        camera: Camera,
    ) {
        val vertices = mutableListOf<Float>()
        commands.filterIsInstance<DrawLine>().forEach { command ->
            appendLine(vertices, command.from, command.to, command.color)
        }

        renderVertices(vertices, camera, depthTest = false)
    }

    /** Uploads line vertices and renders them with the internal shader. */
    fun renderVertices(
        vertices: List<Float>,
        camera: Camera,
        depthTest: Boolean = true,
    ) {
        val vertexCount = vertices.size / FLOATS_PER_VERTEX
        if (vertexCount == 0) return

        val lineMesh = meshFor(vertexCount)
        lineMesh.setVertices(vertices.toFloatArray())

        if (depthTest) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        } else {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        }
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1f)

        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        lineMesh.render(shader, GL20.GL_LINES, 0, vertexCount)

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
    }

    /** Disposes the reusable mesh and shader program. */
    fun dispose() {
        mesh?.dispose()
        shader.dispose()
    }

    /** Appends a world grid to the vertex list. */
    private fun appendGrid(
        vertices: MutableList<Float>,
        command: DrawWorldGrid,
    ) {
        val half = command.halfExtentCells.coerceAtLeast(1)
        val min = -half * command.cellSize
        val max = half * command.cellSize

        for (i in -half..half) {
            val offset = i * command.cellSize
            appendLine(
                vertices,
                from = Vec3(offset, command.y, min),
                to = Vec3(offset, command.y, max),
                color = command.color,
            )
            appendLine(
                vertices,
                from = Vec3(min, command.y, offset),
                to = Vec3(max, command.y, offset),
                color = command.color,
            )
        }
    }

    /** Appends RGB world axes centered at the origin. */
    private fun appendAxes(
        vertices: MutableList<Float>,
        command: DrawWorldAxes,
    ) {
        val length = command.length.coerceAtLeast(1f)
        appendLine(
            vertices,
            Vec3(-length, 0f, 0f),
            Vec3(length, 0f, 0f),
            EngineColor(1f, 0f, 0f, 1f),
        )
        appendLine(
            vertices,
            Vec3(0f, -length, 0f),
            Vec3(0f, length, 0f),
            EngineColor(0f, 1f, 0f, 1f),
        )
        appendLine(
            vertices,
            Vec3(0f, 0f, -length),
            Vec3(0f, 0f, length),
            EngineColor(0f, 0.35f, 1f, 1f),
        )
    }

    /** Appends one colored line segment. */
    private fun appendLine(
        vertices: MutableList<Float>,
        from: Vec3,
        to: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        appendVertex(vertices, from, color)
        appendVertex(vertices, to, color)
    }

    /** Appends one colored line vertex. */
    private fun appendVertex(
        vertices: MutableList<Float>,
        position: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        vertices += position.x
        vertices += position.y
        vertices += position.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    /** Returns a reusable mesh with capacity for the requested vertex count. */
    private fun meshFor(vertexCount: Int): Mesh {
        if (mesh == null || vertexCount > vertexCapacity) {
            mesh?.dispose()
            vertexCapacity = vertexCount
            mesh =
                Mesh(
                    false,
                    vertexCapacity,
                    0,
                    VertexAttribute.Position(),
                    VertexAttribute.ColorUnpacked(),
                )
        }
        return mesh ?: error("Line mesh was not created")
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 7
    }
}
