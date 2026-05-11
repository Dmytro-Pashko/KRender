package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.RuntimeTextureData
import com.pashkd.krender.engine.api.RuntimeTextureFilter
import com.pashkd.krender.engine.api.RuntimeTextureWrap
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import kotlin.math.roundToInt

const val RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID = "runtime:terrain:final_splat"

/**
 * Runtime terrain source factory shared by non-editor terrain scenes.
 *
 * The factory loads serialized [TerrainData] when available and creates a
 * generated fallback with a sensible base material otherwise. It does not
 * create editor state, brush state, panels, or render components.
 */
class TerrainRuntimeFactory(
    private val logger: Logger,
    private val persistence: TerrainPersistence,
    private val materialLibrary: TerrainMaterialLibrary,
) {
    fun loadOrCreate(
        terrainFilePath: String,
        defaultResolution: Int,
        vertexSpacing: Float,
        generator: TerrainGenerator,
    ): TerrainData {
        val candidates = terrainPathCandidates(terrainFilePath)
        logger.info(TAG) {
            "Runtime terrain source candidates requested='$terrainFilePath' " +
                candidates.joinToString(prefix = "[", postfix = "]") { path -> "$path:${persistence.readableSource(path)}" }
        }
        val readablePath = candidates.firstOrNull(persistence::existsReadable)
        if (readablePath != null) {
            try {
                logger.info(TAG) {
                    "Runtime terrain load started path='$readablePath' source=${persistence.readableSource(readablePath)}"
                }
                val descriptor = persistence.loadDescriptor(readablePath)
                return TerrainData.fromDescriptor(descriptor.terrain).also { data ->
                    logger.info(TAG) {
                        "Runtime terrain loaded path='$readablePath' source=${persistence.readableSource(readablePath)} " +
                            "name='${descriptor.name}' (${data.describeRuntimeTerrain()}) ${data.describeLayerWeights()}"
                    }
                }
            } catch (error: Exception) {
                logger.warn(TAG, error) {
                    "Runtime terrain load failed path='$readablePath': ${error.message}. Generating fallback terrain."
                }
            }
        } else {
            logger.warn(TAG) {
                "Runtime terrain file not found requested='$terrainFilePath' candidates=${candidates.joinToString()}. Generating default terrain."
            }
        }

        return createDefault(defaultResolution, vertexSpacing, generator)
    }

    private fun createDefault(
        defaultResolution: Int,
        vertexSpacing: Float,
        generator: TerrainGenerator,
    ): TerrainData {
        val resolution = defaultResolution.coerceAtLeast(2)
        val data = TerrainData(
            width = resolution,
            height = resolution,
            vertexSpacing = vertexSpacing,
        )
        val baseMaterial = preferredBaseMaterial()
        val baseLayer = data.addLayer(
            name = "Base Layer",
            materialId = baseMaterial?.id ?: "terrain/base",
            color = baseMaterial?.fallbackColor ?: TerrainLayerColorDescriptor(0.38f, 0.48f, 0.30f, 1f),
            visible = true,
            tiling = baseMaterial?.defaultTiling ?: 1f,
        )
        for (y in 0 until data.height) {
            for (x in 0 until data.width) {
                data.setLayerWeight(baseLayer.id, x, y, 1f)
            }
        }
        generator.generate(data)
        logger.info(TAG) {
            "Runtime terrain generated generator='${generator.id}' resolution=${data.width} spacing=${"%.2f".format(data.vertexSpacing)} " +
                "baseMaterial='${baseLayer.materialId}' (${data.describeRuntimeTerrain()}) ${data.describeLayerWeights()}"
        }
        return data
    }

    private fun preferredBaseMaterial() =
        materialLibrary.find("terrain/grass")
            ?: materialLibrary.find("terrain/ground_grass")
            ?: materialLibrary.firstOrNull()

    private companion object {
        private const val TAG = "TerrainRuntimeFactory"
    }
}

/**
 * Shared terrain material bake service.
 *
 * Runtime final material baking is deliberately backend-neutral: it blends
 * visible terrain layer weights into one RGBA8888 texture using material
 * fallback colors or layer colors. Editor preview/export code can still use its
 * own Pixmap-based preview path, but runtime scenes depend only on
 * [RuntimeTextureData] and [MaterialTextureRef].
 */
