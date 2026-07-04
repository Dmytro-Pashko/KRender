package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.tools.common.EditorTexturePreviewService
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewZoomMode
import java.io.File

class EnvironmentResourcePreviewController(
    private val state: EnvironmentEditorState,
    private val engine: EngineContext,
) {
    private val texturePreviewService = EditorTexturePreviewService(engine.assets)
    private val resolver =
        EnvironmentResourceFaceResolver(
            assetRoot = engine.assetRegistry.baseDir(),
            texturePreviewService = texturePreviewService,
            queueTexture = { path -> engine.assets.queue(AssetRef.texture(path)) },
        )

    fun buildModel(environment: Environment): EnvironmentResourcePreviewModel {
        val inspectorState = state.resourceInspectorState
        val model =
            resolver.buildPreviewModel(
                environment = environment,
                mode = inspectorState.selectedResourceMode,
                selectedMipLevel = inspectorState.selectedRadianceMip,
                selectedItemId = inspectorState.selectedRegionOrFace,
                hoveredItemId = inspectorState.hoveredRegionOrFace,
                skyboxImportState = state.skyboxImportState,
            )
        syncSelection(model)
        return model
    }

    fun setMode(mode: EnvironmentResourceMode) {
        if (state.resourceInspectorState.selectedResourceMode == mode) return
        state.resourceInspectorState.selectedResourceMode = mode
        state.resourceInspectorState.hoveredRegionOrFace = null
        state.statusMessage = "Resource Inspector mode: ${mode.label()}."
    }

    fun setSelectedFace(face: EnvironmentCubemapFace?) {
        state.resourceInspectorState.selectedFace = face
        state.resourceInspectorState.selectedRegionOrFace = face?.id
    }

    fun setSelectedItem(itemId: String?) {
        state.resourceInspectorState.selectedRegionOrFace = itemId
        state.resourceInspectorState.selectedFace = EnvironmentCubemapFace.fromIdOrAlias(itemId)
    }

    fun setHoveredItem(itemId: String?) {
        state.resourceInspectorState.hoveredRegionOrFace = itemId
    }

    fun setSelectedRadianceMip(level: Int) {
        state.resourceInspectorState.selectedRadianceMip = level.coerceAtLeast(0)
        state.resourceInspectorState.selectedRegionOrFace = state.resourceInspectorState.selectedFace?.id
    }

    fun setZoomMode(mode: TexturePreviewZoomMode) {
        val preview = state.resourceInspectorState.resourcePreviewState
        preview.zoomMode = mode
        when (mode) {
            TexturePreviewZoomMode.Fit -> fitPreview()
            TexturePreviewZoomMode.Percent50 -> setPreviewZoom(0.5f, updateMode = false)
            TexturePreviewZoomMode.Percent100 -> setPreviewZoom(1f, updateMode = false)
            TexturePreviewZoomMode.Percent200 -> setPreviewZoom(2f, updateMode = false)
            TexturePreviewZoomMode.Custom -> Unit
        }
    }

    fun setPreviewZoom(
        value: Float,
        updateMode: Boolean = true,
    ) {
        val preview = state.resourceInspectorState.resourcePreviewState
        preview.customZoom = value.coerceIn(0.05f, 25f)
        preview.viewport.zoom = preview.customZoom
        if (updateMode) {
            preview.zoomMode = TexturePreviewZoomMode.Custom
        }
    }

    fun panPreview(
        deltaX: Float,
        deltaY: Float,
    ) {
        val viewport = state.resourceInspectorState.resourcePreviewState.viewport
        viewport.panX += deltaX
        viewport.panY += deltaY
    }

    fun fitPreview() {
        val preview = state.resourceInspectorState.resourcePreviewState
        preview.viewport.panX = 0f
        preview.viewport.panY = 0f
        preview.zoomMode = TexturePreviewZoomMode.Fit
    }

    fun resetPreviewCamera() {
        val preview = state.resourceInspectorState.resourcePreviewState
        preview.viewport.panX = 0f
        preview.viewport.panY = 0f
        preview.viewport.zoom = 1f
        preview.customZoom = 1f
        preview.zoomMode = TexturePreviewZoomMode.Percent100
    }

    private fun syncSelection(model: EnvironmentResourcePreviewModel) {
        val selected = model.selectedItem
        if (selected != null) {
            state.resourceInspectorState.selectedRegionOrFace = selected.id
            state.resourceInspectorState.selectedFace = EnvironmentCubemapFace.fromIdOrAlias(selected.id)
        } else {
            state.resourceInspectorState.selectedRegionOrFace = null
            state.resourceInspectorState.selectedFace = null
        }
    }
}

internal fun EnvironmentResourceMode.label(): String =
    when (this) {
        EnvironmentResourceMode.Skybox -> "Skybox"
        EnvironmentResourceMode.Irradiance -> "Irradiance"
        EnvironmentResourceMode.Radiance -> "Radiance"
        EnvironmentResourceMode.BrdfLut -> "BRDF LUT"
        EnvironmentResourceMode.ImportedSkyboxSource -> "Imported Skybox Source"
    }
