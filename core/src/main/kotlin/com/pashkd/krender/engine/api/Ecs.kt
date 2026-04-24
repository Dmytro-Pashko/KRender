package com.pashkd.krender.engine.api

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

typealias EntityId = Long

/**
 * Marker for data attached to an [Entity].
 *
 * Components should stay lightweight and avoid direct backend dependencies.
 */
interface Component

data class TransformComponent(
    var position: Vec3 = Vec3.zero(),
    var rotation: Quat = Quat.identity(),
    var eulerDegrees: Vec3 = Vec3.zero(),
    var scale: Vec3 = Vec3.one(),
) : Component {
    fun snapshot(): TransformSnapshot = TransformSnapshot(position, rotation, eulerDegrees, scale)
}

data class ParentComponent(
    val parentId: EntityId,
) : Component

data class NameComponent(
    var name: String,
) : Component

data class VelocityComponent(
    var value: Vec3 = Vec3.zero(),
) : Component

data class LifetimeComponent(
    var remainingSeconds: Float,
) : Component

class ScriptComponent(
    val fixedUpdate: (SceneWorld.(Entity, Float) -> Unit)? = null,
    val update: (SceneWorld.(Entity, Float) -> Unit)? = null,
    val lateUpdate: (SceneWorld.(Entity, Float) -> Unit)? = null,
) : Component

class ComponentContainer {
    @PublishedApi
    internal val components = linkedMapOf<KClass<out Component>, Component>()

    fun <T : Component> add(component: T): T {
        components[component::class] = component
        return component
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> get(type: KClass<T>): T? = components[type] as? T

    inline fun <reified T : Component> get(): T? = get(T::class)

    fun remove(type: KClass<out Component>): Component? = components.remove(type)

    fun all(): List<Component> = components.values.toList()
}

class Entity(
    val id: EntityId,
    val components: ComponentContainer = ComponentContainer(),
) {
    var active: Boolean = true
    var scene: SceneWorld? = null

    val name: String
        get() = get<NameComponent>()?.name ?: "Entity $id"

    val transform: TransformComponent
        get() = get<TransformComponent>() ?: add(TransformComponent())

    fun <T : Component> add(component: T): T = components.add(component)
    fun <T : Component> get(type: KClass<T>): T? = components.get(type)
    inline fun <reified T : Component> get(): T? = components.get<T>()
    fun remove(type: KClass<out Component>): Component? = components.remove(type)
}

abstract class System {
    open fun onAdded(world: SceneWorld) = Unit
    open fun fixedUpdate(world: SceneWorld, dt: Float) = Unit
    open fun update(world: SceneWorld, dt: Float) = Unit
    open fun lateUpdate(world: SceneWorld, dt: Float) = Unit
    open fun render(world: SceneWorld, alpha: Float) = Unit
    open fun debugRender(world: SceneWorld) = Unit
}

class SystemPipeline {
    private val systems = mutableListOf<System>()
    private var world: SceneWorld? = null

    fun bind(world: SceneWorld) {
        this.world = world
        systems.forEach { it.onAdded(world) }
    }

    fun add(system: System): System {
        systems += system
        world?.let(system::onAdded)
        return system
    }

    fun fixedUpdate(world: SceneWorld, dt: Float) = systems.forEach { it.fixedUpdate(world, dt) }
    fun update(world: SceneWorld, dt: Float) = systems.forEach { it.update(world, dt) }
    fun lateUpdate(world: SceneWorld, dt: Float) = systems.forEach { it.lateUpdate(world, dt) }
    fun render(world: SceneWorld, alpha: Float) = systems.forEach { it.render(world, alpha) }
    fun debugRender(world: SceneWorld) = systems.forEach { it.debugRender(world) }
}

/**
 * Deferred mutation command for [SceneWorld].
 *
 * Commands keep entity/component changes out of active system iteration.
 */
sealed interface WorldCommand {
    data class AddEntity(val entity: Entity) : WorldCommand
    data class RemoveEntity(val entityId: EntityId) : WorldCommand
    data class AddComponent<T : Component>(val entityId: EntityId, val component: T) : WorldCommand
    data class RemoveComponent(val entityId: EntityId, val type: KClass<out Component>) : WorldCommand
}

class CommandBuffer internal constructor() {
    private val commands = ArrayDeque<WorldCommand>()

    fun addEntity(entity: Entity) {
        commands += WorldCommand.AddEntity(entity)
    }

    fun removeEntity(entityId: EntityId) {
        commands += WorldCommand.RemoveEntity(entityId)
    }

    fun <T : Component> addComponent(entityId: EntityId, component: T) {
        commands += WorldCommand.AddComponent(entityId, component)
    }

    fun removeComponent(entityId: EntityId, type: KClass<out Component>) {
        commands += WorldCommand.RemoveComponent(entityId, type)
    }

    fun drain(): List<WorldCommand> = buildList {
        while (commands.isNotEmpty()) {
            add(commands.removeFirst())
        }
    }

    fun size(): Int = commands.size
}

class SceneWorld {
    val systems: SystemPipeline = SystemPipeline()
    val commands: CommandBuffer = CommandBuffer()
    val renderCommands: RenderCommandBuffer = RenderCommandBuffer()

    private val entities = linkedMapOf<EntityId, Entity>()
    private var nextEntityId: EntityId = 1L
    private var iterating: Boolean = false

    init {
        systems.bind(this)
    }

    fun createEntity(name: String): Entity {
        val entity = Entity(nextEntityId++)
        entity.add(NameComponent(name))
        entity.add(TransformComponent())
        addEntity(entity)
        return entity
    }

    fun addEntity(entity: Entity): Entity {
        if (iterating) {
            commands.addEntity(entity)
        } else {
            attach(entity)
        }
        return entity
    }

    fun removeEntity(entityId: EntityId) {
        if (iterating) {
            commands.removeEntity(entityId)
        } else {
            entities.remove(entityId)?.scene = null
        }
    }

    fun getEntity(entityId: EntityId): Entity? = entities[entityId]

    fun all(): List<Entity> = entities.values.toList()

    @JvmName("query1")
    inline fun <reified A : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null }

    @JvmName("query2")
    inline fun <reified A : Component, reified B : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null && it.get<B>() != null }

    @JvmName("query3")
    inline fun <reified A : Component, reified B : Component, reified C : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null && it.get<B>() != null && it.get<C>() != null }

    fun fixedUpdate(dt: Float) {
        flushCommands()
        withIteration {
            systems.fixedUpdate(this, dt)
        }
    }

    fun update(dt: Float) {
        withIteration {
            systems.update(this, dt)
        }
    }

    fun lateUpdate(dt: Float) {
        withIteration {
            systems.lateUpdate(this, dt)
        }
        flushCommands()
    }

    fun render(alpha: Float) {
        renderCommands.clear()
        withIteration {
            systems.render(this, alpha)
        }
    }

    fun debugRender() {
        withIteration {
            systems.debugRender(this)
        }
    }

    fun flushCommands() {
        commands.drain().forEach { command ->
            when (command) {
                is WorldCommand.AddEntity -> attach(command.entity)
                is WorldCommand.RemoveEntity -> entities.remove(command.entityId)?.scene = null
                is WorldCommand.AddComponent<*> -> entities[command.entityId]?.add(command.component)
                is WorldCommand.RemoveComponent -> entities[command.entityId]?.remove(command.type)
            }
        }
    }

    private fun attach(entity: Entity) {
        entities[entity.id] = entity
        entity.scene = this
    }

    private inline fun withIteration(block: () -> Unit) {
        iterating = true
        try {
            block()
        } finally {
            iterating = false
        }
    }
}
