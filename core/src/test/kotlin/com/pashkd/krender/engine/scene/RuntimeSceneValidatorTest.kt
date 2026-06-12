package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeSceneValidatorTest {
    @Test
    fun `missing active camera is an error`() {
        val report =
            validate(
                descriptor = descriptor(),
                existingFiles = emptySet(),
            )

        assertFalse(report.isValid)
        assertEquals(listOf(SceneValidationIssueCode.MissingActiveCamera), report.errors.map { it.code })
    }

    @Test
    fun `broken parent reference is an error`() {
        val report =
            validate(
                descriptor =
                    descriptor(
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Camera",
                                    parentId = 99L,
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Camera)),
                                ),
                            ),
                        activeCameraEntityId = 1L,
                    ),
                existingFiles = emptySet(),
            )

        assertTrue(report.errors.any { it.code == SceneValidationIssueCode.BrokenParentReference })
    }

    @Test
    fun `missing model asset is an error`() {
        val report =
            validate(
                descriptor =
                    descriptor(
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Camera",
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Camera)),
                                ),
                                EntityDescriptor(
                                    id = 2L,
                                    name = "Model",
                                    components =
                                        listOf(
                                            ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/missing.glb")),
                                        ),
                                ),
                            ),
                        activeCameraEntityId = 1L,
                    ),
                existingFiles = emptySet(),
            )

        assertTrue(report.errors.any { it.code == SceneValidationIssueCode.MissingModelAsset })
    }

    @Test
    fun `optional terrain absent is valid`() {
        val report =
            validate(
                descriptor =
                    descriptor(
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Camera",
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Camera)),
                                ),
                            ),
                        activeCameraEntityId = 1L,
                        activeTerrainEntityId = null,
                        showSkybox = false,
                    ),
                existingFiles = emptySet(),
            )

        assertTrue(report.isValid)
    }

    @Test
    fun `invalid active terrain reference is an error`() {
        val report =
            validate(
                descriptor =
                    descriptor(
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Camera",
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Camera)),
                                ),
                            ),
                        activeCameraEntityId = 1L,
                        activeTerrainEntityId = 79L,
                    ),
                existingFiles = emptySet(),
            )

        assertTrue(report.errors.any { it.code == SceneValidationIssueCode.MissingActiveTerrainEntity })
    }

    @Test
    fun `skybox absent with showSkybox false is valid`() {
        val report =
            validate(
                descriptor =
                    descriptor(
                        entities =
                            listOf(
                                EntityDescriptor(
                                    id = 1L,
                                    name = "Camera",
                                    components = listOf(ComponentDescriptor(SceneComponentTypes.Camera)),
                                ),
                            ),
                        activeCameraEntityId = 1L,
                        showSkybox = false,
                        skyboxAssetPath = null,
                    ),
                existingFiles = emptySet(),
            )

        assertTrue(report.isValid)
        assertTrue(report.warnings.none { it.code == SceneValidationIssueCode.MissingSkyboxPath })
    }

    private fun validate(
        descriptor: SceneDescriptor,
        existingFiles: Set<String>,
    ): SceneValidationReport {
        val collector = SceneDependencyCollector(sceneFiles = ValidatorSceneFiles(existingFiles))
        val graph = collector.collect(descriptor)
        return RuntimeSceneValidator.validate(descriptor, graph)
    }

    private fun descriptor(
        entities: List<EntityDescriptor> = emptyList(),
        activeCameraEntityId: Long? = null,
        activeTerrainEntityId: Long? = null,
        showSkybox: Boolean = true,
        skyboxAssetPath: String? = null,
    ): SceneDescriptor =
        SceneDescriptor(
            id = "scene:demo",
            name = "Demo",
            entities = entities,
            settings =
                SceneSettingsDescriptor(
                    activeCameraEntityId = activeCameraEntityId,
                    activeTerrainEntityId = activeTerrainEntityId,
                    environment =
                        SceneEnvironmentDescriptor(
                            skyboxAssetPath = skyboxAssetPath,
                            showSkybox = showSkybox,
                        ),
                ),
        )
}

private class ValidatorSceneFiles(
    private val existingFiles: Set<String>,
) : SceneFileService {
    override fun writeText(
        path: String,
        text: String,
    ) = Unit

    override fun readText(path: String): String = error("Not required")

    override fun ensureDirectories(path: String) = Unit

    override fun exists(path: String): Boolean = path in existingFiles
}
