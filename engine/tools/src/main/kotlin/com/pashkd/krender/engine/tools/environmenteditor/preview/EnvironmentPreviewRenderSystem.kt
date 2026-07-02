package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.tools.environmenteditor.EnvironmentEditorState

/** Submits the configured test model through the shared glTF/PBR renderer path. */
class EnvironmentPreviewRenderSystem(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentPreviewController,
) : System() {
    override fun render(
        world: SceneWorld,
        alpha: Float,
    ) {
        val previewEntity = state.previewModelEntityId?.let(world::getEntity) ?: return
        val model = checkNotNull(previewEntity.get<ModelComponent>())
        val transform = checkNotNull(previewEntity.get<TransformComponent>())
        world.renderCommands.submit(
            DrawModel(
                entityId = previewEntity.id,
                model = model.model,
                transform = transform.snapshot(),
                material = model.material,
                gltfRenderer = controller.gltfRendererSettings(state),
            ),
        )
    }
}
