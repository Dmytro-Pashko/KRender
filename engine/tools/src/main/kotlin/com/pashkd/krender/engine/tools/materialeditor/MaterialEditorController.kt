package com.pashkd.krender.engine.tools.materialeditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.scene.DefaultTerrainMaterialLibraryPath
import com.pashkd.krender.engine.scene.SceneComponentTypes
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.terrain.TerrainPersistence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class MaterialEditorController(
    private val state: MaterialEditorState,
    private val engine: EngineContext,
) {
    private val terrainPersistence = TerrainPersistence(engine.logger)
    private val assetRoot = engine.assetRegistry.baseDir().toPath().toAbsolutePath().normalize()
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun reload() {
        try {
            val library = TerrainMaterialLibrary(engine.logger, engine.sceneFiles)
            library.load(state.path)
            state.materials.clear()
            state.materials += library.all().map {
                MaterialDraft(it.id, it.id, it.name, it.albedoTexture, it.fallbackColor, it.defaultTiling)
            }
            state.loadedIds = library.all().map { it.id }.toSet()
            state.selectedIndex = state.selectedIndex.coerceIn(0, (state.materials.size - 1).coerceAtLeast(0))
            state.texturePaths = engine.assetRegistry.scanSnapshot().assets
                .filter { it.category == AssetCategory.Texture }
                .map { it.path }.sorted()
            state.dirty = false
            state.status = "Loaded ${state.materials.size} material(s)"
        } catch (error: Exception) {
            state.status = "Reload failed: ${error.message}"
            engine.logger.error(TAG, error) { state.status }
        }
    }

    fun add() {
        val id = generateSequence(1) { it + 1 }.map { "terrain/material_$it" }
            .first { candidate -> state.materials.none { it.id == candidate } }
        state.materials += MaterialDraft(
            null, id, "New Material", state.texturePaths.firstOrNull() ?: "textures/t_grass_01_s.png",
            com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor(), 8f,
        )
        state.selectedIndex = state.materials.lastIndex
        state.dirty = true
    }

    fun removeSelected() {
        if (state.materials.size <= 1) {
            state.status = "At least one material is required"
            return
        }
        state.materials.removeAt(state.selectedIndex)
        state.selectedIndex = state.selectedIndex.coerceAtMost(state.materials.lastIndex)
        state.dirty = true
    }

    fun save() {
        try {
            validate()
            val renames = state.materials.mapNotNull { draft ->
                draft.originalId?.takeIf { it != draft.id }?.let { it to draft.id }
            }.toMap()
            val removedIds = state.loadedIds - state.materials.mapNotNull { it.originalId }.toSet()
            if (removedIds.isNotEmpty()) require(migratedTerrainFiles(removedIds.associateWith { it }).isEmpty()) {
                "A removed material is still used by a terrain"
            }
            val writes = linkedMapOf<Path, String>()
            if (renames.isNotEmpty()) writes.putAll(migratedTerrainFiles(renames))
            writes[resolveAsset(state.path)] = encodeLibrary()
            writeTransaction(writes)
            state.materials.forEach { it.originalId = it.id }
            state.loadedIds = state.materials.map { it.id }.toSet()
            state.dirty = false
            state.status = "Saved library and updated ${writes.size - 1} terrain file(s)"
        } catch (error: Exception) {
            state.status = "Save failed: ${error.message}"
            engine.logger.error(TAG, error) { state.status }
        }
    }

    private fun validate() {
        require(state.materials.isNotEmpty()) { "Library needs at least one material" }
        val ids = mutableSetOf<String>()
        state.materials.forEach { draft ->
            require(draft.id.isNotBlank() && draft.name.isNotBlank()) { "ID and Name are required" }
            require(ids.add(draft.id)) { "Duplicate material ID: ${draft.id}" }
            require(draft.defaultTiling > 0f) { "Tiling must be positive: ${draft.id}" }
            require(draft.albedoTexture in state.texturePaths || Files.isRegularFile(resolveAsset(draft.albedoTexture))) {
                "Texture asset not found: ${draft.albedoTexture}"
            }
        }
    }

    private fun encodeLibrary(): String {
        val root = buildJsonObject {
            put("formatVersion", JsonPrimitive(1))
            put("materials", buildJsonArray {
                state.materials.forEach { material ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(material.id))
                        put("name", JsonPrimitive(material.name))
                        put("albedoTexture", JsonPrimitive(material.albedoTexture))
                        put("fallbackColor", buildJsonObject {
                            put("r", JsonPrimitive(material.fallbackColor.r))
                            put("g", JsonPrimitive(material.fallbackColor.g))
                            put("b", JsonPrimitive(material.fallbackColor.b))
                            put("a", JsonPrimitive(material.fallbackColor.a))
                        })
                        put("defaultTiling", JsonPrimitive(material.defaultTiling))
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    private fun migratedTerrainFiles(renames: Map<String, String>): Map<Path, String> {
        val sceneBindings = sceneBindings()
        val result = linkedMapOf<Path, String>()
        val terrainDir = assetRoot.resolve("terrains")
        if (!Files.isDirectory(terrainDir)) return result
        Files.walk(terrainDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }.forEach { file ->
                val descriptor = terrainPersistence.decodeDescriptor(Files.readString(file, StandardCharsets.UTF_8))
                if (descriptor.terrain.layers.none { it.materialId in renames }) return@forEach
                val relativePath = assetRoot.relativize(file).toString().replace('\\', '/')
                val binding = descriptor.materialLibraryPath ?: run {
                    val bindings = sceneBindings[relativePath].orEmpty()
                    require(bindings.size <= 1) { "Ambiguous material library for $relativePath: $bindings" }
                    bindings.firstOrNull() ?: DefaultTerrainMaterialLibraryPath
                }
                if (binding.replace('\\', '/') != state.path.replace('\\', '/')) return@forEach
                val updated = descriptor.copy(terrain = descriptor.terrain.copy(
                    layers = descriptor.terrain.layers.map { layer ->
                        layer.copy(materialId = renames[layer.materialId] ?: layer.materialId)
                    },
                ))
                result[file] = terrainPersistence.encode(updated) + "\n"
            }
        }
        return result
    }

    private fun sceneBindings(): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        val sceneDir = assetRoot.resolve("scenes")
        if (!Files.isDirectory(sceneDir)) return result
        Files.walk(sceneDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".krscene") }.forEach { file ->
                val scene = SceneSerializer.decode(Files.readString(file, StandardCharsets.UTF_8))
                scene.entities.forEach { entity ->
                    entity.components.filter { it.type == SceneComponentTypes.Terrain }.forEach { component ->
                        component.properties["terrain"]?.replace('\\', '/')?.let { path ->
                            result.getOrPut(path) { mutableSetOf() }.add(scene.settings.terrain.materialLibraryPath)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun writeTransaction(writes: Map<Path, String>) {
        val originals = writes.keys.associateWith { Files.readString(it, StandardCharsets.UTF_8) }
        val staged = mutableMapOf<Path, Path>()
        try {
            writes.forEach { (file, content) ->
                val temp = Files.createTempFile(file.parent, ".krender-material-", ".tmp")
                Files.writeString(temp, content, StandardCharsets.UTF_8)
                staged[file] = temp
            }
            val replaced = mutableListOf<Path>()
            try {
                staged.forEach { (file, temp) ->
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
                    replaced.add(file)
                }
            } catch (error: Exception) {
                replaced.forEach { file -> Files.writeString(file, originals.getValue(file), StandardCharsets.UTF_8) }
                throw error
            }
        } finally {
            staged.values.forEach(Files::deleteIfExists)
        }
    }

    private fun resolveAsset(relativePath: String): Path {
        val path = assetRoot.resolve(relativePath.replace('\\', '/')).normalize()
        require(path.startsWith(assetRoot)) { "Asset path escapes project assets: $relativePath" }
        return path
    }

    companion object { private const val TAG = "MaterialEditor" }
}
