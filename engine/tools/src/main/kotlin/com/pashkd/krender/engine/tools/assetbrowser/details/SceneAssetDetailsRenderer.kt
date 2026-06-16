package com.pashkd.krender.engine.tools.assetbrowser.details

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.tools.assetbrowser.assetBrowserTextLine
import imgui.ImGui
import imgui.dsl

class SceneAssetDetailsRenderer : AssetDetailsRenderer {
    override fun supports(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Scene

    override fun render(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        ImGui.text("Metadata")
        assetBrowserTextLine("Scene Name: ${asset.metadata["sceneName"] ?: asset.name}")
        assetBrowserTextLine("Schema: ${asset.metadata["sceneSchemaVersion"] ?: "unknown"}")

        drawSceneActions(asset, context)

        ImGui.separator()
        ImGui.text("Contents")
        assetBrowserTextLine(
            "Entities: ${asset.metadata["sceneEntityCount"] ?: "0"} " +
                "(${asset.metadata["sceneActiveEntityCount"] ?: "0"} active, " +
                "${asset.metadata["sceneInactiveEntityCount"] ?: "0"} inactive)",
        )
        assetBrowserTextLine("Root entities: ${asset.metadata["sceneRootEntityCount"] ?: "0"}")
        assetBrowserTextLine("Cameras: ${asset.metadata["sceneCameraCount"] ?: "0"}")
        assetBrowserTextLine(
            "Lights: ${asset.metadata["sceneLightCount"] ?: "0"} " +
                "(${asset.metadata["sceneDirectionalLightCount"] ?: "0"} directional, " +
                "${asset.metadata["scenePointLightCount"] ?: "0"} point)",
        )
        assetBrowserTextLine("Models: ${asset.metadata["sceneModelCount"] ?: "0"}")
        assetBrowserTextLine("Terrains: ${asset.metadata["sceneTerrainCount"] ?: "0"}")
        asset.metadata["sceneBounds"]?.let { bounds -> assetBrowserTextLine("Scene bounds: $bounds") }

        ImGui.separator()
        ImGui.text("Environment")
        assetBrowserTextLine("Skybox: ${asset.metadata["sceneSkyboxPath"] ?: "none"}")
        assetBrowserTextLine("Skybox visible: ${asset.metadata["sceneSkyboxVisible"] ?: "true"}")
        assetBrowserTextLine("Environment intensity: ${asset.metadata["sceneEnvironmentIntensity"] ?: "1.00"}")
        assetBrowserTextLine("Ambient intensity: ${asset.metadata["sceneAmbientIntensity"] ?: "0.00"}")

        ImGui.separator()
        ImGui.text("Diagnostics")
        assetBrowserTextLine(
            "Validation: ${asset.metadata["sceneValidationErrorCount"] ?: "0"} errors, " +
                "${asset.metadata["sceneValidationWarningCount"] ?: "0"} warnings",
        )
        assetBrowserTextLine(
            "Dependencies: ${asset.metadata["sceneDependencyCount"] ?: "0"} total, " +
                "${asset.metadata["sceneMissingDependencyCount"] ?: "0"} missing",
        )
        asset.metadata["sceneValidationIssuePreview"]?.let { preview -> assetBrowserTextLine("Issues: $preview") }

        ImGui.separator()
        ImGui.text("Scene bindings")
        assetBrowserTextLine("Active camera: ${asset.metadata["sceneActiveCameraName"] ?: "none"}")
        assetBrowserTextLine("Active terrain: ${asset.metadata["sceneActiveTerrainName"] ?: "none"}")
        asset.metadata["sceneActiveTerrainPath"]?.let { path -> assetBrowserTextLine("Terrain asset: $path") }
        asset.metadata["sceneTerrainSize"]?.let { size -> assetBrowserTextLine("Terrain size: $size") }
        asset.metadata["sceneTerrainLayerCount"]?.let { layers -> assetBrowserTextLine("Terrain layers: $layers") }
        asset.metadata["sceneTerrainBakedResolution"]?.let { resolution ->
            assetBrowserTextLine("Terrain baked resolution: ${resolution}px")
        }
        assetBrowserTextLine(
            "Terrain material library: ${asset.metadata["sceneTerrainMaterialLibraryPath"] ?: "unknown"}",
        )
    }

    private fun drawSceneActions(
        asset: AssetDescriptor,
        context: AssetDetailsRenderContext,
    ) {
        val sceneTools = context.operations.toolsFor(asset)
        if (sceneTools.isEmpty()) return

        ImGui.separator()
        ImGui.text("Scene actions")
        sceneTools.forEachIndexed { index, tool ->
            with(dsl) {
                button("${tool.label}##${context.panelId}_scene_tool_${tool.id}") {
                    context.operations.openWith(asset, tool.id)
                }
            }
            if (index < sceneTools.lastIndex) {
                ImGui.sameLine()
            }
        }
    }
}
