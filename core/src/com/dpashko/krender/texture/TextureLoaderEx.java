package com.dpashko.krender.texture;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.Array;

/**
 * Loader of Textures that fixes issue with incorrect file resolving in original one.
 */
public class TextureLoaderEx extends TextureLoader {

    static public class TextureLoaderInfo {
        String filename;
        TextureData data;
        Texture texture;
    }

    protected TextureLoaderInfo info = new TextureLoaderInfo();

    public TextureLoaderEx(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public void loadAsync(
            AssetManager manager,
            String fileName,
            FileHandle file,
            TextureParameter parameter
    ) {
        info.filename = fileName;
        if (parameter == null || parameter.textureData == null) {
            Pixmap.Format format = null;
            boolean genMipMaps = false;
            info.texture = null;

            if (parameter != null) {
                format = parameter.format;
                genMipMaps = parameter.genMipMaps;
                info.texture = parameter.texture;
            }
            // Default Pixmap loaded doesn't use resolver.
            info.data = TextureData.Factory.loadFromFile(resolve(fileName), format, genMipMaps);
        } else {
            info.data = parameter.textureData;
            info.texture = parameter.texture;
        }
        if (!info.data.isPrepared()) info.data.prepare();
    }

    @Override
    public Texture loadSync(
            AssetManager manager,
            String fileName,
            FileHandle file,
            TextureParameter parameter
    ) {
        if (info == null) return null;
        Texture texture = info.texture;
        if (texture != null) {
            texture.load(info.data);
        } else {
            texture = new Texture(info.data);
        }
        if (parameter != null) {
            texture.setFilter(parameter.minFilter, parameter.magFilter);
            texture.setWrap(parameter.wrapU, parameter.wrapV);
        }
        return texture;
    }

    @Override
    public Array<AssetDescriptor> getDependencies(
            String fileName,
            FileHandle file,
            TextureParameter parameter
    ) {
        return null;
    }
}
