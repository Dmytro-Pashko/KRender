package com.dpashko.krender.di.module;

import com.dpashko.krender.scene.SceneFactory;
import com.dpashko.krender.scene.common.BaseScene;
import com.dpashko.krender.scene.editor.EditorScene;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

import java.util.Map;

import javax.inject.Provider;
import javax.inject.Singleton;

@Module
public class SceneModule {

  @IntoMap
  @Provides
  @ClassKey(EditorScene.class)
  public BaseScene<?, ?> editorScene(EditorScene scene) {
    return scene;
  }

//  @IntoMap
//  @Provides
//  @ClassKey(TerrainGeneratorScene.class)
//  public BaseScene<?, ?> terrainGenerator(TerrainGeneratorScene scene) {
//    return scene;
//  }

  @Singleton
  @Provides
  public SceneFactory sceneFactory(
      Map<Class<?>, Provider<BaseScene<?, ?>>> sceneProviders
  ) {
    return new SceneFactory(sceneProviders);
  }
}
