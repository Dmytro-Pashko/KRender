package com.pashkd.krender.engine.backend.gdx.ui.composer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.ui.runtime.scene.GdxUiSceneBuilder
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneTableOrientation
import com.pashkd.krender.engine.uicomposer.UiComposerActorPreviewInfo
import com.pashkd.krender.engine.uicomposer.UiComposerCanvasHit
import com.pashkd.krender.engine.uicomposer.UiComposerGuideBounds
import com.pashkd.krender.engine.uicomposer.UiComposerGuideRect
import com.pashkd.krender.engine.uicomposer.UiComposerGuideSnapshot
import com.pashkd.krender.engine.uicomposer.UiComposerVisualGuideOptions
import com.pashkd.krender.engine.uicomposer.clampPreviewZoom
import com.pashkd.krender.engine.uicomposer.computeAlignmentAnchor
import com.pashkd.krender.engine.uicomposer.computePaddingInnerRect
import kotlin.math.abs

/**
 * LibGDX Scene2D preview owned by the read-only UiComposer editor tool.
 *
 * This class belongs to the backend preview layer, not RuntimeUiService,
 * gameplay UI, the shared `.krui` model, or a completed editing pipeline. It
 * builds the currently loaded document directly into a private Stage so editor
 * preview stays independent from runtime UI layers. It intentionally does not
 * implement saving, property editing, node editing, drag/drop canvas editing,
 * Skin editing, Asset Browser drag/drop, asset-id references, editable payloads,
 * multi-select, snapping, transform gizmos, resize handles, canvas structure
 * editing, or full Scene2D actor serialization; selected-node and hover
 * highlighting are best-effort Scene2D debug drawing.
 */
