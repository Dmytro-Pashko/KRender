package com.pashkd.krender.engine.api

import kotlin.reflect.KClass

/**
 * Marker for messages published through [EventBus].
 */
interface Event

/**
 * Lightweight typed event dispatcher scoped to the engine runtime.
 */
class EventBus {
    private val subscribers = mutableMapOf<KClass<out Event>, MutableList<(Event) -> Unit>>()

    /** Registers a handler for the given event type. */
    fun <T : Event> subscribe(
        type: KClass<T>,
        handler: (T) -> Unit,
    ): Subscription {
        val handlers = subscribers.getOrPut(type) { mutableListOf() }

        @Suppress("UNCHECKED_CAST")
        val wrapped: (Event) -> Unit = { event -> handler(event as T) }
        handlers += wrapped
        return Subscription {
            handlers -= wrapped
        }
    }

    /** Registers a handler using a reified event type. */
    inline fun <reified T : Event> subscribe(noinline handler: (T) -> Unit): Subscription = subscribe(T::class, handler)

    /** Publishes one event to subscribers of its exact runtime type. */
    fun publish(event: Event) {
        subscribers[event::class]?.toList()?.forEach { handler -> handler(event) }
    }
}

/**
 * Disposable handle returned when an event subscription is registered.
 */
class Subscription(
    private val unsubscribeAction: () -> Unit,
) {
    /** Removes the associated handler from the event bus. */
    fun unsubscribe() = unsubscribeAction()
}
