package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import kotlin.reflect.KClass

enum class SceneFieldKind { Text, Float, Vec3, Color, Choice, ModelAsset }

data class SceneFieldDescriptor(
    val key: String,
    val label: String,
    val kind: SceneFieldKind,
    val choices: List<String> = emptyList(),
)

class SceneComponentDefinition(
    val type: String,
    val componentClass: KClass<out Component>,
    val fields: List<SceneFieldDescriptor>,
    val defaultProperties: Map<String, String>,
    val addable: Boolean = true,
    val requiresTransform: Boolean = false,
    val encode: (Component) -> Map<String, String>,
    val decode: (Map<String, String>) -> Component?,
    val validate: (Map<String, String>) -> String? = { null },
)

/** Backend-neutral definition shared by scene persistence, entity operations and editor field rendering. */
object SceneComponentRegistry {
    private val builtIns: List<SceneComponentDefinition> = listOf(
        SceneComponentDefinition(
            SceneComponentTypes.Name, NameComponent::class,
            listOf(SceneFieldDescriptor("name", "Name", SceneFieldKind.Text)),
            mapOf("name" to "Entity"), addable = false,
            encode = { mapOf("name" to (it as NameComponent).name) },
            decode = { NameComponent(it["name"] ?: "Entity") },
            validate = { if (it["name"].isNullOrBlank()) "Name cannot be blank." else null },
        ),
        SceneComponentDefinition(
            SceneComponentTypes.Transform, TransformComponent::class,
            listOf(
                SceneFieldDescriptor("position", "Position", SceneFieldKind.Vec3),
                SceneFieldDescriptor("rotation", "Rotation", SceneFieldKind.Vec3),
                SceneFieldDescriptor("scale", "Scale", SceneFieldKind.Vec3),
            ),
            mapOf("position" to "0,0,0", "rotation" to "0,0,0", "scale" to "1,1,1"), addable = false,
            encode = { component ->
                val value = component as TransformComponent
                mapOf("position" to value.position.csv(), "rotation" to value.eulerDegrees.csv(), "scale" to value.scale.csv())
            },
            decode = { values ->
                TransformComponent(
                    position = values["position"].orEmpty().toSceneVec3(Vec3.zero()),
                    eulerDegrees = values["rotation"].orEmpty().toSceneVec3(Vec3.zero()),
                    scale = values["scale"].orEmpty().toSceneVec3(Vec3.one()),
                )
            },
            validate = { values ->
                listOf("position", "rotation", "scale").firstOrNull { !values[it].isSceneVec3() }
                    ?.let { "Invalid $it vector." }
            },
        ),
        SceneComponentDefinition(
            SceneComponentTypes.Parent, ParentComponent::class,
            listOf(SceneFieldDescriptor("parentId", "Parent", SceneFieldKind.Text)),
            emptyMap(), addable = false,
            encode = { mapOf("parentId" to (it as ParentComponent).parentId.toString()) },
            decode = { it["parentId"]?.toLongOrNull()?.let(::ParentComponent) },
        ),
        SceneComponentDefinition(
            SceneComponentTypes.Camera, PerspectiveCameraComponent::class,
            listOf(
                SceneFieldDescriptor("fieldOfViewDegrees", "Field of View", SceneFieldKind.Float),
                SceneFieldDescriptor("near", "Near", SceneFieldKind.Float),
                SceneFieldDescriptor("far", "Far", SceneFieldKind.Float),
            ),
            mapOf("fieldOfViewDegrees" to "67", "near" to "0.05", "far" to "250"),
            requiresTransform = true,
            encode = { component ->
                val value = component as PerspectiveCameraComponent
                mapOf("fieldOfViewDegrees" to value.fieldOfViewDegrees.toString(), "near" to value.near.toString(), "far" to value.far.toString())
            },
            decode = { values ->
                PerspectiveCameraComponent(
                    fieldOfViewDegrees = values["fieldOfViewDegrees"]?.toFloatOrNull() ?: 67f,
                    near = values["near"]?.toFloatOrNull() ?: 0.05f,
                    far = values["far"]?.toFloatOrNull() ?: 250f,
                )
            },
            validate = { values ->
                val fov = values["fieldOfViewDegrees"]?.toFloatOrNull()
                val near = values["near"]?.toFloatOrNull()
                val far = values["far"]?.toFloatOrNull()
                if (fov == null || !fov.isFinite() || fov !in 1f..160f || near == null || !near.isFinite() || near <= 0f || far == null || !far.isFinite() || far <= near) "Invalid camera FOV or clipping planes." else null
            },
        ),
        SceneComponentDefinition(
            SceneComponentTypes.Light, LightComponent::class,
            listOf(
                SceneFieldDescriptor("type", "Type", SceneFieldKind.Choice, listOf("Directional", "Point")),
                SceneFieldDescriptor("intensity", "Intensity", SceneFieldKind.Float),
                SceneFieldDescriptor("color", "Color", SceneFieldKind.Color),
                SceneFieldDescriptor("direction", "Direction", SceneFieldKind.Vec3),
            ),
            mapOf("type" to "Directional", "intensity" to "1", "color" to "1,1,1,1", "direction" to "-0.45,-0.8,-0.35"),
            requiresTransform = true,
            encode = { component ->
                val value = component as LightComponent
                mapOf("type" to value.type.name, "intensity" to value.intensity.toString(), "color" to value.color.csv(), "direction" to value.direction.csv())
            },
            decode = { values ->
                LightComponent(
                    type = LightType.entries.firstOrNull { it.name == values["type"] } ?: LightType.Directional,
                    intensity = values["intensity"]?.toFloatOrNull() ?: 1f,
                    color = values["color"].toSceneColor(),
                    direction = values["direction"].orEmpty().toSceneVec3(Vec3(-0.45f, -0.8f, -0.35f)),
                )
            },
            validate = { values ->
                val intensity = values["intensity"]?.toFloatOrNull()
                when {
                    values["type"] !in listOf("Directional", "Point") -> "Unsupported light type."
                    intensity == null || !intensity.isFinite() || intensity < 0f -> "Invalid light intensity."
                    !values["color"].isSceneColor() -> "Invalid light color."
                    !values["direction"].isSceneVec3() -> "Invalid light direction."
                    else -> null
                }
            },
        ),
        SceneComponentDefinition(
            SceneComponentTypes.Model, ModelComponent::class,
            listOf(SceneFieldDescriptor("model", "Model", SceneFieldKind.ModelAsset)),
            mapOf("model" to ""), requiresTransform = true,
            encode = { mapOf("model" to (it as ModelComponent).model.path) },
            decode = { values -> values["model"]?.trim()?.takeIf(String::isNotBlank)?.let { ModelComponent(AssetRef.model(it)) } },
            validate = { if (it["model"].isNullOrBlank()) "Choose a model asset." else null },
        ),
    )

