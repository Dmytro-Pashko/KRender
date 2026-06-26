package com.pashkd.krender.engine.tools

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.importing.AwtFileDialogService
import com.pashkd.krender.engine.tools.animationviewer.AnimationViewerScene
import com.pashkd.krender.engine.tools.assetbrowser.AssetBrowserScene
import com.pashkd.krender.engine.tools.bitmapfonteditor.BitmapFontEditorScene
import com.pashkd.krender.engine.tools.modelviewer.ModelViewerScene
import com.pashkd.krender.engine.tools.sceneeditor.SceneEditorScene
import com.pashkd.krender.engine.tools.skin.SkinEditorScene
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasEditorScene
import com.pashkd.krender.engine.tools.uicomposer.UiComposerScene

object ToolsModule {
    @JvmStatic
    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun createScene(
        sceneName: String,
        modelPath: String?,
        terrainPath: String? = null,
        scenePath: String? = null,
        sceneNameOverride: String? = null,
        uiScenePath: String? = null,
        skinPath: String? = null,
        atlasPath: String? = null,
        fontPath: String? = null,
    ): Scene? =
        when (sceneName.lowercase()) {
            "asset-browser" -> AssetBrowserScene()
            "model-viewer" -> ModelViewerScene(modelPath ?: throw missingProperty("krender.model.path", sceneName))
            "animation-viewer" -> AnimationViewerScene(modelPath ?: throw missingProperty("krender.model.path", sceneName))
            "terrain-editor", "terrain-generator" ->
                com.pashkd.krender.engine.tools.terraineditor.TerrainEditorScene(
                    terrainPath ?: throw missingProperty("krender.terrain.path", sceneName),
                )
            "scene-editor" -> SceneEditorScene(scenePath = scenePath, initialSceneName = sceneNameOverride)
            "skin-editor" -> SkinEditorScene(initialSkinPath = skinPath)
            "texture-atlas-editor" -> TextureAtlasEditorScene(initialAtlasPath = atlasPath, fileDialogService = AwtFileDialogService())
            "ui-composer" -> UiComposerScene(uiScenePath ?: throw missingProperty("krender.ui.scene.path", sceneName))
            "bitmap-font-editor" -> BitmapFontEditorScene(initialFontPath = fontPath)
            else -> null
        }

    private fun missingProperty(
        propertyName: String,
        sceneName: String,
    ): IllegalArgumentException = IllegalArgumentException("Missing required system property '$propertyName' for krender.scene='$sceneName'.")
}
