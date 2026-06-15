package com.pashkd.krender.engine.tools.sceneeditor

import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.sceneeditor.SceneEditorBoundsProvider
import com.pashkd.krender.engine.sceneeditor.SceneEditorLocalBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SceneEditorSelectionSystemTest {
    @Test
    fun `left click in focused viewport selects closest document entity`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.transform.position.set(0f, 0f, 5f)
        val state = SceneEditorState(viewportFocused = true)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Selected Target.", state.statusMessage)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `left click uses transformed entity bounds for selection`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Wide Target")
        target.transform.position.set(1.5f, 0f, 5f)
        target.transform.scale.set(4f, 1f, 1f)
        target.add(ModelComponent(AssetRef.model("models/wide.glb")))
        val state = SceneEditorState(viewportFocused = true)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Selected Wide Target.", state.statusMessage)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `left click uses viewport-local coordinates when viewport has a screen rect`() {
        val input =
            FakeInputService(
                InputSnapshot(
                    mouseButtonsPressedThisFrame = setOf(MouseButton.Left),
                    mousePosition = Vec2(150f, 100f),
                    viewportSize = Vec2(400f, 300f),
                    uiCapturesMouse = true,
                ),
            )
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Offset Viewport Target")
        target.transform.position.set(0f, 0f, 5f)
        val state =
            SceneEditorState(
                viewportFocused = true,
                viewportOrigin = Vec2(100f, 50f),
                viewportSize = Vec2(100f, 100f),
            )
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Selected Offset Viewport Target.", state.statusMessage)
    }

    @Test
    fun `left click outside viewport rect uses window coordinates when ui does not capture mouse`() {
        val input =
            FakeInputService(
                InputSnapshot(
                    mouseButtonsPressedThisFrame = setOf(MouseButton.Left),
                    mousePosition = Vec2(50f, 50f),
                    viewportSize = Vec2(100f, 100f),
                    uiCapturesMouse = false,
                ),
            )
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Window Target")
        target.transform.position.set(0f, 0f, 5f)
        val state =
            SceneEditorState(
                viewportFocused = false,
                viewportOrigin = Vec2(500f, 500f),
                viewportSize = Vec2(100f, 100f),
            )
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Selected Window Target.", state.statusMessage)
    }

    @Test
    fun `left click outside viewport rect is ignored`() {
        val input =
            FakeInputService(
                InputSnapshot(
                    mouseButtonsPressedThisFrame = setOf(MouseButton.Left),
                    mousePosition = Vec2(20f, 20f),
                    viewportSize = Vec2(400f, 300f),
                    uiCapturesMouse = true,
                ),
            )
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.transform.position.set(0f, 0f, 5f)
        val state =
            SceneEditorState(
                selectedEntityId = target.id,
                viewportFocused = true,
                viewportOrigin = Vec2(100f, 50f),
                viewportSize = Vec2(100f, 100f),
            )
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Scene Editor ready.", state.statusMessage)
    }

    @Test
    fun `left click uses provided actual model bounds for selection`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Actual Bounds Target")
        target.transform.position.set(1.5f, 0f, 5f)
        target.add(ModelComponent(AssetRef.model("models/actual.glb")))
        val boundsProvider =
            SceneEditorBoundsProvider(
                FakeModelBoundsService(
                    "models/actual.glb" to
                        SceneEditorLocalBounds(
                            min = Vec3(-2f, -0.5f, -0.5f),
                            max = Vec3(2f, 0.5f, 0.5f),
                        ),
                ),
            )
        val state = SceneEditorState(viewportFocused = true)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger, boundsProvider))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Selected Actual Bounds Target.", state.statusMessage)
    }

    @Test
    fun `left click picks nearest entity bounds hit`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val far = document.world.createEntity("Far")
        far.transform.position.set(0f, 0f, 8f)
        far.add(ModelComponent(AssetRef.model("models/far.glb")))
        val near = document.world.createEntity("Near")
        near.transform.position.set(0f, 0f, 4f)
        near.add(ModelComponent(AssetRef.model("models/near.glb")))
        val state = SceneEditorState(viewportFocused = true)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(near.id, state.selectedEntityId)
        assertEquals("Selected Near.", state.statusMessage)
    }

    @Test
    fun `left click on empty space clears selection`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.transform.position.set(8f, 0f, 5f)
        val state =
            SceneEditorState(
                selectedEntityId = target.id,
                viewportFocused = true,
            )
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(null, state.selectedEntityId)
        assertEquals("Selection cleared.", state.statusMessage)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `selection skips editor-only and inactive entities`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val inactive = document.world.createEntity("Inactive")
        inactive.transform.position.set(0f, 0f, 5f)
        inactive.active = false
        val editorOnly = document.world.createEntity("Editor Only")
        editorOnly.transform.position.set(0f, 0f, 4f)
        editorOnly.add(EditorOnlyComponent())
        val state = SceneEditorState(viewportFocused = true)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(null, state.selectedEntityId)
        assertEquals("Selection cleared.", state.statusMessage)
    }

    @Test
    fun `selection is ignored while camera is navigating`() {
        val input = FakeInputService(centerLeftClick())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.transform.position.set(0f, 0f, 5f)
        val state =
            SceneEditorState(
                selectedEntityId = target.id,
                viewportFocused = true,
            )
        state.camera.navigating = true
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.update(dt = 0f)

        assertEquals(target.id, state.selectedEntityId)
        assertEquals("Scene Editor ready.", state.statusMessage)
    }

    @Test
    fun `render draws three selected entity marker lines`() {
        val input = FakeInputService(InputSnapshot())
        val document = SceneEditorDocument(SceneWorld())
        val target = document.world.createEntity("Target")
        target.transform.position.set(0f, 0f, 5f)
        val state = SceneEditorState(selectedEntityId = target.id)
        val world = runtimeWorldWithEditorCamera()
        world.systems.add(SceneEditorSelectionSystem(input, document, state, NoopLogger))

        world.render(alpha = 0f)

        val commands = world.renderCommands.snapshot()
        assertEquals(3, commands.size)
        commands.forEach { command -> assertIs<DrawLine>(command) }
    }

    private fun centerLeftClick(): InputSnapshot =
        InputSnapshot(
            mouseButtonsPressedThisFrame = setOf(MouseButton.Left),
            mousePosition = Vec2(50f, 50f),
            viewportSize = Vec2(100f, 100f),
            uiCapturesMouse = true,
        )

    private fun runtimeWorldWithEditorCamera(): SceneWorld {
        val world = SceneWorld()
        val camera = world.createEntity("Editor Camera")
        camera.add(SceneEditorCameraComponent())
        camera.add(PerspectiveCameraComponent())
        camera.get<TransformComponent>()?.position?.set(0f, 0f, 0f)
        camera.get<TransformComponent>()?.eulerDegrees?.set(0f, 0f, 0f)
        return world
    }

    private class FakeInputService(
        private var currentSnapshot: InputSnapshot,
    ) : InputService {
        override fun beginFrame() = Unit

        override fun snapshot(): InputSnapshot = currentSnapshot

        override fun endFrame() = Unit

        override fun setCursorCaptured(captured: Boolean) = Unit

        override fun isActionPressed(action: Action): Boolean = false

        override fun isActionJustPressed(action: Action): Boolean = false

        override fun axis(axis: Axis): Float = 0f
    }

    private class FakeModelBoundsService(
        vararg entries: Pair<String, SceneEditorLocalBounds>,
    ) : ModelBoundsService {
        private val boundsByPath = entries.toMap()

        override fun boundsFor(model: AssetRef<ModelAsset>): SceneEditorLocalBounds? = boundsByPath[model.path]
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            error: Throwable?,
            message: () -> String,
        ) = Unit
    }
}