    private val byType = builtIns.associateByTo(linkedMapOf(), SceneComponentDefinition::type)
    private val byClass = builtIns.associateByTo(linkedMapOf(), SceneComponentDefinition::componentClass)

    val definitions: List<SceneComponentDefinition>
        get() = byType.values.toList()

    @Synchronized
    fun register(definition: SceneComponentDefinition) {
        require(definition.type !in byType) { "Scene component type '${definition.type}' is already registered." }
        require(definition.componentClass !in byClass) { "Scene component class '${definition.componentClass}' is already registered." }
        byType[definition.type] = definition
        byClass[definition.componentClass] = definition
    }

    fun find(type: String): SceneComponentDefinition? = byType[type]
    fun find(component: Component): SceneComponentDefinition? = byClass[component::class]
    fun encode(component: Component): ComponentDescriptor? = find(component)?.let { ComponentDescriptor(it.type, it.encode(component)) }
    fun decode(descriptor: ComponentDescriptor): Component? = find(descriptor.type)?.decode?.invoke(descriptor.properties)
    fun copy(component: Component): Component? = find(component)?.let { it.decode(it.encode(component)) }
}

private fun Vec3.csv(): String = "$x,$y,$z"
private fun Color.csv(): String = "$r,$g,$b,$a"
private fun String?.isSceneVec3(): Boolean = this?.split(',')?.let { it.size == 3 && it.all { value -> value.trim().toFloatOrNull()?.isFinite() == true } } == true
private fun String?.isSceneColor(): Boolean = this?.split(',')?.let { it.size in 3..4 && it.all { value -> value.trim().toFloatOrNull()?.isFinite() == true } } == true
private fun String?.toSceneColor(): Color {
    val values = this?.split(',')?.map { it.trim().toFloatOrNull() } ?: emptyList()
    return if (values.size in 3..4 && values.take(3).all { it != null }) Color(values[0]!!, values[1]!!, values[2]!!, values.getOrNull(3) ?: 1f) else Color.white()
}
