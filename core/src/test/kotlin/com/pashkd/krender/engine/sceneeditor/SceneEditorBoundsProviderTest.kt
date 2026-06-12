package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.ModelComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneEditorBoundsProviderTest {
    @Test
    fun `returns actual model bounds when model bounds service provides them`() {
        val model = AssetRef.model("models/actual.glb")
        val actualBounds =
            SceneEditorLocalBounds(
                min = Vec3(-2f, -1f, -3f),
                max = Vec3(2f, 4f, 3f),
            )
        val provider = SceneEditorBoundsProvider(FakeModelBoundsService(model.path to actualBounds))
        val entity = SceneWorld().createEntity("Model")
        entity.add(ModelComponent(model))

        assertEquals(actualBounds, provider.boundsFor(entity))
    }

    @Test
    fun `falls back to unit model bounds when model bounds service has no bounds`() {
        val provider = SceneEditorBoundsProvider(FakeModelBoundsService())
        val entity = SceneWorld().createEntity("Model")
        entity.add(ModelComponent(AssetRef.model("models/unloaded.glb")))

        assertEquals(
            SceneEditorLocalBounds(
                min = Vec3(-0.5f, -0.5f, -0.5f),
                max = Vec3(0.5f, 0.5f, 0.5f),
            ),
            provider.boundsFor(entity),
        )
    }

    private class FakeModelBoundsService(
        vararg entries: Pair<String, SceneEditorLocalBounds>,
    ) : ModelBoundsService {
        private val boundsByPath = entries.toMap()

        override fun boundsFor(model: AssetRef<ModelAsset>): SceneEditorLocalBounds? = boundsByPath[model.path]
    }
}
