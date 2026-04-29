package com.pashkd.krender.engine.api

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/** Stable numeric identifier assigned to each entity. */
typealias EntityId = Long

/**
 * Marker for data attached to an [Entity].
 *
 * Components should stay lightweight and avoid direct backend dependencies.
 */
interface Component

/**
 * Standard transform component used by most scene entities.
 */
data class TransformComponent(
    /** World-space position. */
    var position: Vec3 = Vec3.zero(),
    /** Quaternion orientation. */
    var rotation: Quat = Quat.identity(),
    /** Euler rotation in degrees. */
    var eulerDegrees: Vec3 = Vec3.zero(),
    /** Non-uniform scale. */
    var scale: Vec3 = Vec3.one(),
) : Component {
    /** Captures an immutable snapshot for rendering. */
    fun snapshot(): TransformSnapshot = TransformSnapshot(position, rotation, eulerDegrees, scale)
}

/** Links an entity to a parent entity by id. */
data class ParentComponent(
    /** Parent entity identifier. */
    val parentId: EntityId,
) : Component

/** Stores a human-readable entity name. */
data class NameComponent(
    /** Display name for the entity. */
    var name: String,
) : Component

/** Stores a world-space linear velocity. */
data class VelocityComponent(
    /** Velocity vector in units per second. */
    var value: Vec3 = Vec3.zero(),
) : Component

/** Stores remaining lifetime before an entity should expire. */
data class LifetimeComponent(
    /** Seconds remaining before expiration. */
    var remainingSeconds: Float,
) : Component

/**
 * Attaches optional per-entity script callbacks to the scene update phases.
 */
class ScriptComponent(
    /** Fixed-step callback invoked during physics-style updates. */
    val fixedUpdate: (SceneWorld.(Entity, Float) -> Unit)? = null,
    /** Variable-step callback invoked during the main update phase. */
    val update: (SceneWorld.(Entity, Float) -> Unit)? = null,
    /** Callback invoked during the late-update phase. */
    val lateUpdate: (SceneWorld.(Entity, Float) -> Unit)? = null,
) : Component

/**
 * Typed component storage for a single entity.
 */
class ComponentContainer {
    @PublishedApi
    internal val components = linkedMapOf<KClass<out Component>, Component>()

    /** Inserts or replaces a component by its runtime type. */
    fun <T : Component> add(component: T): T {
        components[component::class] = component
        return component
    }

    @Suppress("UNCHECKED_CAST")
    /** Returns the component stored under the given type, if any. */
    fun <T : Component> get(type: KClass<T>): T? = components[type] as? T

    /** Returns the component stored under the reified type, if any. */
    inline fun <reified T : Component> get(): T? = get(T::class)

    /** Removes the component stored under the given type. */
    fun remove(type: KClass<out Component>): Component? = components.remove(type)

    /** Returns all stored components as a list snapshot. */
    fun all(): List<Component> = components.values.toList()
}

/**
 * Runtime entity containing a stable id and an arbitrary set of components.
 */
class Entity(
    val id: EntityId,
    val components: ComponentContainer = ComponentContainer(),
) {
    /** Controls whether the entity participates in queries and updates. */
    var active: Boolean = true
    /** Back-reference to the owning scene world, when attached. */
    var scene: SceneWorld? = null

    /** Returns the friendly name or a generated fallback. */
    val name: String
        get() = get<NameComponent>()?.name ?: "Entity $id"

    /** Returns the transform component, creating one if it does not exist yet. */
    val transform: TransformComponent
        get() = get<TransformComponent>() ?: add(TransformComponent())

    /** Adds or replaces a component on this entity. */
    fun <T : Component> add(component: T): T = components.add(component)
    /** Returns the component of the requested type, if present. */
    fun <T : Component> get(type: KClass<T>): T? = components.get(type)
    /** Returns the component of the reified type, if present. */
    inline fun <reified T : Component> get(): T? = components.get<T>()
    /** Removes the component of the requested type, if present. */
    fun remove(type: KClass<out Component>): Component? = components.remove(type)
}

/**
 * Base type for scene systems participating in the ECS lifecycle.
 */
abstract class System {
    /** Invoked when the system is added to a bound world. */
    open fun onAdded(world: SceneWorld) = Unit
    /** Invoked during the fixed-step update phase. */
    open fun fixedUpdate(world: SceneWorld, dt: Float) = Unit
    /** Invoked during the main variable-step update phase. */
    open fun update(world: SceneWorld, dt: Float) = Unit
    /** Invoked during the late-update phase. */
    open fun lateUpdate(world: SceneWorld, dt: Float) = Unit
    /** Invoked when collecting render commands. */
    open fun render(world: SceneWorld, alpha: Float) = Unit
    /** Invoked when collecting debug render commands. */
    open fun debugRender(world: SceneWorld) = Unit
}

/**
 * Ordered list of systems bound to a single [SceneWorld].
 */
class SystemPipeline {
    private val systems = mutableListOf<System>()
    private var world: SceneWorld? = null

    /** Binds the pipeline to a world and notifies existing systems. */
    fun bind(world: SceneWorld) {
        this.world = world
        systems.forEach { it.onAdded(world) }
    }

    /** Adds a system to the ordered pipeline. */
    fun add(system: System): System {
        systems += system
        world?.let(system::onAdded)
        return system
    }

    /** Runs fixed updates for every registered system. */
    fun fixedUpdate(world: SceneWorld, dt: Float) = systems.forEach { it.fixedUpdate(world, dt) }
    /** Runs variable updates for every registered system. */
    fun update(world: SceneWorld, dt: Float) = systems.forEach { it.update(world, dt) }
    /** Runs late updates for every registered system. */
    fun lateUpdate(world: SceneWorld, dt: Float) = systems.forEach { it.lateUpdate(world, dt) }
    /** Runs render collection for every registered system. */
    fun render(world: SceneWorld, alpha: Float) = systems.forEach { it.render(world, alpha) }
    /** Runs debug render collection for every registered system. */
    fun debugRender(world: SceneWorld) = systems.forEach { it.debugRender(world) }
}

