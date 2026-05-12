package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef

object SceneAssetCollector {
    fun collect(
        descriptor: SceneDescriptor,
        skybox: SkyboxAssetDescriptor? = null,
    ): List<AssetRef<*>> {
        val assets = linkedSetOf<AssetRef<*>>()
        descriptor.entities.forEach { entity ->
            entity.components.forEach { component ->
                when (component.type) {
                    "ModelComponent" -> component.properties["model"]
                        ?.normalizedAssetPath()
                        ?.takeIf(String::isNotBlank)
                        ?.let { path -> assets += AssetRef.model(path) }

                    "TerrainComponent" -> component.properties["terrain"]
                        ?.normalizedAssetPath()
                        ?.takeIf(String::isNotBlank)
                        ?.let { path -> assets += AssetRef.terrain(path) }
                }
            }
        }

        skybox?.modelPath
            ?.normalizedAssetPath()
            ?.takeIf(String::isNotBlank)
            ?.let { path -> assets += AssetRef.model(path) }
        skybox?.texturePath
            ?.normalizedAssetPath()
            ?.takeIf(String::isNotBlank)
            ?.let { path -> assets += AssetRef.texture(path) }

        return assets.toList()
    }

    private fun String.normalizedAssetPath(): String =
        trim().replace('\\', '/')
}
