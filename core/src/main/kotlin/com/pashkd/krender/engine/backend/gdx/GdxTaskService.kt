package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.MainThreadTaskQueue
import com.pashkd.krender.engine.api.TaskService
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine-backed task service with explicit background, IO, and render-thread dispatch.
 */
class GdxTaskService : TaskService {
    private val job = SupervisorJob()
    private val backgroundScope = CoroutineScope(job + Dispatchers.Default)
    private val mainQueue = MainThreadTaskQueue()
    private val mainDispatcher = RenderThreadDispatcher()
    private val jobs = mutableSetOf<Job>()

    override val inFlightJobs: Int
        get() = jobs.count { it.isActive }

    /** Launches a tracked background coroutine. */
    override fun launchBackground(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val launched = backgroundScope.launch(block = block)
        jobs += launched
        launched.invokeOnCompletion { jobs -= launched }
        return launched
    }

    /** Runs the block on the default background dispatcher. */
    override suspend fun <T> onBackground(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    /** Runs the block on the IO dispatcher. */
    override suspend fun <T> onIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Runs the block on the render thread dispatcher. */
    override suspend fun <T> onMain(block: suspend () -> T): T = withContext(mainDispatcher) { block() }

    /** Queues a task for the main-thread task queue. */
    override fun postToMain(block: () -> Unit) {
        mainQueue.post(block)
    }

    /** Executes all queued main-thread tasks immediately. */
    override fun flushMainThreadQueue() {
        mainQueue.flush()
    }

    /** Cancels all background work owned by the service. */
    override fun dispose() {
        backgroundScope.cancel()
        job.cancel()
    }
}

/** Coroutine dispatcher that posts work onto the LibGDX application thread. */
class RenderThreadDispatcher : CoroutineDispatcher() {
    /** Schedules the runnable through LibGDX's `postRunnable` callback queue. */
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        Gdx.app.postRunnable(block)
    }
}
