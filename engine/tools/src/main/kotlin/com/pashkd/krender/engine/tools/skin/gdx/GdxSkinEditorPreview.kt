package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.scenes.scene2d.Stage
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.skin.PreviewLayout
import com.pashkd.krender.engine.tools.skin.PreviewLayoutContext
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinEditorPreviewStageInfo
import com.pashkd.krender.engine.tools.skin.SkinLoadResult
import com.pashkd.krender.engine.tools.skin.StyleKey
import com.pashkd.krender.engine.tools.skin.WidgetPreviewFactory

data class GdxSkinPreviewBuildResult(
    val previewInfo: SkinEditorPreviewStageInfo,
    val issues: List<PreviewBuildIssue> = emptyList(),
)

class GdxSkinEditorPreview(
    private val logger: Logger,
) : Disposable {
    private val stage = Stage(FitViewport(1f, 1f))
    private val safeWidgetBuilder = SafeWidgetBuilder()
    private val previewFactory = WidgetPreviewFactory()
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var logicalWidth = 1
    private var logicalHeight = 1
    private var previewScale = 1f

    fun rebuild(
        loadResult: SkinLoadResult,
        layout: PreviewLayout,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
        selectedStyleKey: StyleKey?,
        selectedResourceName: String?,
    ): GdxSkinPreviewBuildResult {
        stage.clear()
        val buildResult =
            safeWidgetBuilder.build(
                item =
                    layout.build(
                        context =
                            PreviewLayoutContext(
                                loadResult = loadResult,
                                selectedStyleKey = selectedStyleKey,
                                selectedResourceName = selectedResourceName,
                            ),
                        factory = previewFactory,
                    ),
                loadedSkin = loadedSkin,
                editSession = editSession,
            )
        val root = buildResult.actor
        stage.addActor(root)
        centerActor(root)
        logger.debug(TAG) { "Skin preview rebuilt layout='${layout.id}' loadedSkin=${loadResult.previewSkinAvailable} issues=${buildResult.issues.size}" }
        return GdxSkinPreviewBuildResult(
            previewInfo = SkinEditorPreviewStageInfo(actorCount = actorCount(root), rootActorClass = root.javaClass.simpleName),
            issues = buildResult.issues,
        )
    }

    fun update(dt: Float) {
        stage.act(dt)
    }

    fun setCanvasViewport(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        logicalWidth: Int,
        logicalHeight: Int,
        scale: Float,
        showBounds: Boolean,
    ) {
        viewportX = x.coerceAtLeast(0)
        viewportY = y.coerceAtLeast(0)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        this.logicalWidth = logicalWidth.coerceAtLeast(1)
        this.logicalHeight = logicalHeight.coerceAtLeast(1)
        previewScale = scale.coerceIn(0.5f, 1.5f)
        stage.viewport.setWorldSize(this.logicalWidth.toFloat(), this.logicalHeight.toFloat())
        stage.viewport.update(viewportWidth, viewportHeight, false)
        (stage.camera as? OrthographicCamera)?.apply {
            position.set(this@GdxSkinEditorPreview.logicalWidth / 2f, this@GdxSkinEditorPreview.logicalHeight / 2f, 0f)
            zoom = 1f / previewScale
            update()
        }
        stage.setDebugAll(showBounds)
        stage.root.children.firstOrNull()?.let(::centerActor)
    }

    fun clearCanvasViewport() {
        viewportWidth = 1
        viewportHeight = 1
    }

    fun clear() {
        stage.clear()
    }

    fun resize(
        width: Int,
        height: Int,
    ) = Unit

    fun render() {
        if (viewportWidth <= 1 || viewportHeight <= 1) return

        val graphicsWidth = Gdx.graphics.width.coerceAtLeast(1)
        val graphicsHeight = Gdx.graphics.height.coerceAtLeast(1)
        val safeViewportX = viewportX.coerceIn(0, graphicsWidth - 1)
        val safeViewportY = viewportY.coerceIn(0, graphicsHeight - 1)
        val safeViewportWidth = viewportWidth.coerceAtMost(graphicsWidth - safeViewportX).coerceAtLeast(1)
        val safeViewportHeight = viewportHeight.coerceAtMost(graphicsHeight - safeViewportY).coerceAtLeast(1)

        val previousViewport = BufferUtils.newIntBuffer(4)
        val previousScissor = BufferUtils.newIntBuffer(4)
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, previousViewport)
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, previousScissor)
        val scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
        val glY = (graphicsHeight - safeViewportY - safeViewportHeight).coerceAtLeast(0)

        Gdx.gl.glViewport(safeViewportX, glY, safeViewportWidth, safeViewportHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(safeViewportX, glY, safeViewportWidth, safeViewportHeight)
        Gdx.gl.glClearColor(0.07f, 0.075f, 0.09f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        val contentWidth = stage.viewport.screenWidth.coerceAtLeast(1)
        val contentHeight = stage.viewport.screenHeight.coerceAtLeast(1)
        val contentX = safeViewportX + (safeViewportWidth - contentWidth) / 2
        val contentY = glY + (safeViewportHeight - contentHeight) / 2
        stage.viewport.setScreenBounds(contentX, contentY, contentWidth, contentHeight)
        stage.viewport.apply(false)
        stage.draw()

        if (!scissorWasEnabled) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        } else {
            Gdx.gl.glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3])
        }
        Gdx.gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    override fun dispose() {
        safeWidgetBuilder.dispose()
        stage.dispose()
    }

    private fun centerActor(actor: com.badlogic.gdx.scenes.scene2d.Actor) {
        actor.setPosition(
            ((logicalWidth - actor.width) / 2f).coerceAtLeast(0f),
            ((logicalHeight - actor.height) / 2f).coerceAtLeast(0f),
        )
    }

    private fun actorCount(actor: com.badlogic.gdx.scenes.scene2d.Actor): Int {
        val group = actor as? com.badlogic.gdx.scenes.scene2d.Group ?: return 1
        return 1 + group.children.sumOf(::actorCount)
    }

    private companion object {
        private const val TAG = "GdxSkinEditorPreview"
    }
}
