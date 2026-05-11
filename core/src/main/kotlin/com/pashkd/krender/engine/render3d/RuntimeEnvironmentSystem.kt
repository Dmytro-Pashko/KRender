package com.pashkd.krender.engine.render3d

import com.pashkd.krender.engine.api.ApplyEnvironment
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System

const val DEFAULT_RUNTIME_SKYBOX_TEXTURE = "textures/default_skybox_studio.png"

/**
 * Submits a backend-neutral runtime environment command.
 */
class RuntimeEnvironmentSystem(
    private val skyboxTexturePath: String = DEFAULT_RUNTIME_SKYBOX_TEXTURE,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        world.renderCommands.submit(
            ApplyEnvironment(
                skyboxTexture = MaterialTextureRef(
                    id = skyboxTexturePath,
                    channel = "skybox",
                    uvChannel = 0,
                ),
                showSkybox = true,
                ambientColor = Color(0.55f, 0.58f, 0.64f, 1f),
                ambientIntensity = 0.6f,
                environmentIntensity = 1f,
            ),
        )
    }
}
