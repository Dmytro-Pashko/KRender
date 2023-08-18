package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.graphics.Texture
import com.dpashko.krender.assets.Models
import com.dpashko.krender.texture.TextureLoaderEx
import net.mgsx.gltf.loaders.glb.GLBAssetLoader
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader
import net.mgsx.gltf.scene3d.scene.SceneAsset
import javax.inject.Inject

class EditorSceneAssetsManager @Inject constructor() : AssetManager(ASSETS_RESOLVER, true) {

    companion object {
        private val ASSETS_RESOLVER = FileHandleResolver { assetName ->
            // Normalize path to asset, some application keeps some related path double dots are used for moving up in
            // the hierarchy.
            val assetFile = Gdx.files.internal(assetName)
            println("${Thread.currentThread()} : Loading of ${assetFile.file().absoluteFile}")
            assetFile
        }
    }

    init {
        setLoader(SceneAsset::class.java, ".gltf", GLTFAssetLoader(ASSETS_RESOLVER))
        setLoader(SceneAsset::class.java, ".glb", GLBAssetLoader(ASSETS_RESOLVER))
        setLoader(Texture::class.java, TextureLoaderEx(ASSETS_RESOLVER))
    }

    /**
     * Starts async loading of scene assets.
     */
    fun loadAssets() {
        println("${Thread.currentThread()}: Started loading of Editor scene assets.")
        // Models.
        load(Models.ACTOR_MODEL, SceneAsset::class.java)
    }

    fun getActorModel(): SceneAsset = get(Models.ACTOR_MODEL)
}