package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import java.io.File

class SkyboxAtlasImportController(
    private val state: EnvironmentEditorState,
    private val assetRoot: File,
    private val logger: Logger? = null,
    private val onEnvironmentUpdated: ((com.pashkd.krender.engine.assets.environment.Environment) -> Unit)? = null,
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
        logger?.info(TAG) {
            "Skybox import regions refreshed source='${state.skyboxImportState.sourcePath}' " +
                "layout=${state.skyboxImportState.layoutPreset} size=${size.first}x${size.second} " +
                "regions=${state.skyboxImportState.regions.size}"
        }
    }

    fun importIntoEnvironment() {
        val environment = state.environment ?: return
        val previousRevision = state.environmentCacheRevision
        val previousSkyboxFaces = environment.skybox?.faces?.size ?: 0
        val sourceFile =
            layoutResolver.resolveSourceFile(assetRoot, state.skyboxImportState.sourcePath)
                ?: run {
                    state.statusMessage = "Skybox import source path is empty."
                    logger?.warn(TAG) { "Skybox import aborted because source path is empty." }
                    return
                }
        logger?.info(TAG) {
            "Skybox import starting manifest='${environment.manifestPath}' source='${sourceFile.path.replace('\\', '/')}' " +
                "outputDirectory='${state.skyboxImportState.outputDirectory}' layout=${state.skyboxImportState.layoutPreset} " +
                "regions=${state.skyboxImportState.regions.size} previousSkyboxFaces=$previousSkyboxFaces cacheRevision=$previousRevision"
        }
        val updated =
            importService.importSkybox(
                environment = environment,
                sourceFile = sourceFile,
                outputDirectory = state.skyboxImportState.outputDirectory,
                regions = state.skyboxImportState.regions.values.sortedBy { it.face.ordinal },
            )
        state.updateEnvironment { updated }
        state.invalidateEnvironmentCache()
        onEnvironmentUpdated?.invoke(updated)
        state.statusMessage = "Imported skybox source and updated manifest faces."
        logger?.info(TAG) {
            "Skybox import completed manifest='${updated.manifestPath}' " +
                "skyboxFaces=${updated.skybox?.faces?.size ?: 0} cacheRevision ${previousRevision} -> ${state.environmentCacheRevision} " +
                "facePaths=${updated.skybox?.faces.orEmpty().entries.joinToString { (face, path) -> "$face=$path" }}"
        }
    }

    companion object {
        private const val TAG = "SkyboxImportCtrl"
    }
}
