package com.pashkd.krender.engine.api

import kotlin.reflect.KClass

/**
 * Marker for messages published through [EventBus].
 */
interface Event

class EventBus {
    private val subscribers = mutableMapOf<KClass<out Event>, MutableList<(Event) -> Unit>>()

    fun <T : Event> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription {
        val handlers = subscribers.getOrPut(type) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        val wrapped: (Event) -> Unit = { event -> handler(event as T) }
        handlers += wrapped
        return Subscription {
            handlers -= wrapped
        }
    }

    inline fun <reified T : Event> subscribe(noinline handler: (T) -> Unit): Subscription =
        subscribe(T::class, handler)

    fun publish(event: Event) {
        subscribers[event::class]?.toList()?.forEach { handler -> handler(event) }
    }
}

class Subscription(
    private val unsubscribeAction: () -> Unit,
) {
    fun unsubscribe() = unsubscribeAction()
}
