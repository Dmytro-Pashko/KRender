package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import kotlin.math.floor
import kotlin.math.roundToInt

const val RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID = "runtime:terrain:final_splat"

/**
 * Minimal terrain persistence surface used by [TerrainRuntimeFactory].
 */
interface TerrainRuntimePersistence {
    fun existsReadable(filePath: String): Boolean

    fun readableSource(filePath: String): String

    fun loadDescriptor(filePath: String): TerrainFileDescriptor
}

/**
 * Builds a stable, backend-runtime texture id for one terrain entity.
 *
 * Runtime texture ids must be unique per terrain because [MaterialTextureRef]
 * only stores an id and the backend uploads [RuntimeTextureData] into an id-keyed
 * cache before drawing.
 */
fun runtimeTerrainFinalSplatTextureId(
    entityId: EntityId,
    modelId: String,
): String =
    RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID +
        ":" + entityId +
        ":" + modelId.replace(Regex("[^A-Za-z0-9_\\-]+"), "_")

/**
 * Platform-neutral terrain material texture sampler used by runtime terrain baking.
 *
 * Implementations may sample backend texture assets, procedural sources, or test
 * doubles. Returning `null` signals that the caller should fall back to the
 * material fallback color.
 */
interface TerrainMaterialTextureSampler : AutoCloseable {
    fun sample(
        layer: TerrainLayer,
        material: TerrainMaterialDescriptor,
        u: Float,
        v: Float,
    ): TerrainLayerColorDescriptor?

    override fun close() = Unit
}

fun interface TerrainMaterialTextureSamplerFactory {
    fun create(): TerrainMaterialTextureSampler?
}

/**
 * Runtime terrain source factory shared by non-editor terrain scenes.
 *
 * The factory loads serialized [TerrainData] from a readable terrain asset and
 * fails fast when the referenced file is missing or invalid. It does not create
 * editor state, brush state, panels, or render components. Persistence is
 * accessed through [TerrainRuntimePersistence] so runtime loading can be tested
 * without a backend file service.
 */
class TerrainRuntimeFactory(
    private val logger: Logger,
    private val persistence: TerrainRuntimePersistence,
) {
    fun load(terrainFilePath: String): TerrainData {
        val candidates = terrainPathCandidates(terrainFilePath)
        logger.info(TAG) {
            "Runtime terrain source candidates requested='$terrainFilePath' " +
                candidates.joinToString(
                    prefix = "[",
                    postfix = "]",
                ) { path -> "$path:${persistence.readableSource(path)}" }
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
                throw IllegalStateException(
                    "Runtime terrain asset '$readablePath' could not be loaded: ${error.message}",
                    error,
                )
            }
        }

        throw IllegalArgumentException(
            "Runtime terrain asset not found: requested='$terrainFilePath' candidates=${candidates.joinToString()}",
        )
    }

    private companion object {
        private const val TAG = "TerrainRuntimeFactory"
    }
}

/**
 * Shared terrain material bake service.
 *
 * Runtime final material baking is deliberately backend-neutral: it blends
 * visible terrain layer weights into one RGBA8888 texture. When LibGDX file
 * access is available, the bake samples terrain material albedo textures
 * directly; otherwise it gracefully falls back to the material fallback color
 * or the layer color. Runtime scenes still depend only on [RuntimeTextureData]
 * and [MaterialTextureRef].
 */
