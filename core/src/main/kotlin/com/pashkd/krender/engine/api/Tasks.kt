package com.pashkd.krender.engine.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Engine task API for background, IO, and render-thread work dispatch.
 *
 * Background jobs must return immutable results or post mutations back to the main queue.
 */
interface TaskService {
    val inFlightJobs: Int

    fun launchBackground(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ): Job

    suspend fun <T> onBackground(block: suspend () -> T): T
    suspend fun <T> onIo(block: suspend () -> T): T
    suspend fun <T> onMain(block: suspend () -> T): T
    fun postToMain(block: () -> Unit)
    fun flushMainThreadQueue()
    fun dispose()
}

class MainThreadTaskQueue {
    private val pending = ArrayDeque<() -> Unit>()

    fun post(block: () -> Unit) {
        pending += block
    }

    fun flush(limit: Int = Int.MAX_VALUE): Int {
        var flushed = 0
        while (pending.isNotEmpty() && flushed < limit) {
            pending.removeFirst().invoke()
            flushed += 1
        }
        return flushed
    }

    fun size(): Int = pending.size
}
