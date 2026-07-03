package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneAssetCollectorTest {
    @Test
    fun `collects model terrain and environment dependencies with requirement metadata`() {
        val graph =
            SceneDependencyCollector(
                sceneFiles =
                    TestSceneFiles(
                        existing =
                            setOf(
                                "model/tree.glb",
                                "terrains/field_b.krterrain",
                                "materials/terrain_materials.json",
                                "environments/studio/studio.environment.json",
                            ),
                    ),
            ).collect(
                descriptor =
                    SceneDescriptor(
                        id = "scene:assets",
                        name = "Assets",
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Model",
                                    components =
                                        listOf(
                                            ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/tree.glb")),
                                        ),
                                ),
                                EntityDescriptor(
                                    id = 2L,
                                    name = "Terrain A",
                                    components =
                                        listOf(
                                            ComponentDescriptor(
                                                SceneComponentTypes.Terrain,
                                                mapOf("terrain" to "terrains/field_a.krterrain"),
                                            ),
                                        ),
                                ),
                                EntityDescriptor(
                                    id = 3L,
                                    name = "Terrain B",
                                    components =
                                        listOf(
                                            ComponentDescriptor(
                                                SceneComponentTypes.Terrain,
                                                mapOf("terrain" to "terrains/field_b.krterrain"),
                                            ),
                                        ),
                                ),
                            ),
                        settings =
                            SceneSettingsDescriptor(
                                activeTerrainEntityId = 3L,
                                environment =
                                    SceneEnvironmentDescriptor(
                                        environmentAssetPath = "environments/studio/studio.environment.json",
                                    ),
                            ),
                    ),
            )

        assertEquals(
            listOf(
                SceneDependencyKind.Model to "model/tree.glb",
                SceneDependencyKind.Terrain to "terrains/field_a.krterrain",
                SceneDependencyKind.Terrain to "terrains/field_b.krterrain",
                SceneDependencyKind.TerrainMaterialLibrary to DefaultTerrainMaterialLibraryPath,
                SceneDependencyKind.EnvironmentManifest to "environments/studio/studio.environment.json",
            ),
            graph.dependencies.map { it.kind to it.path },
        )
        assertEquals(
            listOf(
                SceneDependencyRequirement.Required,
                SceneDependencyRequirement.Optional,
                SceneDependencyRequirement.Required,
                SceneDependencyRequirement.Required,
                SceneDependencyRequirement.Optional,
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
                        settings =
                            SceneSettingsDescriptor(
                                environment = SceneEnvironmentDescriptor(environmentAssetPath = null),
                            ),
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Model A",
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/tree.glb"))),
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
                    ),
            )

        assertEquals(listOf(SceneDependencyKind.Model to "model/tree.glb"), graph.dependencies.map { it.kind to it.path })
        assertTrue(graph.missing.isEmpty())
    }
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
