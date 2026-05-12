package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.TerrainComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeSceneValidatorTest {
    @Test
    fun `missing activeCameraEntityId throws`() {
        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveCamera(SceneWorld(), descriptor())
        }

        assertEquals("Runtime scene 'Demo' has no activeCameraEntityId.", error.message)
    }

    @Test
    fun `activeCameraEntityId points to missing entity throws`() {
        val world = SceneWorld()

        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveCamera(world, descriptor(activeCameraEntityId = 12L))
        }

        assertEquals("Runtime scene activeCameraEntityId=12 does not reference an existing entity.", error.message)
    }

    @Test
    fun `activeCameraEntityId points to entity without camera throws`() {
        val world = SceneWorld()
        world.createEntityWithId(12L, "Entity")

        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveCamera(world, descriptor(activeCameraEntityId = 12L))
        }

        assertEquals(
            "Runtime scene activeCameraEntityId=12 does not reference an entity with PerspectiveCameraComponent.",
            error.message,
        )
    }

    @Test
    fun `valid camera returns entity`() {
        val world = SceneWorld()
        val entity = world.createEntityWithId(12L, "Camera")
        entity.add(PerspectiveCameraComponent())

        val result = RuntimeSceneValidator.requireActiveCamera(world, descriptor(activeCameraEntityId = 12L))

        assertEquals(12L, result.id)
    }

    @Test
    fun `missing skybox path throws`() {
        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireSkyboxPath(descriptor())
        }

        assertEquals("Runtime scene 'Demo' has no environment.skyboxAssetPath.", error.message)
    }

    @Test
    fun `missing activeTerrainEntityId throws`() {
        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveTerrain(SceneWorld(), descriptor())
        }

        assertEquals("Runtime scene 'Demo' has no activeTerrainEntityId.", error.message)
    }

    @Test
    fun `activeTerrainEntityId points to missing entity throws`() {
        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveTerrain(SceneWorld(), descriptor(activeTerrainEntityId = 79L))
        }

        assertEquals("Runtime scene activeTerrainEntityId=79 does not reference an existing entity.", error.message)
    }

    @Test
    fun `terrain entity without TerrainComponent throws`() {
        val world = SceneWorld()
        world.createEntityWithId(79L, "Terrain")

        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveTerrain(world, descriptor(activeTerrainEntityId = 79L))
        }

        assertEquals(
            "Runtime scene activeTerrainEntityId=79 does not reference an entity with TerrainComponent.",
            error.message,
        )
    }

    @Test
    fun `terrain with blank path throws`() {
        val world = SceneWorld()
        val entity = world.createEntityWithId(79L, "Terrain")
        entity.add(TerrainComponent(terrain = AssetRef.terrain("   ")))

        val error = assertFailsWith<IllegalStateException> {
            RuntimeSceneValidator.requireActiveTerrain(world, descriptor(activeTerrainEntityId = 79L))
        }

        assertEquals("Runtime terrain entityId=79 has blank terrain asset path.", error.message)
    }

    private fun descriptor(
        activeCameraEntityId: Long? = null,
        activeTerrainEntityId: Long? = null,
    ): SceneDescriptor =
        SceneDescriptor(
            id = "scene:demo",
            name = "Demo",
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = activeCameraEntityId,
                activeTerrainEntityId = activeTerrainEntityId,
            ),
        )
}
