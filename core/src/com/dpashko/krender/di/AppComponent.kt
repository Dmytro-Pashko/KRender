package com.dpashko.krender.di

import com.dpashko.krender.KRenderApp
import com.dpashko.krender.di.module.AppModule
import com.dpashko.krender.di.module.SceneModule
import dagger.Component
import javax.inject.Singleton

@Component(modules = [AppModule::class, SceneModule::class])
@Singleton
interface AppComponent {

    fun inject(app: KRenderApp)

    @Component.Builder
    interface Builder {

        fun build(): AppComponent
    }
}
