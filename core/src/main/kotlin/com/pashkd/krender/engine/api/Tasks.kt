package com.pashkd.krender.engine.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Engine task API for background, IO, and render-thread work dispatch.
 *
 * Background jobs must return immutable results or post mutations back to the main queue.
 */
interface TaskService {
    /** Returns the number of currently active background jobs. */
    val inFlightJobs: Int

    /** Launches a named background coroutine. */
    fun launchBackground(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job

    /** Runs a suspend block on the background dispatcher. */
    suspend fun <T> onBackground(block: suspend () -> T): T
    /** Runs a suspend block on the IO-oriented dispatcher. */
    suspend fun <T> onIo(block: suspend () -> T): T
    /** Runs a suspend block on the main or render-thread dispatcher. */
    suspend fun <T> onMain(block: suspend () -> T): T
    /** Queues a callback for later execution on the main thread. */
    fun postToMain(block: () -> Unit)
    /** Executes queued main-thread callbacks immediately. */
    fun flushMainThreadQueue()
    /** Releases task-system resources and cancels active jobs. */
    fun dispose()
}

/**
 * FIFO queue of callbacks that must be flushed on the main thread.
 */
class MainThreadTaskQueue {
    private val pending = ArrayDeque<() -> Unit>()

    /** Enqueues one callback for later main-thread execution. */
    fun post(block: () -> Unit) {
        pending += block
    }

    /** Executes queued callbacks up to the optional limit and returns the count. */
    fun flush(limit: Int = Int.MAX_VALUE): Int {
        var flushed = 0
        while (pending.isNotEmpty() && flushed < limit) {
            pending.removeFirst().invoke()
            flushed += 1
        }
        return flushed
    }

    /** Returns the number of queued callbacks. */
    fun size(): Int = pending.size
}
