package com.dpashko.krender.di.module;

import com.dpashko.krender.AppController;
import com.dpashko.krender.AppControllerImpl;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class AppModule {

  @Provides
  @Singleton
  public AppController appController(AppControllerImpl impl) {
    return impl;
  }
}
