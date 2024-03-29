package com.dpashko.krender.di

import com.dpashko.krender.AppEntryPoint
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.di.module.AppModule
import com.dpashko.krender.di.module.SceneModule
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton


@Component(modules = [AppModule::class, SceneModule::class])
@Singleton
interface AppComponent {

    fun inject(app: AppEntryPoint)

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun composeManager(composeManager: ComposeManager): Builder

        fun build(): AppComponent
    }
}