class TerrainMaterialBakeService(
    private val materialLibrary: TerrainMaterialLibrary,
    private val logger: Logger?,
) {
    fun bakeFinalSplatTexture(
        terrain: TerrainData,
        resolution: Int,
        textureId: String,
        revision: Long,
        blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
    ): RuntimeTextureData {
        val outputResolution = resolution.coerceIn(2, MaxRuntimeSplatResolution)
        val pixels = IntArray(outputResolution * outputResolution)
        val distinctSampledColors = linkedSetOf<Int>()
        val denominator = (outputResolution - 1).coerceAtLeast(1).toFloat()
        var offset = 0
        for (y in 0 until outputResolution) {
            val v = y / denominator
            val localZ = terrain.minLocalZ + v * terrain.worldHeight
            for (x in 0 until outputResolution) {
                val u = x / denominator
                val localX = terrain.minLocalX + u * terrain.worldWidth
                val rgba = toRgba8888(blendFinalColor(terrain, localX, localZ, blendMode))
                pixels[offset++] = rgba
                if (distinctSampledColors.size < MaxLoggedDistinctColors) {
                    distinctSampledColors += rgba
                }
            }
        }

        val message = {
            "Baked runtime terrain final splat texture id='$textureId' revision=$revision " +
                "resolution=${outputResolution}x$outputResolution blendMode=$blendMode " +
                "sampledDistinctColors=${distinctSampledColors.size.coerceAtMost(MaxLoggedDistinctColors)} " +
                "layers=${terrain.allLayers().joinToString { layer -> "${layer.id}:${layer.materialId ?: "<none>"}:visible=${layer.visible}:tiling=${"%.2f".format(layer.tiling)}" }}"
        }
        if (distinctSampledColors.size <= 1 && terrain.allLayers().count { it.visible } <= 1) {
            logger?.warn(TAG) {
                message() + ". Texture is expected to look flat because the terrain has one visible material layer and runtime bake currently uses fallback material colors."
            }
        } else {
            logger?.info(TAG, message)
        }
        return RuntimeTextureData(
            id = textureId,
            revision = revision,
            width = outputResolution,
            height = outputResolution,
            rgba8888 = pixels,
            minFilter = RuntimeTextureFilter.Linear,
            magFilter = RuntimeTextureFilter.Linear,
            uWrap = RuntimeTextureWrap.Repeat,
            vWrap = RuntimeTextureWrap.Repeat,
        )
    }

    private fun blendFinalColor(
        terrain: TerrainData,
        localX: Float,
        localZ: Float,
        blendMode: TerrainLayerBlendMode,
    ): TerrainLayerColorDescriptor {
        val samples = terrain.allLayers()
            .filter { it.visible }
            .map { layer ->
                RuntimeTerrainLayerSample(
                    color = materialLibrary.find(layer.materialId)?.fallbackColor ?: layer.color,
                    weight = terrain.sampleLayerWeight(layer.id, localX, localZ),
                    visible = layer.visible,
                )
            }
        return blendSamples(samples, blendMode, BaseFallbackColor)
    }

    private fun blendSamples(
        samples: List<RuntimeTerrainLayerSample>,
        blendMode: TerrainLayerBlendMode,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor =
        when (blendMode) {
            TerrainLayerBlendMode.WeightedAverage -> weightedAverageColor(samples.filter { it.visible }, baseFallbackColor)
            TerrainLayerBlendMode.OrderedAlpha -> orderedAlphaColor(samples.filter { it.visible }, baseFallbackColor)
            TerrainLayerBlendMode.MaxWeight -> maxWeightColor(samples.filter { it.visible }, baseFallbackColor)
        }

    private fun weightedAverageColor(
        samples: List<RuntimeTerrainLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var totalWeight = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        var a = 0f
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight <= 0f) return@forEach
            r += sample.color.r * weight
            g += sample.color.g * weight
            b += sample.color.b * weight
            a += sample.color.a * weight
            totalWeight += weight
        }
        if (totalWeight <= 0f) return baseFallbackColor
        return TerrainLayerColorDescriptor(r / totalWeight, g / totalWeight, b / totalWeight, a / totalWeight)
    }

    private fun orderedAlphaColor(
        samples: List<RuntimeTerrainLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var result = baseFallbackColor
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight <= 0f) return@forEach
            result = TerrainLayerColorDescriptor(
                r = result.r + (sample.color.r - result.r) * weight,
                g = result.g + (sample.color.g - result.g) * weight,
                b = result.b + (sample.color.b - result.b) * weight,
                a = result.a + (sample.color.a - result.a) * weight,
            )
        }
        return result
    }

    private fun maxWeightColor(
        samples: List<RuntimeTerrainLayerSample>,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor {
        var bestSample: RuntimeTerrainLayerSample? = null
        var bestWeight = 0f
        samples.forEach { sample ->
            val weight = sample.weight.coerceIn(0f, 1f)
            if (weight > bestWeight) {
                bestWeight = weight
                bestSample = sample
            }
        }
        return bestSample?.color ?: baseFallbackColor
    }

    private fun toRgba8888(color: TerrainLayerColorDescriptor): Int {
        val r = (color.r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val a = (color.a.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (r shl 24) or (g shl 16) or (b shl 8) or a
    }

    private companion object {
        private const val TAG = "TerrainMaterialBakeService"
        private const val MaxRuntimeSplatResolution = 8192
        private const val MaxLoggedDistinctColors = 32
        private val BaseFallbackColor = TerrainLayerColorDescriptor(0.38f, 0.48f, 0.30f, 1f)
    }
}

private data class RuntimeTerrainLayerSample(
    val color: TerrainLayerColorDescriptor,
    val weight: Float,
    val visible: Boolean,
)

/**
 * Runtime-only terrain mesh and material synchronization.
 *
 * This system consumes [TerrainDataComponent] and [TerrainRendererComponent]
 * without touching [TerrainEditorState]. Dirty terrain data is converted through
 * [TerrainMeshBuilder], then [TerrainMaterialBakeService] creates the final
 * baked texture. The renderer material stores a [MaterialTextureRef] whose id
 * matches the generated [RuntimeTextureData], and [TerrainRenderSystem] forwards
 * both to the backend in [DrawDynamicModel].
 */
class RuntimeTerrainMeshSystem(
    private val materialBakeService: TerrainMaterialBakeService,
    private val logger: Logger,
    private val finalSplatTextureId: String = RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID,
    private val finalSplatResolution: Int = 512,
    private val blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
) : System() {
    constructor(
        materialLibrary: TerrainMaterialLibrary,
        logger: Logger,
        finalSplatTextureId: String = RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID,
        finalSplatResolution: Int = 512,
        blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
    ) : this(
        materialBakeService = TerrainMaterialBakeService(materialLibrary, logger),
        logger = logger,
        finalSplatTextureId = finalSplatTextureId,
        finalSplatResolution = finalSplatResolution,
        blendMode = blendMode,
    )

    override fun update(world: SceneWorld, dt: Float) {
        world.query<TransformComponent, TerrainDataComponent, TerrainRendererComponent>().forEach { entity ->
            val terrain = entity.get<TerrainDataComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            if (!terrain.dirty && renderer.model != null) {
                return@forEach
            }

            renderer.meshRevision += 1L
            val mesh = TerrainMeshBuilder.build(
                terrain = terrain.data,
                modelId = renderer.modelId,
                revision = renderer.meshRevision,
            )
            renderer.model = mesh.model
            renderer.vertexCount = mesh.vertexCount
            renderer.triangleCount = mesh.triangleCount
            val requestedFinalSplatResolution = (renderer.previewResolution.takeIf { it > 0 } ?: finalSplatResolution)
                .coerceIn(2, MaxRuntimeTerrainSplatResolution)
            renderer.previewResolution = requestedFinalSplatResolution

            val textureId = finalSplatTextureId
            try {
                val texture = materialBakeService.bakeFinalSplatTexture(
                    terrain = terrain.data,
                    resolution = requestedFinalSplatResolution,
                    textureId = textureId,
                    revision = renderer.meshRevision,
                    blendMode = blendMode,
                )
                renderer.replaceFinalSplatTexture(texture)
                renderer.materialRevision += 1L
                renderer.material = renderer.material.copy(
                    baseColor = Color.white(),
                    diffuseTextureRef = MaterialTextureRef(
                        id = texture.id,
                        channel = "baseColor",
                        uvChannel = 0,
                    ),
                )
            } catch (error: Exception) {
                renderer.replaceFinalSplatTexture(null)
                renderer.material = renderer.material.copy(diffuseTextureRef = null)
                logger.warn(TAG, error) {
                    "Runtime terrain final splat bake failed modelId='${renderer.modelId}': ${error.message}"
                }
            }

            terrain.clearDirty()
            logger.info(TAG) {
                "Runtime terrain mesh synced modelId='${renderer.modelId}' revision=${renderer.meshRevision} " +
                    "vertices=${renderer.vertexCount} triangles=${renderer.triangleCount} " +
                    "blendMode=$blendMode materialRevision=${renderer.materialRevision} " +
                    "finalSplatResolution=${renderer.previewResolution}x${renderer.previewResolution} " +
                    "finalSplat=${renderer.finalSplatTexture?.id ?: "<none>"} materialTexture=${renderer.material.diffuseTextureRef?.id ?: "<none>"}"
            }
        }
    }

    private companion object {
        private const val TAG = "RuntimeTerrainMeshSystem"
        private const val MaxRuntimeTerrainSplatResolution = 8192
    }
}

private fun TerrainData.describeRuntimeTerrain(): String =
    "size=${width}x${height} spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} " +
        "[${allLayers().joinToString { layer -> "${layer.id}:${layer.name}:${layer.materialId ?: "<none>"}" }}]"

private fun terrainPathCandidates(path: String): List<String> {
    val normalized = path.trim().replace('\\', '/')
    if (normalized.isBlank()) return listOf("terrains/terrain_01.json")
    val candidates = linkedSetOf(normalized)
    if (normalized.endsWith(".krterrain", ignoreCase = true)) {
        candidates += normalized.dropLast(".krterrain".length) + ".json"
    }
    return candidates.toList()
}

private fun TerrainData.describeLayerWeights(): String =
    "weights=[" + allLayers().joinToString { layer ->
        val weights = getLayerWeightMap(layer.id)
        if (weights == null || weights.isEmpty()) {
            "${layer.id}:empty"
        } else {
            val min = weights.minOrNull() ?: 0f
            val max = weights.maxOrNull() ?: 0f
            val avg = weights.average().toFloat()
            "${layer.id}:min=${"%.3f".format(min)} max=${"%.3f".format(max)} avg=${"%.3f".format(avg)}"
        }
    } + "]"
