package com.pashkd.krender.game

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.sceneeditor.SceneDescriptor
import com.pashkd.krender.engine.sceneeditor.SceneSerializer

/**
 * Runtime-only scene that loads saved `.krscene` content into an isolated world.
 */
class RuntimeScene(
    private val scenePath: String?,
) : Scene("runtime_scene") {
    override fun show() {
        world.systems.add(ModelRenderSystem())
        loadScene()
    }

    private fun loadScene() {
        val path = scenePath?.takeIf(String::isNotBlank)
        if (path == null) {
            engine.logger.error(TAG) { "Runtime scene path is missing." }
            createFallbackCamera()
            return
        }

        try {
            engine.logger.info(TAG) { "Loading runtime scene path='$path'" }
            val text = engine.sceneFiles.readText(path)
            val descriptor = SceneSerializer.decode(text)
            SceneSerializer.applyToWorld(descriptor, world, engine.logger)
            ensureRuntimeCamera(descriptor)
            engine.logger.info(TAG) {
                "Runtime scene loaded path='$path' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size}"
            }
        } catch (error: Exception) {
            engine.logger.error(TAG, error) { "Runtime scene load failed path='$path': ${error.message}" }
            world.clear()
            createFallbackCamera()
        }
    }

    private fun ensureRuntimeCamera(descriptor: SceneDescriptor) {
        val activeCameraEntityId = descriptor.settings.activeCameraEntityId
        val activeCamera = activeCameraEntityId
            ?.let(world::getEntity)
            ?.takeIf { entity -> entity.get<PerspectiveCameraComponent>() != null }

        if (activeCamera == null) {
            engine.logger.warn(TAG) {
                "No active runtime camera found for scene id='${descriptor.id}' activeCameraEntityId=${activeCameraEntityId ?: "<none>"}; creating fallback camera."
            }
            createFallbackCamera()
            return
        }

        activeCamera.add(ActiveCameraComponent())
        engine.logger.info(TAG) { "Using runtime camera entityId=${activeCamera.id} name='${activeCamera.name}'" }
    }

    private fun createFallbackCamera() {
        val camera = world.createEntity("Runtime Fallback Camera")
        camera.transform.position.set(0f, 2f, 6f)
        camera.transform.eulerDegrees.set(-10f, 180f, 0f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
                lookAt = Vec3.zero(),
            ),
        )
        camera.add(ActiveCameraComponent())
    }

    companion object {
        private const val TAG = "RuntimeScene"
    }
}
