package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Actor
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.skin.PreviewLayout
import com.pashkd.krender.engine.tools.skin.PreviewLayoutContext
import com.pashkd.krender.engine.tools.skin.SkinEditSession
import com.pashkd.krender.engine.tools.skin.SkinPreviewTextSettings
import com.pashkd.krender.engine.tools.skin.SkinPreviewInteractionFeedback
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerButton
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerEvent
import com.pashkd.krender.engine.tools.skin.SkinPreviewPointerEventType
import com.pashkd.krender.engine.tools.skin.SkinEditorPreviewStageInfo
import com.pashkd.krender.engine.tools.skin.SkinLoadResult
import com.pashkd.krender.engine.tools.skin.StyleKey
import com.pashkd.krender.engine.tools.skin.WidgetPreviewFactory

data class GdxSkinPreviewBuildResult(
    val previewInfo: SkinEditorPreviewStageInfo,
    val issues: List<PreviewBuildIssue> = emptyList(),
)

/** GDX adapter responsible only for Scene2D preview stage construction/rendering. */
class GdxSkinEditorPreview(
    private val logger: Logger,
) : Disposable {
    private val stage = Stage(FitViewport(1f, 1f))
    private val safeWidgetBuilder = SafeWidgetBuilder()
    private val previewFactory = WidgetPreviewFactory()
    private val checkerboardShapes = ShapeRenderer()
    private val selectedStyleShapes = ShapeRenderer()
    private var selectedStyleActors: List<com.badlogic.gdx.scenes.scene2d.Actor> = emptyList()
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var logicalWidth = 1
    private var logicalHeight = 1
    private var previewScale = 1f
    private var showCheckerboard = true
    private var highlightSelectedStyle = true
    private var cameraPanX = 0f
    private var cameraPanY = 0f
    private var cameraZoom = 1f
    private var contentScreenX = 0
    private var contentScreenY = 0
    private var contentScreenWidth = 1
    private var contentScreenHeight = 1
    private var contentGlX = 0
    private var contentGlY = 0

    fun rebuild(
        loadResult: SkinLoadResult,
        layout: PreviewLayout,
        loadedSkin: LoadedSkinHandle?,
        editSession: SkinEditSession,
        previewText: SkinPreviewTextSettings,
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
                                text = previewText,
                            ),
                        factory = previewFactory,
                    ),
                loadedSkin = loadedSkin,
                editSession = editSession,
                selectedStyleKey = selectedStyleKey,
            )
        val root = buildResult.actor
        selectedStyleActors = buildResult.selectedStyleActors
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

    fun handlePointerEvent(event: SkinPreviewPointerEvent): SkinPreviewInteractionFeedback {
        val insideContent = containsScreenPosition(event.screenX, event.screenY)
        val manualStageCoords = screenToStageCoordinates(event.screenX, event.screenY)
        val dispatchedScreenCoords = stage.stageToScreenCoordinates(manualStageCoords.cpy())
        logger.debug(TAG) {
            "Preview pointer event type=${event.type} screen=(${event.screenX.toInt()},${event.screenY.toInt()}) " +
                "manualStage=(${manualStageCoords.x},${manualStageCoords.y}) dispatchedScreen=(${dispatchedScreenCoords.x},${dispatchedScreenCoords.y}) " +
                "button=${event.button} inside=$insideContent content=($contentScreenX,$contentScreenY ${contentScreenWidth}x$contentScreenHeight) " +
                "viewport=($viewportX,$viewportY ${viewportWidth}x$viewportHeight)"
        }
        if (!insideContent) {
            return currentInteractionFeedback(status = "Pointer is outside preview content.")
        }
        val button = toGdxButton(event.button)
        when (event.type) {
            SkinPreviewPointerEventType.Move -> stage.mouseMoved(dispatchedScreenCoords.x.toInt(), dispatchedScreenCoords.y.toInt())
            SkinPreviewPointerEventType.Down -> stage.touchDown(dispatchedScreenCoords.x.toInt(), dispatchedScreenCoords.y.toInt(), event.pointer, button)
            SkinPreviewPointerEventType.Drag -> stage.touchDragged(dispatchedScreenCoords.x.toInt(), dispatchedScreenCoords.y.toInt(), event.pointer)
            SkinPreviewPointerEventType.Up -> stage.touchUp(dispatchedScreenCoords.x.toInt(), dispatchedScreenCoords.y.toInt(), event.pointer, button)
            SkinPreviewPointerEventType.Scroll -> stage.scrolled(0f, event.scrollAmountY)
        }
        val status =
            when (event.type) {
                SkinPreviewPointerEventType.Down -> "Preview widget pointer down."
                SkinPreviewPointerEventType.Up -> "Preview widget pointer up."
                SkinPreviewPointerEventType.Scroll -> "Preview widget scroll forwarded."
                else -> null
            }
        return currentInteractionFeedback(event.screenX, event.screenY, status)
    }

    fun setCanvasViewport(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        logicalWidth: Int,
        logicalHeight: Int,
        scale: Float,
        showCheckerboard: Boolean,
        showBounds: Boolean,
        highlightSelectedStyle: Boolean,
        cameraPanX: Float,
        cameraPanY: Float,
        cameraZoom: Float,
    ) {
        viewportX = x.coerceAtLeast(0)
        viewportY = y.coerceAtLeast(0)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        this.logicalWidth = logicalWidth.coerceAtLeast(1)
        this.logicalHeight = logicalHeight.coerceAtLeast(1)
        previewScale = scale.coerceIn(0.5f, 1.5f)
        this.showCheckerboard = showCheckerboard
        this.highlightSelectedStyle = highlightSelectedStyle
        this.cameraPanX = cameraPanX
        this.cameraPanY = cameraPanY
        this.cameraZoom = cameraZoom.coerceIn(0.25f, 4f)
        stage.viewport.setWorldSize(this.logicalWidth.toFloat(), this.logicalHeight.toFloat())
        stage.viewport.update(viewportWidth, viewportHeight, false)
        (stage.camera as? OrthographicCamera)?.apply {
            position.set(
                this@GdxSkinEditorPreview.logicalWidth / 2f + this@GdxSkinEditorPreview.cameraPanX,
                this@GdxSkinEditorPreview.logicalHeight / 2f + this@GdxSkinEditorPreview.cameraPanY,
                0f,
            )
            zoom = (1f / previewScale) / this@GdxSkinEditorPreview.cameraZoom
            update()
        }
        updateStageScreenBounds()
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
        logger.debug(TAG) {
            "Preview render viewport=($safeViewportX,$safeViewportY ${safeViewportWidth}x$safeViewportHeight) " +
                "contentScreen=($contentScreenX,$contentScreenY ${contentScreenWidth}x$contentScreenHeight) " +
                "contentGl=($contentGlX,$contentGlY ${contentScreenWidth}x$contentScreenHeight) logical=${logicalWidth}x$logicalHeight scale=$previewScale cameraZoom=$cameraZoom " +
                "cameraPan=($cameraPanX,$cameraPanY)"
        }
        stage.viewport.setScreenBounds(contentGlX, contentGlY, contentScreenWidth, contentScreenHeight)
        stage.viewport.apply(false)
        drawCheckerboardBackground()
        stage.draw()
        drawSelectedStyleHighlights()

        if (!scissorWasEnabled) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        } else {
            Gdx.gl.glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3])
        }
        Gdx.gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    override fun dispose() {
        safeWidgetBuilder.dispose()
        checkerboardShapes.dispose()
        selectedStyleShapes.dispose()
        stage.dispose()
    }

    private fun drawCheckerboardBackground() {
        if (!showCheckerboard) return
        val visibleBounds = visibleWorldBounds()
        val startX = kotlin.math.floor(visibleBounds.x / CheckerCellSize) * CheckerCellSize
        val endX = visibleBounds.x + visibleBounds.width
        val startY = kotlin.math.floor(visibleBounds.y / CheckerCellSize) * CheckerCellSize
        val endY = visibleBounds.y + visibleBounds.height
        checkerboardShapes.projectionMatrix = stage.camera.combined
        checkerboardShapes.begin(ShapeRenderer.ShapeType.Filled)
        var rowIndex = 0
        var y = startY
        while (y < endY) {
            var columnIndex = 0
            var x = startX
            while (x < endX) {
                checkerboardShapes.color =
                    if ((rowIndex + columnIndex) % 2 == 0) {
                        CheckerLightColor
                    } else {
                        CheckerDarkColor
                    }
                checkerboardShapes.rect(x, y, CheckerCellSize, CheckerCellSize)
                x += CheckerCellSize
                columnIndex++
            }
            y += CheckerCellSize
            rowIndex++
        }
        checkerboardShapes.end()
    }

    private fun drawSelectedStyleHighlights() {
        if (!highlightSelectedStyle) return
        if (selectedStyleActors.isEmpty()) return
        selectedStyleShapes.projectionMatrix = stage.camera.combined
        selectedStyleShapes.begin(ShapeRenderer.ShapeType.Line)
        selectedStyleShapes.color = SelectedStyleHighlightColor
        selectedStyleActors.forEach { actor ->
            if (!actor.isVisible || actor.width <= 0f || actor.height <= 0f) return@forEach
            val bottomLeft = actor.localToStageCoordinates(Vector2(0f, 0f))
            val bottomRight = actor.localToStageCoordinates(Vector2(actor.width, 0f))
            val topRight = actor.localToStageCoordinates(Vector2(actor.width, actor.height))
            val topLeft = actor.localToStageCoordinates(Vector2(0f, actor.height))
            selectedStyleShapes.line(bottomLeft, bottomRight)
            selectedStyleShapes.line(bottomRight, topRight)
            selectedStyleShapes.line(topRight, topLeft)
            selectedStyleShapes.line(topLeft, bottomLeft)
        }
        selectedStyleShapes.end()
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

    private fun containsScreenPosition(
        screenX: Float,
        screenY: Float,
    ): Boolean =
        screenX >= contentScreenX &&
            screenX < contentScreenX + contentScreenWidth &&
            screenY >= contentScreenY &&
            screenY < contentScreenY + contentScreenHeight

    private fun visibleWorldBounds(): com.badlogic.gdx.math.Rectangle {
        val visibleWorldWidth = logicalWidth * ((1f / previewScale) / cameraZoom)
        val visibleWorldHeight = logicalHeight * ((1f / previewScale) / cameraZoom)
        return com.badlogic.gdx.math.Rectangle(
            logicalWidth * 0.5f + cameraPanX - visibleWorldWidth * 0.5f,
            logicalHeight * 0.5f + cameraPanY - visibleWorldHeight * 0.5f,
            visibleWorldWidth,
            visibleWorldHeight,
        )
    }

    private fun updateStageScreenBounds() {
        val graphicsHeight = Gdx.graphics.height.coerceAtLeast(1)
        val safeViewportX = viewportX.coerceAtLeast(0)
        val safeViewportY = viewportY.coerceAtLeast(0)
        val safeViewportWidth = viewportWidth.coerceAtLeast(1)
        val safeViewportHeight = viewportHeight.coerceAtLeast(1)
        val glY = (graphicsHeight - safeViewportY - safeViewportHeight).coerceAtLeast(0)
        val contentWidth = stage.viewport.screenWidth.coerceAtLeast(1)
        val contentHeight = stage.viewport.screenHeight.coerceAtLeast(1)
        contentGlX = safeViewportX + (safeViewportWidth - contentWidth) / 2
        contentGlY = glY + (safeViewportHeight - contentHeight) / 2
        contentScreenX = contentGlX
        contentScreenY = graphicsHeight - contentGlY - contentHeight
        contentScreenWidth = contentWidth
        contentScreenHeight = contentHeight
        logger.debug(TAG) {
            "Preview bounds synced viewport=($safeViewportX,$safeViewportY ${safeViewportWidth}x$safeViewportHeight) " +
                "contentScreen=($contentScreenX,$contentScreenY ${contentScreenWidth}x$contentScreenHeight) " +
                "contentGl=($contentGlX,$contentGlY ${contentScreenWidth}x$contentScreenHeight)"
        }
    }

    private fun currentInteractionFeedback(
        screenX: Float? = null,
        screenY: Float? = null,
        status: String? = null,
    ): SkinPreviewInteractionFeedback =
        if (screenX != null && screenY != null) {
                val manualCoords = screenToStageCoordinates(screenX, screenY)
                val dispatchedScreenCoords = stage.stageToScreenCoordinates(manualCoords.cpy())
                val stageCoords = stage.screenToStageCoordinates(dispatchedScreenCoords.cpy())
                val hitActor = stage.hit(stageCoords.x, stageCoords.y, false) ?: stage.hit(manualCoords.x, manualCoords.y, false)
                val localCanvasX = (screenX - contentScreenX).coerceIn(0f, contentScreenWidth.toFloat())
                val localCanvasY = (screenY - contentScreenY).coerceIn(0f, contentScreenHeight.toFloat())
                logger.debug(TAG) {
                    "Preview hit-test screen=(${screenX.toInt()},${screenY.toInt()}) dispatchedScreen=(${dispatchedScreenCoords.x},${dispatchedScreenCoords.y}) " +
                        "stage=(${stageCoords.x},${stageCoords.y}) manualStage=(${manualCoords.x},${manualCoords.y}) " +
                        "actor=${hitActor?.let(::actorPath) ?: "<none>"}"
                }
            SkinPreviewInteractionFeedback(
                hoveredActorPath = hitActor?.let(::actorPath),
                focusedActorPath = (stage.keyboardFocus ?: stage.scrollFocus ?: hitActor)?.let(::actorPath),
                lastInputStatus = status,
                cursorCanvasX = localCanvasX,
                cursorCanvasY = localCanvasY,
                cursorStageX = manualCoords.x,
                cursorStageY = manualCoords.y,
            )
        } else {
            SkinPreviewInteractionFeedback(lastInputStatus = status)
        }

    private fun actorPath(actor: Actor): String {
        val segments = mutableListOf<String>()
        var current: Actor? = actor
        while (current != null) {
            val name = current.name?.takeIf(String::isNotBlank)
            val label = name ?: current.javaClass.simpleName
            segments += label
            current = current.parent
        }
        return segments.asReversed().joinToString(" / ")
    }

    private fun toGdxButton(button: SkinPreviewPointerButton): Int =
        when (button) {
            SkinPreviewPointerButton.Left -> 0
            SkinPreviewPointerButton.Right -> 1
            SkinPreviewPointerButton.Middle -> 2
        }

    private fun screenToStageCoordinates(
        screenX: Float,
        screenY: Float,
    ): Vector2 {
        val localX = (screenX - contentScreenX).coerceIn(0f, contentScreenWidth.toFloat())
        val localY = (screenY - contentScreenY).coerceIn(0f, contentScreenHeight.toFloat())
        val visibleWorldWidth = logicalWidth * ((1f / previewScale) / cameraZoom)
        val visibleWorldHeight = logicalHeight * ((1f / previewScale) / cameraZoom)
        val cameraCenterX = logicalWidth * 0.5f + cameraPanX
        val cameraCenterY = logicalHeight * 0.5f + cameraPanY
        val stageX = cameraCenterX - visibleWorldWidth * 0.5f + localX / contentScreenWidth * visibleWorldWidth
        val stageY = cameraCenterY + visibleWorldHeight * 0.5f - localY / contentScreenHeight * visibleWorldHeight
        return Vector2(stageX, stageY)
    }

    private companion object {
        private const val TAG = "GdxSkinEditorPreview"
        private const val CheckerCellSize = 32f
        private val CheckerLightColor = Color(0.23f, 0.24f, 0.27f, 1f)
        private val CheckerDarkColor = Color(0.15f, 0.16f, 0.19f, 1f)
        private val SelectedStyleHighlightColor = Color(1f, 0.55f, 0.08f, 1f)
    }
}