class TerrainMaterialBakeService(
    private val materialLibrary: TerrainMaterialLibrary,
    private val logger: Logger?,
    private val textureSamplerFactory: TerrainMaterialTextureSamplerFactory? = null,
) {
    /**
     * Bakes the runtime terrain's final diffuse texture into CPU-side RGBA8888 data.
     *
     * The bake walks a regular output grid, maps each pixel into normalized UV space,
     * converts that UV back into terrain-local coordinates, and asks [blendFinalColor]
     * to resolve the visible layer stack at that location. The resulting color is
     * packed into an integer array that becomes [RuntimeTextureData].
     *
     * Resolution is clamped to a safe runtime range, and any backend-provided
     * texture sampler is created once per bake and disposed before returning.
     */
    fun bakeFinalSplatTexture(
        terrain: TerrainData,
        resolution: Int,
        textureId: String,
        revision: Long,
        blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.OrderedAlpha,
    ): RuntimeTextureData {
        // Clamp the requested resolution so runtime baking stays within the
        // supported texture size range even if callers pass invalid values.
        val outputResolution = resolution.coerceIn(2, MaxRuntimeSplatResolution)
        val pixels = IntArray(outputResolution * outputResolution)
        val distinctSampledColors = linkedSetOf<Int>()
        // Use resolution - 1 so the outermost pixels land on the terrain bounds
        // instead of stopping one texel short of the far edge.
        val denominator = (outputResolution - 1).coerceAtLeast(1).toFloat()
        val textureSampler = createTextureSampler()
        try {
            var offset = 0
            for (y in 0 until outputResolution) {
                // Map the output row into normalized V and then terrain-local Z.
                val v = y / denominator
                val localZ = terrain.minLocalZ + v * terrain.worldHeight
                for (x in 0 until outputResolution) {
                    // Map the output column into normalized U and terrain-local X.
                    val u = x / denominator
                    val localX = terrain.minLocalX + u * terrain.worldWidth
                    // Sample and blend the visible terrain layers at this terrain
                    // position, then convert the float color into packed RGBA8888.
                    val rgba =
                        toRgba8888(
                            blendFinalColor(
                                terrain = terrain,
                                u = u,
                                v = v,
                                localX = localX,
                                localZ = localZ,
                                blendMode = blendMode,
                                textureSampler = textureSampler,
                            ),
                        )
                    // Write the packed pixel into the linear output buffer.
                    pixels[offset++] = rgba
                    // Keep a small set of distinct sampled colors for diagnostics so
                    // logs can hint when the final texture is unexpectedly flat.
                    if (distinctSampledColors.size < MaxLoggedDistinctColors) {
                        distinctSampledColors += rgba
                    }
                }
            }
        } finally {
            textureSampler?.closeSafely()
        }

        val message = {
            "Baked runtime terrain final splat texture id='$textureId' revision=$revision " +
                "resolution=${outputResolution}x$outputResolution blendMode=$blendMode " +
                "sampledDistinctColors=${distinctSampledColors.size.coerceAtMost(MaxLoggedDistinctColors)} " +
                "layers=${
                    terrain.allLayers().joinToString { layer ->
                        "${layer.id}:${layer.materialId ?: "<none>"}:visible=${layer.visible}:tiling=${
                            "%.2f".format(layer.tiling)
                        }"
                    }
                }"
        }
        // Emit a more explicit warning when the baked output is expected to be
        // visually flat because only one visible layer contributed any color.
        if (distinctSampledColors.size <= 1 && terrain.allLayers().count { it.visible } <= 1) {
            logger?.warn(TAG) {
                message() + ". Texture is expected to look flat because the terrain has one visible material layer."
            }
        } else {
            logger?.info(TAG, message)
        }
        // Hand the packed pixels to the runtime renderer using repeating wrap and
        // linear filtering so the baked texture behaves like a normal diffuse map.
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

    /**
     * Samples every visible terrain layer at one terrain position and blends the
     * result into the final runtime color.
     */
    private fun blendFinalColor(
        terrain: TerrainData,
        u: Float,
        v: Float,
        localX: Float,
        localZ: Float,
        blendMode: TerrainLayerBlendMode,
        textureSampler: TerrainMaterialTextureSampler?,
    ): TerrainLayerColorDescriptor {
        // Build color/weight pairs first so the selected blend mode can operate on
        // a complete snapshot of all visible layers at this terrain location.
        val samples =
            terrain
                .allLayers()
                .filter { it.visible }
                .map { layer ->
                    RuntimeTerrainLayerSample(
                        // Resolve the layer's material color in texture UV space.
                        color = sampleLayerTextureColor(layer, u, v, textureSampler),
                        // Resolve how strongly this layer contributes at the same point
                        // in terrain-local space.
                        weight = terrain.sampleLayerWeight(layer.id, localX, localZ),
                        visible = layer.visible,
                    )
                }
        // Delegate the actual color compositing to the configured runtime blend
        // mode, falling back to a base terrain color when nothing contributes.
        return blendSamples(samples, blendMode, BaseFallbackColor)
    }

    private fun blendSamples(
        samples: List<RuntimeTerrainLayerSample>,
        blendMode: TerrainLayerBlendMode,
        baseFallbackColor: TerrainLayerColorDescriptor,
    ): TerrainLayerColorDescriptor =
        when (blendMode) {
            TerrainLayerBlendMode.WeightedAverage ->
                weightedAverageColor(
                    samples.filter { it.visible },
                    baseFallbackColor,
                )

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
            result =
                TerrainLayerColorDescriptor(
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

    /**
     * Resolves the sampled color for one terrain layer at normalized UV coordinates.
     *
     * The method prefers a material texture sample, but it falls back safely to a
     * sampler override, the material fallback color, or the serialized layer color
     * so runtime baking can proceed when assets are missing.
     */
    private fun sampleLayerTextureColor(
        layer: TerrainLayer,
        u: Float,
        v: Float,
        textureSampler: TerrainMaterialTextureSampler?,
    ): TerrainLayerColorDescriptor {
        // If the layer does not resolve to a runtime material, keep the serialized
        // layer color so the bake can continue without external assets.
        val material = materialLibrary.find(layer.materialId) ?: return layer.color
        val sampled = trySampleMaterialTexture(layer, material, u, v, textureSampler)
        return sampled ?: material.fallbackColor
    }

    private fun trySampleMaterialTexture(
        layer: TerrainLayer,
        material: TerrainMaterialDescriptor,
        u: Float,
        v: Float,
        textureSampler: TerrainMaterialTextureSampler?,
    ): TerrainLayerColorDescriptor? {
        val sampler = textureSampler ?: return null
        val tiledU = fract(u * layer.tiling)
        val tiledV = fract(v * layer.tiling)
        return try {
            sampler.sample(layer, material, tiledU, tiledV)
        } catch (error: Exception) {
            logger?.warn(TAG, error) {
                "Terrain material sampling failed material='${material.id}' texture='${material.albedoTexture}': ${error.message}"
            }
            null
        }
    }

    private fun createTextureSampler(): TerrainMaterialTextureSampler? =
        try {
            textureSamplerFactory?.create()
        } catch (error: Exception) {
            logger?.warn(TAG, error) {
                "Terrain material texture sampler creation failed; falling back to material colors: ${error.message}"
            }
            null
        }

    private fun TerrainMaterialTextureSampler.closeSafely() {
        try {
            close()
        } catch (error: Exception) {
            logger?.warn(TAG, error) {
                "Terrain material texture sampler cleanup failed: ${error.message}"
            }
        }
    }

    private fun fract(value: Float): Float = value - floor(value)

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
 * both to the backend as the runtime dynamic-model draw command.
 *
 * [finalSplatResolution] is the runtime final material resolution used when a
 * terrain renderer does not provide one. Individual terrain renderers can override it with
 * [TerrainRendererComponent.finalSplatResolution]. This is separate from
 * [TerrainRendererComponent.previewResolution], which is editor-only.
 */
class RuntimeTerrainMeshSystem(
    private val materialBakeService: TerrainMaterialBakeService,
    private val logger: Logger,
    private val finalSplatTextureIdProvider: (EntityId, TerrainRendererComponent) -> String = { entityId, renderer ->
        runtimeTerrainFinalSplatTextureId(entityId, renderer.modelId)
    },
    private val finalSplatResolution: Int? = null,
    private val blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
) : System() {
    constructor(
        materialLibrary: TerrainMaterialLibrary,
        logger: Logger,
        finalSplatResolution: Int? = null,
        blendMode: TerrainLayerBlendMode = TerrainLayerBlendMode.WeightedAverage,
    ) : this(
        materialBakeService = TerrainMaterialBakeService(materialLibrary, logger),
        logger = logger,
        finalSplatResolution = finalSplatResolution,
        blendMode = blendMode,
    )

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        world.query<TransformComponent, TerrainDataComponent, TerrainRendererComponent>().forEach { entity ->
            val terrain = entity.get<TerrainDataComponent>() ?: return@forEach
            val renderer = entity.get<TerrainRendererComponent>() ?: return@forEach
            if (!terrain.dirty && renderer.model != null) {
                return@forEach
            }

            renderer.meshRevision += 1L
            val mesh =
                TerrainMeshBuilder.build(
                    terrain = terrain.data,
                    modelId = renderer.modelId,
                    revision = renderer.meshRevision,
                )
            renderer.model = mesh.model
            renderer.vertexCount = mesh.vertexCount
            renderer.triangleCount = mesh.triangleCount
            val requestedFinalSplatResolution =
                (
                    renderer.finalSplatResolution.takeIf { it > 0 }
                        ?: finalSplatResolution
                        ?: throw IllegalStateException(
                            "Runtime terrain final splat resolution is missing for modelId='${renderer.modelId}'.",
                        )
                    ).coerceIn(2, MaxRuntimeTerrainSplatResolution)
            renderer.finalSplatResolution = requestedFinalSplatResolution

            var bakeSucceeded = false
            val textureId = finalSplatTextureIdProvider(entity.id, renderer)
            try {
                val texture =
                    materialBakeService.bakeFinalSplatTexture(
                        terrain = terrain.data,
                        resolution = requestedFinalSplatResolution,
                        textureId = textureId,
                        revision = renderer.meshRevision,
                        blendMode = blendMode,
                    )
                renderer.replaceFinalSplatTexture(texture)
                renderer.materialRevision += 1L
                renderer.material =
                    renderer.material.copy(
                        baseColor = Color.white(),
                        diffuseTextureRef =
                            MaterialTextureRef(
                                id = texture.id,
                                channel = "baseColor",
                                uvChannel = 0,
                            ),
                    )
                bakeSucceeded = true
            } catch (error: Exception) {
                renderer.replaceFinalSplatTexture(null)
                renderer.material = renderer.material.copy(diffuseTextureRef = null)
                logger.warn(TAG, error) {
                    "Runtime terrain final splat bake failed modelId='${renderer.modelId}': ${error.message}"
                }
            }

            if (bakeSucceeded) {
                terrain.clearDirty()
            }
            logger.info(TAG) {
                "Runtime terrain mesh synced modelId='${renderer.modelId}' revision=${renderer.meshRevision} " +
                    "vertices=${renderer.vertexCount} triangles=${renderer.triangleCount} " +
                    "blendMode=$blendMode materialRevision=${renderer.materialRevision} " +
                    "finalSplatResolution=${renderer.finalSplatResolution}x${renderer.finalSplatResolution} " +
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
    "size=${width}x$height spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} " +
        "[${allLayers().joinToString { layer -> "${layer.id}:${layer.name}:${layer.materialId ?: "<none>"}" }}]"

private fun terrainPathCandidates(path: String): List<String> {
    val normalized = path.trim().replace('\\', '/')
    if (normalized.isBlank()) return emptyList()
    val candidates = linkedSetOf(normalized)
    if (normalized.endsWith(".krterrain", ignoreCase = true)) {
        candidates += normalized.dropLast(".krterrain".length) + ".json"
    }
    return candidates.toList()
}

private fun TerrainData.describeLayerWeights(): String =
    "weights=[" +
        allLayers().joinToString { layer ->
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
