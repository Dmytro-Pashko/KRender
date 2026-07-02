package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui

class EnvironmentAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Environment

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Environment Details")
        when (asset.type) {
            AssetType.Environment -> renderEnvironmentManifest(asset)
            AssetType.HdrSource -> renderHdrSource(asset)
            else -> renderEnvironmentResource(asset)
        }
    }

    private fun renderEnvironmentManifest(asset: AssetDescriptor) {
        assetBrowserTextLine("Environment ID: ${asset.metadata["environmentId"] ?: asset.name}")
        assetBrowserTextLine("Display Name: ${asset.metadata["environmentName"] ?: asset.name}")
        assetBrowserTextLine("Schema Version: ${asset.metadata["environmentSchemaVersion"] ?: "unknown"}")
        assetBrowserTextLine("Environment Type: ${asset.metadata["environmentType"] ?: "unknown"}")
        assetBrowserTextLine("Source Variants: ${asset.metadata["environmentSourceCount"] ?: "0"}")
        assetBrowserTextLine("Background Mode: ${environmentModeLabel(asset.metadata["environmentBackgroundMode"])}")
        assetBrowserTextLine("Skybox Visible: ${asset.metadata["environmentSkyboxVisible"] ?: "unknown"}")
        assetBrowserTextLine("Exposure: ${asset.metadata["environmentExposure"] ?: "1.0"}")
        assetBrowserTextLine("Rotation: ${asset.metadata["environmentRotationDegrees"] ?: "0.0"} deg")
        assetBrowserTextLine("Skybox Intensity: ${asset.metadata["environmentSkyboxIntensity"] ?: "1.0"}")
        assetBrowserTextLine("Diffuse Intensity: ${asset.metadata["environmentDiffuseIntensity"] ?: "1.0"}")
        assetBrowserTextLine("Specular Intensity: ${asset.metadata["environmentSpecularIntensity"] ?: "1.0"}")
        assetBrowserTextLine(
            "Generated Resources: skybox=${availability(asset.metadata["environmentHasSkybox"])}, " +
                "irradiance=${availability(asset.metadata["environmentHasIrradiance"])}, " +
                "radiance=${availability(asset.metadata["environmentHasRadiance"])}, " +
                "brdfLut=${availability(asset.metadata["environmentHasBrdfLut"])}",
        )
        asset.metadata["environmentDescription"]?.let { assetBrowserTextLine("Description: $it") }
        asset.metadata["environmentParseError"]?.let { assetBrowserTextLine("Parse Error: $it") }
    }

    private fun renderHdrSource(asset: AssetDescriptor) {
        assetBrowserTextLine("Source Kind: ${asset.metadata["environmentSourceKind"] ?: asset.extension.uppercase()}")
        assetBrowserTextLine("Intended Use: Source image for Environment creation and IBL generation.")
        assetBrowserTextLine("Index Policy: ${asset.metadata["indexPolicy"] ?: "managed"}")
    }

    private fun renderEnvironmentResource(asset: AssetDescriptor) {
        assetBrowserTextLine("Resource Kind: ${asset.metadata["environmentResourceKind"] ?: asset.type.name}")
        assetBrowserTextLine("Environment Category: Generated or support resource.")
        assetBrowserTextLine("Index Policy: ${asset.metadata["indexPolicy"] ?: "managed"}")
    }

    private fun environmentModeLabel(value: String?): String =
        when (value) {
            "Skybox" -> "Skybox"
            "SolidColor" -> "Solid Color"
            "Transparent" -> "Transparent"
            "None" -> "None"
            null -> "unknown"
            else -> value
        }

    private fun availability(value: String?): String =
        when (value) {
            "true" -> "yes"
            "false" -> "no"
            else -> "unknown"
        }
}
