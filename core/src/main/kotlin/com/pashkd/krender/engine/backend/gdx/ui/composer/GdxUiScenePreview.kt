package com.pashkd.krender.engine.backend.gdx.ui.composer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.ui.runtime.scene.GdxUiSceneBuilder
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
 * or full Scene2D actor serialization; selected-node highlighting is
 * best-effort Scene2D debug drawing.
 */
class GdxUiScenePreview(
    private val logger: Logger,
) : Disposable {
    private val stage = Stage(ScreenViewport())
    private val builder = GdxUiSceneBuilder(logger)
    private val actorsByNodeId = linkedMapOf<String, Actor>()
    private var showBounds = true
    private var selectedNodeId: String? = null

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
        if (document == null) return

        val actor = builder.build(
            document = document,
            payload = payload,
            actionHandler = null,
            onActorBuilt = { node, builtActor ->
                actorsByNodeId[node.id] = builtActor
            },
        )
        stage.addActor(actor)
        updateDebugOverlay(showBounds, selectedNodeId)
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
     * Draws the preview Stage over the main renderer and under ImGui.
     *
     * The method assumes the engine renderer already cleared the backbuffer. It
     * intentionally draws fullscreen rather than embedding into an ImGui child
     * viewport; that viewport integration is left for a later composer phase.
     */
    fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
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
     * every Scene2D actor has a distinct visual style.
     */
    fun updateDebugOverlay(
        showBounds: Boolean,
        selectedNodeId: String?,
    ) {
        this.showBounds = showBounds
        this.selectedNodeId = selectedNodeId
        clearActorDebug(stage.root)
        stage.setDebugAll(showBounds)
        actorsByNodeId[selectedNodeId]?.setDebug(true)
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

    companion object {
        private const val TAG = "GdxUiScenePreview"
    }
}
