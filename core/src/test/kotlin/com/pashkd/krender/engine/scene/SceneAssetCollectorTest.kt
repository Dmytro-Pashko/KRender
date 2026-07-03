package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneAssetCollectorTest {
    @Test
    fun `collects model and terrain dependencies with requirement metadata`() {
        val graph = collector(existingAssetPaths()).collect(assetSceneDescriptor())

        assertEquals(
            listOf(
                SceneDependencyKind.Model to "model/tree.glb",
                SceneDependencyKind.Terrain to "terrains/field_a.krterrain",
                SceneDependencyKind.Terrain to "terrains/field_b.krterrain",
                SceneDependencyKind.TerrainMaterialLibrary to DefaultTerrainMaterialLibraryPath,
            ),
            graph.dependencies.map { it.kind to it.path },
        )
        assertEquals(
            listOf(
                SceneDependencyRequirement.Required,
                SceneDependencyRequirement.Optional,
                SceneDependencyRequirement.Required,
                SceneDependencyRequirement.Required,
            ),
            graph.dependencies.map { it.requirement },
        )
        assertEquals(
            listOf("terrains/field_a.krterrain"),
            graph.missing.map { it.dependency.path },
        )
        assertEquals(
            listOf("model/tree.glb", "terrains/field_a.krterrain", "terrains/field_b.krterrain"),
            graph.schedulableAssets.map { it.path },
        )
    }

    @Test
    fun `de-duplicates repeated dependencies preserving first-seen order`() {
        val graph =
            SceneDependencyCollector(sceneFiles = TestSceneFiles(existing = setOf("model/tree.glb"))).collect(
                descriptor =
                    SceneDescriptor(
                        id = "scene:dedupe",
                        name = "Dedupe",
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Model A",
                                    components =
                                        listOf(
                                            ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/tree.glb")),
                                        ),
                                ),
                                EntityDescriptor(
                                    id = 2L,
                                    name = "Model B",
                                    components =
                                        listOf(
                                            ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to " model\\tree.glb ")),
                                        ),
                                ),
                            ),
                        settings =
                            SceneSettingsDescriptor(
                                environment = SceneEnvironmentDescriptor(environmentAssetPath = null),
                            ),
                    ),
            )

        assertEquals(
            listOf(SceneDependencyKind.Model to "model/tree.glb"),
            graph.dependencies.map { it.kind to it.path },
        )
        assertTrue(graph.missing.isEmpty())
    }

    private fun collector(existing: Set<String>): SceneDependencyCollector = SceneDependencyCollector(sceneFiles = TestSceneFiles(existing = existing))

    private fun existingAssetPaths(): Set<String> =
        setOf(
            "model/tree.glb",
            "terrains/field_b.krterrain",
            "materials/terrain_materials.json",
        )

    private fun assetSceneDescriptor(): SceneDescriptor =
        SceneDescriptor(
            id = "scene:assets",
            name = "Assets",
            entities =
                listOf(
                    modelEntity(),
                    terrainEntity(id = 2L, name = "Terrain A", path = "terrains/field_a.krterrain"),
                    terrainEntity(id = 3L, name = "Terrain B", path = "terrains/field_b.krterrain"),
                ),
            settings =
                SceneSettingsDescriptor(
                    activeTerrainEntityId = 3L,
                    environment = SceneEnvironmentDescriptor(environmentAssetPath = null),
                ),
        )

    private fun modelEntity(): EntityDescriptor =
        EntityDescriptor(
            id = 1L,
            name = "Model",
            components = listOf(ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/tree.glb"))),
        )

    private fun terrainEntity(
        id: Long,
        name: String,
        path: String,
    ): EntityDescriptor =
        EntityDescriptor(
            id = id,
            name = name,
            components = listOf(ComponentDescriptor(SceneComponentTypes.Terrain, mapOf("terrain" to path))),
        )
}

private class TestSceneFiles(
    private val existing: Set<String>,
) : SceneFileService {
    override fun writeText(
        path: String,
        text: String,
    ) = Unit

    override fun readText(path: String): String = error("Not needed in dependency tests")

    override fun ensureDirectories(path: String) = Unit

    override fun exists(path: String): Boolean = path in existing
}
