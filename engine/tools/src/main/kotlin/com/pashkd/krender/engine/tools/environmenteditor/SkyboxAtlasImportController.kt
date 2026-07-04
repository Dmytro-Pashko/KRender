package com.pashkd.krender.engine.tools.environmenteditor

import java.io.File

class SkyboxAtlasImportController(
    private val state: EnvironmentEditorState,
    private val assetRoot: File,
) {
    private val layoutResolver = SkyboxAtlasLayoutResolver()
    private val importService = SkyboxAtlasImportService(assetRoot)

    fun setSourcePath(path: String) {
        state.skyboxImportState.sourcePath = path
    }

    fun setOutputDirectory(path: String) {
        state.skyboxImportState.outputDirectory = path
    }

    fun setLayoutPreset(preset: SkyboxImportLayoutPreset) {
        state.skyboxImportState.layoutPreset = preset
        refreshDefaultRegions()
    }

    fun selectFace(face: EnvironmentCubemapFace) {
        state.skyboxImportState.selectedFace = face
    }

    fun updateRegion(
        face: EnvironmentCubemapFace,
        x: Int? = null,
        y: Int? = null,
        width: Int? = null,
        height: Int? = null,
    ) {
        val current = state.skyboxImportState.regions[face] ?: return
        state.skyboxImportState.regions =
            state.skyboxImportState.regions + (
                face to
                    current.copy(
                        x = x ?: current.x,
                        y = y ?: current.y,
                        width = width ?: current.width,
                        height = height ?: current.height,
                    )
            )
    }

    fun updateTransform(
        face: EnvironmentCubemapFace,
        rotateDegrees: Int? = null,
        flipX: Boolean? = null,
        flipY: Boolean? = null,
    ) {
        val current = state.skyboxImportState.regions[face] ?: return
        state.skyboxImportState.regions =
            state.skyboxImportState.regions + (
                face to
                    current.copy(
                        transform =
                            current.transform.copy(
                                rotateDegrees = rotateDegrees ?: current.transform.rotateDegrees,
                                flipX = flipX ?: current.transform.flipX,
                                flipY = flipY ?: current.transform.flipY,
                            ),
                    )
            )
    }

    fun refreshDefaultRegions() {
        val sourcePath = state.skyboxImportState.sourcePath
        val size = layoutResolver.readSourceSize(assetRoot, sourcePath) ?: return
        state.skyboxImportState.regions =
            layoutResolver.resolveRegions(
                width = size.first,
                height = size.second,
                preset = state.skyboxImportState.layoutPreset,
                existing = state.skyboxImportState.regions,
            )
        state.statusMessage = "Skybox import regions updated from ${state.skyboxImportState.layoutPreset}."
    }

    fun importIntoEnvironment() {
        val environment = state.environment ?: return
        val sourceFile =
            layoutResolver.resolveSourceFile(assetRoot, state.skyboxImportState.sourcePath)
                ?: run {
                    state.statusMessage = "Skybox import source path is empty."
                    return
                }
        val updated =
            importService.importSkybox(
                environment = environment,
                sourceFile = sourceFile,
                outputDirectory = state.skyboxImportState.outputDirectory,
                regions = state.skyboxImportState.regions.values.sortedBy { it.face.ordinal },
            )
        state.updateEnvironment { updated }
        state.statusMessage = "Imported skybox source and updated manifest faces."
    }
}
