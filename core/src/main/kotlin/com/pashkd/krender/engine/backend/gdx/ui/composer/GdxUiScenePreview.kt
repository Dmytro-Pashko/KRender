package com.pashkd.krender.engine.backend.gdx.ui.composer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.ui.runtime.scene.GdxUiSceneBuilder
import com.pashkd.krender.engine.uicomposer.UiComposerActorPreviewInfo
import com.pashkd.krender.engine.uicomposer.UiComposerCanvasHit
import com.pashkd.krender.engine.ui.scene.UiSceneDocument

/**
 * LibGDX Scene2D preview owned by the read-only UiComposer editor tool.
 *
 * This class belongs to the backend preview layer, not RuntimeUiService,
 * gameplay UI, the shared `.krui` model, or a completed editing pipeline. It
 * builds the currently loaded document directly into a private Stage so editor
 * preview stays independent from runtime UI layers. It intentionally does not
 * implement saving, property editing, node editing, drag/drop canvas editing,
 * Skin editing, Asset Browser picking, asset-id references, editable payloads,
 * multi-select, snapping, transform gizmos, resize handles, canvas structure
 * editing, or full Scene2D actor serialization; selected-node and hover
 * highlighting are best-effort Scene2D debug drawing.
 */
class GdxUiScenePreview(
    private val logger: Logger,
) : Disposable {
    private val stage = Stage(ScreenViewport())
    private val builder = GdxUiSceneBuilder(logger)
    private val actorsByNodeId = linkedMapOf<String, Actor>()
    private val nodeIdsByActor = mutableMapOf<Actor, String>()
    private var showBounds = true
    private var highlightSelected = true
    private var selectedNodeId: String? = null
    private var highlightHovered = true
    private var hoveredNodeId: String? = null

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
        if (document == null) return

        val actor = builder.build(
            document = document,
            payload = payload,
            actionHandler = null,
            onActorBuilt = { node, builtActor ->
                actorsByNodeId[node.id] = builtActor
                nodeIdsByActor[builtActor] = node.id
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
     * Draws the preview Stage over a neutral editor background and under ImGui.
     *
     * The UiComposer preview owns the editor background for this scene so
     * transparent Scene2D widgets remain visible. Runtime UI backends must not
     * clear the backbuffer this way. This preview still intentionally draws
     * fullscreen rather than embedding into an ImGui child viewport.
     */
    fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.075f, 0.078f, 0.088f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glColorMask(true, true, true, true)
        stage.draw()
        Gdx.gl.glDepthMask(true)
    }

    /**
     * Resizes the preview Stage viewport to match the editor tool window.
     *
     * This is presentation-only plumbing; it does not change `.krui` layout data,
     * save files, or introduce an embedded ImGui viewport.
     */
    fun resize(
        width: Int,
        height: Int,
    ) {
        stage.viewport.update(width.coerceAtLeast(1), height.coerceAtLeast(1), true)
    }

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
        stage.setDebugAll(showBounds)
        if (highlightSelected) {
            actorsByNodeId[selectedNodeId]?.setDebug(true)
        }
        if (highlightHovered) {
            actorsByNodeId[hoveredNodeId]?.setDebug(true)
        }
    }

    /**
     * Hit-tests the editor preview Stage and returns the closest mapped `.krui` node.
     *
     * This belongs to editor canvas interaction on top of the backend preview.
     * It converts screen coordinates through the preview viewport, asks Scene2D
     * for the topmost touchable actor, then walks up parent actors until it finds
     * one built from a `.krui` node because widgets may contain internal child
     * actors. It intentionally does not dispatch runtime UI actions, mutate or
     * save `.krui`, drag/drop, resize, add/delete/reorder nodes, support
     * multi-select, snap, show transform gizmos, edit Skins, pick assets, solve
     * layout, or serialize Scene2D actors.
     */
    fun hitTest(
        screenX: Int,
        screenY: Int,
    ): UiComposerCanvasHit? {
        val stageCoordinates = stage.viewport.unproject(Vector2(screenX.toFloat(), screenY.toFloat()))
        val actor = stage.hit(stageCoordinates.x, stageCoordinates.y, true) ?: return null
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
        stage.dispose()
        builder.dispose()
    }

    private fun clearActorDebug(actor: Actor) {
        actor.setDebug(false)
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

    companion object {
        private const val TAG = "GdxUiScenePreview"
    }
}