class GdxUiScenePreview(
    private val logger: Logger,
) : Disposable {
    private val stage = Stage(FitViewport(1f, 1f))
    private val builder = GdxUiSceneBuilder(logger)
    private val guideShapeRenderer = ShapeRenderer()
    private val guideBatch = SpriteBatch()
    private val guideFont = BitmapFont()
    private val actorsByNodeId = linkedMapOf<String, Actor>()
    private val nodeIdsByActor = mutableMapOf<Actor, String>()
    private val nodesById = linkedMapOf<String, UiSceneNode>()
    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var logicalWidth = 1
    private var logicalHeight = 1
    private var cameraOffsetX = 0f
    private var cameraOffsetY = 0f
    private var previewZoom = 1f
    private var showBounds = true
    private var highlightSelected = true
    private var selectedNodeId: String? = null
    private var highlightHovered = true
    private var hoveredNodeId: String? = null
    private var visualGuideOptions = UiComposerVisualGuideOptions()

    /**
     * Rebuilds the preview Stage from [document] using simple string [payload] bindings.
     *
     * Passing null clears the preview. The preview does not save, edit, mutate the
     * document, edit Skins, resolve Asset Browser ids, or serialize Scene2D actors.
     */
    fun rebuild(
        document: UiSceneDocument?,
        payload: Map<String, String> = emptyMap(),
    ) {
        stage.clear()
        actorsByNodeId.clear()
        nodeIdsByActor.clear()
        nodesById.clear()
        if (document == null) return

        val actor = builder.build(
            document = document,
            payload = payload,
            actionHandler = null,
            onActorBuilt = { node, builtActor ->
                actorsByNodeId[node.id] = builtActor
                nodeIdsByActor[builtActor] = node.id
                nodesById[node.id] = node
            },
        )
        stage.addActor(actor)
        updateDebugOverlay(showBounds, highlightSelected, selectedNodeId, highlightHovered, hoveredNodeId)
        logger.debug(TAG) { "Rebuilt UI scene preview document='${document.id}' actors=${actorsByNodeId.size}" }
    }

    /**
     * Updates Scene2D actor actions and layout for the editor preview Stage.
     *
     * This belongs to backend preview rendering only and does not route input
     * through runtime UI layers or add editing interactions.
     */
    fun update(dt: Float) {
        stage.act(dt)
    }

    /**
     * Draws the preview Stage inside the editor Preview Canvas panel and under ImGui.
     *
     * The UiComposer preview owns only the canvas rectangle background for this
     * scene so transparent Scene2D widgets remain visible. Runtime UI backends
     * must not clear the backbuffer this way.
     */
    fun render() {
        if (viewportWidth <= 1 || viewportHeight <= 1) return

        val previousViewport = BufferUtils.newIntBuffer(4)
        val previousScissor = BufferUtils.newIntBuffer(4)
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, previousViewport)
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, previousScissor)
        val scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)

        // ImGui reports top-left screen coordinates; OpenGL viewport/scissor uses bottom-left.
        val glY = Gdx.graphics.height - viewportY - viewportHeight

        Gdx.gl.glViewport(viewportX, glY, viewportWidth, viewportHeight)
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glScissor(viewportX, glY, viewportWidth, viewportHeight)
        Gdx.gl.glClearColor(0.075f, 0.078f, 0.088f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glColorMask(true, true, true, true)
        stage.draw()
        renderVisualGuides()
        Gdx.gl.glDepthMask(true)

        if (!scissorWasEnabled) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        } else {
            Gdx.gl.glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3])
        }
        Gdx.gl.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
    }

    /**
     * Updates visual guide overlay options for the next render pass.
     *
     * These options are editor-only and never mutate `.krui`, dirty state, or
     * runtime UI behavior.
     */
    fun updateVisualGuideOptions(options: UiComposerVisualGuideOptions) {
        visualGuideOptions = options
    }

    /**
     * Returns a backend-neutral snapshot describing which semantic guide bounds
     * are available for the current selected and hovered nodes.
     */
    fun guideSnapshot(
        selectedNodeId: String?,
        hoveredNodeId: String?,
    ): UiComposerGuideSnapshot {
        val selectedActor = actorsByNodeId[selectedNodeId]
        val hoveredActor = actorsByNodeId[hoveredNodeId]
        return UiComposerGuideSnapshot(
            selected = selectedActor?.toGuideBounds(selectedNodeId),
            hovered = hoveredActor?.toGuideBounds(hoveredNodeId),
            parentChain = selectedActor?.mappedParentGuideBounds().orEmpty(),
        )
    }

    /**
     * Updates the screen-space render rectangle and logical Stage size for the
     * editor preview canvas.
     *
     * [x], [y], [width], and [height] are the effective preview rectangle in
     * ImGui screen coordinates. [logicalWidth] and [logicalHeight] are the
     * Scene2D Stage size selected by the resolution preset. This does not affect
     * runtime UI viewport configuration.
     */
    fun setCanvasViewport(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        logicalWidth: Int,
        logicalHeight: Int,
        cameraOffsetX: Float,
        cameraOffsetY: Float,
        previewZoom: Float,
    ) {
        viewportX = x.coerceAtLeast(0)
        viewportY = y.coerceAtLeast(0)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        this.logicalWidth = logicalWidth.coerceAtLeast(1)
        this.logicalHeight = logicalHeight.coerceAtLeast(1)
        this.cameraOffsetX = cameraOffsetX
        this.cameraOffsetY = cameraOffsetY
        this.previewZoom = clampPreviewZoom(previewZoom)

        stage.viewport.setWorldSize(this.logicalWidth.toFloat(), this.logicalHeight.toFloat())
        stage.viewport.update(viewportWidth, viewportHeight, false)
        applyPreviewCamera()
    }

    /**
     * Clears the editor preview canvas placement so rendering is skipped until
     * the Preview Canvas panel reports a valid effective rectangle again.
     */
    fun clearCanvasViewport() {
        viewportWidth = 1
        viewportHeight = 1
    }

    /**
     * Legacy window resize hook retained for the scene API. Panel drawing now
     * owns the preview viewport through [setCanvasViewport].
     */
    fun resize(
        @Suppress("UNUSED_PARAMETER")
        width: Int,
        @Suppress("UNUSED_PARAMETER")
        height: Int,
    ) = Unit

    /**
     * Toggles Scene2D debug bounds and best-effort selected-node highlighting.
     *
     * Scene2D debug drawing is intentionally used for the MVP instead of a custom
     * ShapeRenderer overlay. Selection is id-based and best-effort because not
     * every Scene2D actor has a distinct visual style. Hover feedback belongs to
     * editor canvas interaction and does not edit `.krui`, dispatch runtime
     * actions, drag/drop, resize, add/delete/reorder nodes, support multi-select,
     * snap, display transform gizmos, edit Skins, pick assets, or serialize
     * Scene2D actors.
     */
    fun updateDebugOverlay(
        showBounds: Boolean,
        highlightSelected: Boolean,
        selectedNodeId: String?,
        highlightHovered: Boolean,
        hoveredNodeId: String?,
    ) {
        this.showBounds = showBounds
        this.highlightSelected = highlightSelected
        this.selectedNodeId = selectedNodeId
        this.highlightHovered = highlightHovered
        this.hoveredNodeId = hoveredNodeId
        clearActorDebug(stage.root)
        stage.isDebugAll = showBounds
        if (highlightSelected) {
            actorsByNodeId[selectedNodeId]?.debug = true
        }
        if (highlightHovered) {
            actorsByNodeId[hoveredNodeId]?.debug = true
        }
    }

    /**
     * Hit-tests the preview Stage using effective-preview-rect-local coordinates.
     *
     * [localX] and [localY] are measured from the top-left corner of the
     * rendered preview rectangle. This function converts them into Scene2D Stage
     * coordinates based on the current logical preview resolution. It
     * intentionally does not dispatch runtime UI actions, mutate or save
     * `.krui`, drag/drop, resize, add/delete/reorder nodes, support multi-select,
     * snap, show transform gizmos, edit Skins, pick assets, solve layout, or
     * serialize Scene2D actors.
     */
    fun hitTestLocal(
        localX: Int,
        localY: Int,
    ): UiComposerCanvasHit? {
        if (viewportWidth <= 1 || viewportHeight <= 1) return null
        val visibleWorldWidth = logicalWidth.toFloat() / previewZoom
        val visibleWorldHeight = logicalHeight.toFloat() / previewZoom
        val cameraCenterX = logicalWidth.toFloat() * 0.5f + cameraOffsetX
        val cameraCenterY = logicalHeight.toFloat() * 0.5f + cameraOffsetY
        val stageX = cameraCenterX - visibleWorldWidth * 0.5f +
            localX.toFloat() / viewportWidth.toFloat() * visibleWorldWidth
        val stageY = cameraCenterY + visibleWorldHeight * 0.5f -
            localY.toFloat() / viewportHeight.toFloat() * visibleWorldHeight
        return hitTestStage(stageX, stageY)
    }

    /**
     * Converts effective-preview-rect-local coordinates into Scene2D Stage coordinates.
     */
    fun previewLocalToStage(
        localX: Float,
        localY: Float,
    ): Vector2 {
        val visibleWorldWidth = logicalWidth.toFloat() / previewZoom
        val visibleWorldHeight = logicalHeight.toFloat() / previewZoom
        val cameraCenterX = logicalWidth.toFloat() * 0.5f + cameraOffsetX
        val cameraCenterY = logicalHeight.toFloat() * 0.5f + cameraOffsetY
        val stageX = cameraCenterX - visibleWorldWidth * 0.5f +
            localX / viewportWidth.toFloat() * visibleWorldWidth
        val stageY = cameraCenterY + visibleWorldHeight * 0.5f -
            localY / viewportHeight.toFloat() * visibleWorldHeight
        return Vector2(stageX, stageY)
    }

    /**
     * Hit-tests the editor preview Stage and returns the closest mapped `.krui` node.
     *
     * Kept for older call sites and diagnostics. New canvas interaction should
     * use [hitTestLocal] after checking [UiComposerCanvasRect]-scoped bounds.
     */
    fun hitTest(
        screenX: Int,
        screenY: Int,
    ): UiComposerCanvasHit? {
        val stageCoordinates = stage.viewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))
        return hitTestStage(stageCoordinates.x, stageCoordinates.y)
    }

    /**
     * Returns immutable preview actor metadata for a selected `.krui` node id.
     *
     * This belongs to editor preview diagnostics and deliberately avoids exposing
     * mutable LibGDX Actor objects to editor panels. It does not edit, save,
     * serialize actors, modify Skins, add drag/drop, or create runtime UI state.
     */
    fun actorInfo(nodeId: String?): UiComposerActorPreviewInfo? {
        val id = nodeId ?: return null
        val actor = actorsByNodeId[id] ?: return null
        return UiComposerActorPreviewInfo(
            nodeId = id,
            actorClass = actor.javaClass.simpleName.takeIf(String::isNotBlank) ?: actor.javaClass.name,
            x = actor.x,
            y = actor.y,
            width = actor.width,
            height = actor.height,
            visible = actor.isVisible,
        )
    }

    /**
     * Releases the private Stage and builder-owned Skin/Texture resources.
     *
     * This belongs to backend preview lifetime management and does not affect
     * RuntimeUiService layers or shared `.krui` model state.
     */
    override fun dispose() {
        guideShapeRenderer.dispose()
        guideBatch.dispose()
        guideFont.dispose()
        stage.dispose()
        builder.dispose()
    }

    private fun renderVisualGuides() {
        val selectedActor = actorsByNodeId[selectedNodeId]
        val hoveredActor = actorsByNodeId[hoveredNodeId]
        val selectedNode = nodesById[selectedNodeId]
        val projection = stage.camera.combined

        guideShapeRenderer.projectionMatrix = projection
        guideBatch.projectionMatrix = projection

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1f)
        guideShapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        if (visualGuideOptions.showParentChainGuides && selectedActor != null) {
            selectedActor.mappedParentGuideBounds().forEachIndexed { index, bounds ->
                guideShapeRenderer.color = parentGuideColor(index)
                drawRect(bounds.toRect())
            }
        }

        if (
            visualGuideOptions.showHoveredGuide &&
            hoveredActor != null &&
            hoveredNodeId != selectedNodeId
        ) {
            guideShapeRenderer.color = HoveredGuideColor
            drawRect(actorStageRect(hoveredActor))
        }

        if (visualGuideOptions.showSelectedGuide && selectedActor != null) {
            guideShapeRenderer.color = SelectedGuideColor
            drawRect(actorStageRect(selectedActor))
        }

        if (
            visualGuideOptions.showPaddingGuides &&
            selectedActor != null &&
            selectedNode != null &&
            selectedNode.hasMeaningfulPaddingGuide()
        ) {
            drawPaddingGuide(actorStageRect(selectedActor), selectedNode)
        }

        if (
            visualGuideOptions.showAlignmentGuide &&
            selectedActor != null &&
            selectedNode?.align != null
        ) {
            drawAlignmentMarker(actorStageRect(selectedActor), selectedNode)
        }

        if (
            visualGuideOptions.showTableOrientationGuide &&
            selectedActor != null &&
            selectedNode?.type == UiSceneNodeType.Table
        ) {
            drawTableOrientationMarker(actorStageRect(selectedActor), selectedNode)
        }

        guideShapeRenderer.end()
        Gdx.gl.glLineWidth(1f)

        guideBatch.begin()
        if (visualGuideOptions.showNodeIdLabel && selectedActor != null && selectedNode != null) {
            drawText(
                text = "${selectedNode.id} [${selectedNode.type}]",
                x = actorStageRect(selectedActor).x,
                y = labelY(actorStageRect(selectedActor)),
                color = LabelTextColor,
            )
        }
        if (
            visualGuideOptions.showTableOrientationGuide &&
            selectedActor != null &&
            selectedNode?.type == UiSceneNodeType.Table
        ) {
            drawTableOrientationText(actorStageRect(selectedActor), selectedNode)
        }
        if (
            visualGuideOptions.showAlignmentGuide &&
            selectedActor != null &&
            selectedNode?.align != null
        ) {
            val anchor = computeAlignmentAnchor(actorStageRect(selectedActor), selectedNode.align)
            drawText("align: ${selectedNode.align}", anchor.x + 6f, anchor.y + 14f, AlignmentGuideColor)
        }
        guideBatch.end()
    }

    private fun drawPaddingGuide(
        bounds: UiComposerGuideRect,
        node: UiSceneNode,
    ) {
        val inner = computePaddingInnerRect(bounds, node.padding)
        guideShapeRenderer.color = PaddingGuideColor
        drawRect(inner)
        guideShapeRenderer.line(bounds.left, bounds.top, inner.left, inner.top)
        guideShapeRenderer.line(bounds.right, bounds.top, inner.right, inner.top)
        guideShapeRenderer.line(bounds.left, bounds.bottom, inner.left, inner.bottom)
        guideShapeRenderer.line(bounds.right, bounds.bottom, inner.right, inner.bottom)
    }

    private fun drawAlignmentMarker(
        bounds: UiComposerGuideRect,
        node: UiSceneNode,
    ) {
        val align = node.align ?: return
        val anchor = computeAlignmentAnchor(bounds, align)
        guideShapeRenderer.color = AlignmentGuideColor
        guideShapeRenderer.line(anchor.x - MarkerRadius, anchor.y, anchor.x + MarkerRadius, anchor.y)
        guideShapeRenderer.line(anchor.x, anchor.y - MarkerRadius, anchor.x, anchor.y + MarkerRadius)
        guideShapeRenderer.line(anchor.x - MarkerRadius, anchor.y, anchor.x, anchor.y + MarkerRadius)
        guideShapeRenderer.line(anchor.x, anchor.y + MarkerRadius, anchor.x + MarkerRadius, anchor.y)
        guideShapeRenderer.line(anchor.x + MarkerRadius, anchor.y, anchor.x, anchor.y - MarkerRadius)
        guideShapeRenderer.line(anchor.x, anchor.y - MarkerRadius, anchor.x - MarkerRadius, anchor.y)
    }

    private fun drawTableOrientationMarker(
        bounds: UiComposerGuideRect,
        node: UiSceneNode,
    ) {
        val startX = bounds.x + 10f
        val startY = bounds.top - 22f
        guideShapeRenderer.color = TableGuideColor
        when (node.tableOrientation) {
            UiSceneTableOrientation.Vertical -> {
                guideShapeRenderer.line(startX, startY + 12f, startX, startY - 12f)
                guideShapeRenderer.line(startX, startY - 12f, startX - 5f, startY - 5f)
                guideShapeRenderer.line(startX, startY - 12f, startX + 5f, startY - 5f)
            }

            UiSceneTableOrientation.Horizontal -> {
                guideShapeRenderer.line(startX, startY, startX + 24f, startY)
                guideShapeRenderer.line(startX + 24f, startY, startX + 17f, startY + 5f)
                guideShapeRenderer.line(startX + 24f, startY, startX + 17f, startY - 5f)
            }
        }
    }

    private fun drawTableOrientationText(
        bounds: UiComposerGuideRect,
        node: UiSceneNode,
    ) {
        val arrow = when (node.tableOrientation) {
            UiSceneTableOrientation.Vertical -> "v"
            UiSceneTableOrientation.Horizontal -> ">"
        }
        drawText(
            text = "Table: ${node.tableOrientation} $arrow",
            x = bounds.x + 20f,
            y = bounds.top - 12f,
            color = TableGuideColor,
        )
    }

    private fun drawText(
        text: String,
        x: Float,
        y: Float,
        color: Color,
    ) {
        guideFont.color = color
        guideFont.draw(guideBatch, text, x, y)
    }

    private fun drawRect(rect: UiComposerGuideRect) {
        guideShapeRenderer.rect(rect.x, rect.y, rect.width, rect.height)
    }

    private fun actorStageRect(actor: Actor): UiComposerGuideRect {
        val bottomLeft = actor.localToStageCoordinates(Vector2(0f, 0f))
        val topRight = actor.localToStageCoordinates(Vector2(actor.width, actor.height))
        val x = minOf(bottomLeft.x, topRight.x)
        val y = minOf(bottomLeft.y, topRight.y)
        return UiComposerGuideRect(
            x = x,
            y = y,
            width = abs(topRight.x - bottomLeft.x),
            height = abs(topRight.y - bottomLeft.y),
        )
    }

    private fun visibleStageRect(): UiComposerGuideRect {
        val visibleWorldWidth = logicalWidth.toFloat() / previewZoom
        val visibleWorldHeight = logicalHeight.toFloat() / previewZoom
        val cameraCenterX = logicalWidth.toFloat() * 0.5f + cameraOffsetX
        val cameraCenterY = logicalHeight.toFloat() * 0.5f + cameraOffsetY
        return UiComposerGuideRect(
            x = cameraCenterX - visibleWorldWidth * 0.5f,
            y = cameraCenterY - visibleWorldHeight * 0.5f,
            width = visibleWorldWidth,
            height = visibleWorldHeight,
        )
    }

    private fun Actor.toGuideBounds(nodeId: String?): UiComposerGuideBounds {
        val bounds = actorStageRect(this)
        return UiComposerGuideBounds(
            nodeId = nodeId,
            label = nodeId ?: "<unmapped>",
            actorClass = javaClass.simpleName.takeIf(String::isNotBlank) ?: javaClass.name,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
        )
    }

    private fun Actor.mappedParentGuideBounds(): List<UiComposerGuideBounds> {
        val parents = mutableListOf<UiComposerGuideBounds>()
        var parent = this.parent
        while (parent != null && parent != stage.root) {
            val parentNodeId = nodeIdsByActor[parent]
            if (parentNodeId != null) {
                parents += parent.toGuideBounds(parentNodeId)
            }
            parent = parent.parent
        }
        return parents.asReversed()
    }

    private fun UiComposerGuideBounds.toRect(): UiComposerGuideRect =
        UiComposerGuideRect(x, y, width, height)

    private fun UiSceneNode.hasMeaningfulPaddingGuide(): Boolean =
        type == UiSceneNodeType.Container ||
            type == UiSceneNodeType.Table ||
            padding.left != 0f ||
            padding.top != 0f ||
            padding.right != 0f ||
            padding.bottom != 0f

    private fun labelY(bounds: UiComposerGuideRect): Float {
        val visible = visibleStageRect()
        val above = bounds.top + 16f
        return if (above <= visible.top) above else bounds.top - 6f
    }

    private fun parentGuideColor(index: Int): Color =
        Color(0.78f, 0.68f, 1f, (0.70f - index * 0.08f).coerceAtLeast(0.32f))

    private fun clearActorDebug(actor: Actor) {
        actor.debug = false
        // Scene2D debug flags live on each actor, so recursively reset children before reapplying.
        (actor as? Group)?.children?.forEach { child -> clearActorDebug(child) }
    }

    private fun findMappedActor(actor: Actor): Actor? {
        var current: Actor? = actor
        while (current != null) {
            // Scene2D widgets can hit internal children; the nearest mapped parent is the `.krui` node.
            if (current in nodeIdsByActor) return current
            current = current.parent
        }
        return null
    }

    private fun hitTestStage(
        stageX: Float,
        stageY: Float,
    ): UiComposerCanvasHit? {
        val actor = stage.hit(stageX, stageY, true) ?: return null
        val mappedActor = findMappedActor(actor) ?: return null
        val nodeId = nodeIdsByActor[mappedActor] ?: return null
        return UiComposerCanvasHit(
            nodeId = nodeId,
            actorClass = mappedActor.javaClass.simpleName.takeIf(String::isNotBlank) ?: mappedActor.javaClass.name,
            x = mappedActor.x,
            y = mappedActor.y,
            width = mappedActor.width,
            height = mappedActor.height,
        )
    }

    private fun applyPreviewCamera() {
        val camera = stage.camera as? OrthographicCamera ?: return
        camera.position.set(
            logicalWidth.toFloat() * 0.5f + cameraOffsetX,
            logicalHeight.toFloat() * 0.5f + cameraOffsetY,
            0f,
        )
        camera.zoom = 1f / previewZoom
        camera.update()
    }

    companion object {
        private const val TAG = "GdxUiScenePreview"
        private const val MarkerRadius = 7f
        private val SelectedGuideColor = Color(0.20f, 0.82f, 1f, 1f)
        private val HoveredGuideColor = Color(1f, 0.76f, 0.20f, 0.92f)
        private val PaddingGuideColor = Color(0.32f, 1f, 0.52f, 0.92f)
        private val AlignmentGuideColor = Color(1f, 0.35f, 0.82f, 0.95f)
        private val TableGuideColor = Color(0.92f, 0.92f, 0.28f, 0.95f)
        private val LabelTextColor = Color(0.72f, 0.94f, 1f, 1f)
    }
}
