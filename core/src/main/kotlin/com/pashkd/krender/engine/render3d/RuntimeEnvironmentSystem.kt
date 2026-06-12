package com.pashkd.krender.engine.render3d

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor

data class RuntimeEnvironment(
    val skyboxTexturePath: String?,
    val showSkybox: Boolean,
    val ambientColor: Color,
    val ambientIntensity: Float,
    val environmentIntensity: Float,
)

object RuntimeEnvironmentFactory {
    fun fromSceneSettings(
        settings: SceneSettingsDescriptor,
        skybox: SkyboxAssetDescriptor? = null,
    ): RuntimeEnvironment =
        RuntimeEnvironment(
            skyboxTexturePath = skybox?.texturePath
                ?.trim()
                ?.replace('\\', '/')
                ?.takeIf(String::isNotBlank),
            showSkybox = settings.environment.showSkybox &&
                !skybox?.texturePath.isNullOrBlank(),
            ambientColor = settings.lighting.ambientColor.copy(),
            ambientIntensity = settings.lighting.ambientIntensity,
            environmentIntensity = settings.environment.environmentIntensity * (skybox?.intensity ?: 1f),
        )
}

/**
 * Submits a backend-neutral runtime environment command.
 */
class RuntimeEnvironmentSystem(
    private val environment: RuntimeEnvironment,
) : System() {
    override fun render(world: SceneWorld, alpha: Float) {
        world.renderCommands.submit(
            ApplyEnvironment(
                skyboxTexture = environment.skyboxTexturePath?.let { texturePath ->
                    MaterialTextureRef(
                        id = texturePath,
                        channel = "skybox",
                        uvChannel = 0,
                    )
                },
                showSkybox = environment.showSkybox,
                ambientColor = environment.ambientColor.copy(),
                ambientIntensity = environment.ambientIntensity,
                environmentIntensity = environment.environmentIntensity,
            ),
        )
    }
}