/**
 * Deferred mutation command for [SceneWorld].
 *
 * Commands keep entity/component changes out of active system iteration.
 */
sealed interface WorldCommand {
    /** Adds an entity to the world. */
    data class AddEntity(val entity: Entity) : WorldCommand
    /** Removes an entity from the world by id. */
    data class RemoveEntity(val entityId: EntityId) : WorldCommand
    /** Adds or replaces a component on an entity. */
    data class AddComponent<T : Component>(val entityId: EntityId, val component: T) : WorldCommand
    /** Removes a component from an entity by type. */
    data class RemoveComponent(val entityId: EntityId, val type: KClass<out Component>) : WorldCommand
}

/**
 * FIFO buffer of deferred entity and component mutations.
 */
class CommandBuffer internal constructor() {
    private val commands = ArrayDeque<WorldCommand>()

    /** Queues an entity creation command. */
    fun addEntity(entity: Entity) {
        commands += WorldCommand.AddEntity(entity)
    }

    /** Queues an entity removal command. */
    fun removeEntity(entityId: EntityId) {
        commands += WorldCommand.RemoveEntity(entityId)
    }

    /** Queues a component insertion or replacement command. */
    fun <T : Component> addComponent(entityId: EntityId, component: T) {
        commands += WorldCommand.AddComponent(entityId, component)
    }

    /** Queues a component removal command. */
    fun removeComponent(entityId: EntityId, type: KClass<out Component>) {
        commands += WorldCommand.RemoveComponent(entityId, type)
    }

    /** Drains all queued commands into a list snapshot. */
    fun drain(): List<WorldCommand> = buildList {
        while (commands.isNotEmpty()) {
            add(commands.removeFirst())
        }
    }

    /** Returns the current command count. */
    fun size(): Int = commands.size
}

/**
 * ECS container that owns entities, systems, command buffers, and render commands.
 */
class SceneWorld {
    /** Ordered pipeline of systems bound to this world. */
    val systems: SystemPipeline = SystemPipeline()
    /** Deferred mutation buffer for entity and component changes. */
    val commands: CommandBuffer = CommandBuffer()
    /** Render-command collector populated during render phases. */
    val renderCommands: RenderCommandBuffer = RenderCommandBuffer()

    private val entities = linkedMapOf<EntityId, Entity>()
    private var nextEntityId: EntityId = 1L
    private var iterating: Boolean = false

    init {
        systems.bind(this)
    }

    /** Creates a named entity with default name and transform components. */
    fun createEntity(name: String): Entity {
        val entity = Entity(nextEntityId++)
        entity.add(NameComponent(name))
        entity.add(TransformComponent())
        addEntity(entity)
        return entity
    }

    /** Adds an entity immediately or defers it if systems are iterating. */
    fun addEntity(entity: Entity): Entity {
        if (iterating) {
            commands.addEntity(entity)
        } else {
            attach(entity)
        }
        return entity
    }

    /** Removes an entity immediately or defers it if systems are iterating. */
    fun removeEntity(entityId: EntityId) {
        if (iterating) {
            commands.removeEntity(entityId)
        } else {
            entities.remove(entityId)?.scene = null
        }
    }

    /** Returns the entity with the given id, if present. */
    fun getEntity(entityId: EntityId): Entity? = entities[entityId]

    /** Returns all entities as a list snapshot. */
    fun all(): List<Entity> = entities.values.toList()

    /** Removes every entity and pending mutation while keeping systems bound to this world. */
    fun clear() {
        commands.drain()
        entities.values.forEach { entity -> entity.scene = null }
        entities.clear()
        renderCommands.clear()
        nextEntityId = 1L
    }

    @JvmName("query1")
    /** Returns active entities containing component [A]. */
    inline fun <reified A : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null }

    @JvmName("query2")
    /** Returns active entities containing components [A] and [B]. */
    inline fun <reified A : Component, reified B : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null && it.get<B>() != null }

    @JvmName("query3")
    /** Returns active entities containing components [A], [B], and [C]. */
    inline fun <reified A : Component, reified B : Component, reified C : Component> query(): List<Entity> =
        all().filter { it.active && it.get<A>() != null && it.get<B>() != null && it.get<C>() != null }

    /** Runs the fixed-step system phase. */
    fun fixedUpdate(dt: Float) {
        flushCommands()
        withIteration {
            systems.fixedUpdate(this, dt)
        }
    }

    /** Runs the variable update system phase. */
    fun update(dt: Float) {
        withIteration {
            systems.update(this, dt)
        }
    }

    /** Runs late updates and then applies deferred world mutations. */
    fun lateUpdate(dt: Float) {
        withIteration {
            systems.lateUpdate(this, dt)
        }
        flushCommands()
    }

    /** Clears render commands and runs render collection. */
    fun render(alpha: Float) {
        renderCommands.clear()
        withIteration {
            systems.render(this, alpha)
        }
    }

    /** Runs debug render collection without clearing the main render buffer. */
    fun debugRender() {
        withIteration {
            systems.debugRender(this)
        }
    }

    /** Applies all deferred entity and component mutations. */
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

    /** Attaches an entity to this world immediately. */
    private fun attach(entity: Entity) {
        entities[entity.id] = entity
        entity.scene = this
    }

    /** Marks a critical section where world mutations must be deferred. */
    private inline fun withIteration(block: () -> Unit) {
        iterating = true
        try {
            block()
        } finally {
            iterating = false
        }
    }
}
