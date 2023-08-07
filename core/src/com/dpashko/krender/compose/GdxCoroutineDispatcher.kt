package com.dpashko.krender.compose

import com.badlogic.gdx.Gdx
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Dispatcher that posts Compose tasks into GDX app main loop.
 */
class GdxCoroutineDispatcher : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Gdx.app.postRunnable(block)
//        println("$block dispatched...")
    }
}