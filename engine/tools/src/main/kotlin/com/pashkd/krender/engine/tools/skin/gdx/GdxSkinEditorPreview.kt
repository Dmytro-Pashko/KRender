package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.scenes.scene2d.Stage
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.skin.PreviewLayout
import com.pashkd.krender.engine.tools.skin.PreviewLayoutContext
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
    private val stage = Stage(ScreenViewport())
    private val safeWidgetBuilder = SafeWidgetBuilder()
    private val previewFactory = WidgetPreviewFactory()
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun rebuild(
        loadResult: SkinLoadResult,
        layout: PreviewLayout,
        loadedSkin: LoadedSkinHandle?,
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
            )
        val root = buildResult.actor
        stage.addActor(root)
        root.setPosition(24f, 24f)
        logger.debug(TAG) { "Skin preview rebuilt layout='${layout.id}' loadedSkin=${loadResult.previewSkinAvailable} issues=${buildResult.issues.size}" }
        return GdxSkinPreviewBuildResult(
            previewInfo = SkinEditorPreviewStageInfo(actorCount = stage.root.children.size, rootActorClass = root.javaClass.simpleName),
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
    ) {
        viewportX = x.coerceAtLeast(0)
        viewportY = y.coerceAtLeast(0)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        stage.viewport.update(viewportWidth, viewportHeight, true)
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

        val previousViewport = BufferUtils.newIntBuffer(4)
        val previousScissor = BufferUtils.newIntBuffer(4)
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, previousViewport)
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, previousScissor)
        val scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
        val glY = Gdx.graphics.height - viewportY - viewportHeight

        Gdx.gl.glViewport(viewportX, glY, viewportWidth, viewportHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(viewportX, glY, viewportWidth, viewportHeight)
        Gdx.gl.glClearColor(0.07f, 0.075f, 0.09f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
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

    private companion object {
        private const val TAG = "GdxSkinEditorPreview"
    }
}
