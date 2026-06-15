package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.sceneeditor.SceneEditorBoundsProvider
import com.pashkd.krender.engine.sceneeditor.SceneEditorLocalBounds
import com.pashkd.krender.engine.sceneeditor.transformedBoundsCorners
import com.pashkd.krender.engine.render3d.ModelComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SceneEditorBoundingBoxSystemTest {
    @Test
    fun `draws twelve selected bounding box edges`() {
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.add(ModelComponent(AssetRef.model("models/target.glb")))
        val state = SceneEditorState(selectedEntityId = target.id)
        val world = SceneWorld()
        world.systems.add(SceneEditorBoundingBoxSystem(document, state))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot()
        assertEquals(12, commands.size)
        commands.forEach { command -> assertIs<DrawLine>(command) }
    }

    @Test
    fun `draws selected bounding box from provided actual model bounds`() {
        val model = AssetRef.model("models/actual.glb")
        val actualBounds =
            SceneEditorLocalBounds(
                min = Vec3(-2f, -1f, -3f),
                max = Vec3(2f, 1f, 3f),
            )
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.add(ModelComponent(model))
        val state = SceneEditorState(selectedEntityId = target.id)
        val world = SceneWorld()
        val boundsProvider = SceneEditorBoundsProvider(FakeModelBoundsService(model.path to actualBounds))
        world.systems.add(SceneEditorBoundingBoxSystem(document, state, boundsProvider))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot().filterIsInstance<DrawLine>()
        assertEquals(12, commands.size)
        assertTrue(
            commands.any { command ->
                command.from == Vec3(-2f, -1f, -3f) && command.to == Vec3(2f, -1f, -3f)
            },
        )
    }

    @Test
    fun `respects selected bounding box toggle`() {
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.add(ModelComponent(AssetRef.model("models/target.glb")))
        val state =
            SceneEditorState(
                selectedEntityId = target.id,
                showSelectedBoundingBox = false,
            )
        val world = SceneWorld()
        world.systems.add(SceneEditorBoundingBoxSystem(document, state))

        world.render(alpha = 0f)

        assertTrue(world.renderCommands.snapshot().isEmpty())
        assertEquals(false, state.hasUnsavedChanges)
    }

    @Test
    fun `skips inactive and editor-only selected entities`() {
        val document = SceneEditorDocument(SceneWorld())
        val inactive = document.world.createEntity("Inactive")
        inactive.active = false
        val editorOnly = document.world.createEntity("Editor Only")
        editorOnly.add(EditorOnlyComponent())
        val state = SceneEditorState(selectedEntityId = inactive.id)
        val world = SceneWorld()
        world.systems.add(SceneEditorBoundingBoxSystem(document, state))

        world.render(alpha = 0f)
        assertTrue(world.renderCommands.snapshot().isEmpty())

        state.selectedEntityId = editorOnly.id
        world.render(alpha = 0f)
        assertTrue(world.renderCommands.snapshot().isEmpty())
    }

    @Test
    fun `transformed bounds corners apply position and scale`() {
        val transform =
            TransformComponent(
                position = Vec3(10f, 20f, 30f),
                scale = Vec3(2f, 3f, 4f),
            )
        val corners =
            transformedBoundsCorners(
                SceneEditorLocalBounds(Vec3(-1f, -1f, -1f), Vec3(1f, 1f, 1f)),
                transform,
            )

        assertEquals(Vec3(8f, 17f, 26f), corners[0])
        assertEquals(Vec3(12f, 23f, 34f), corners[6])
    }

    private class FakeModelBoundsService(
        vararg entries: Pair<String, SceneEditorLocalBounds>,
    ) : ModelBoundsService {
        private val boundsByPath = entries.toMap()

        override fun boundsFor(model: AssetRef<ModelAsset>): SceneEditorLocalBounds? = boundsByPath[model.path]
    }
}
