package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.assets.environment.CubemapResource
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.assets.environment.RadianceMipChain
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.tools.common.EditorTexturePreviewService
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewController
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentResourcePreviewTest {
    @Test
    fun `preview availability accepts generated irradiance and radiance face templates`() {
        val environment = testEnvironment()
        val existingPaths =
            buildSet {
                EnvironmentPathResolver.orderedFaceNames.forEach { face ->
                    add("environments/meadow/irradiance/$face.png")
                    add("environments/meadow/radiance/mip_0/$face.png")
                }
            }

        val availability = EnvironmentPreviewController(TestSceneFiles(existingPaths)).availability(environment)

        assertTrue(availability.hasIrradiance)
        assertTrue(availability.hasRadiance)
    }

    @Test
    fun `resource face resolver keeps irradiance paths relative until canvas resolution`() {
        val resolver =
            EnvironmentResourceFaceResolver(
                assetRoot = File("."),
                texturePreviewService = EditorTexturePreviewService(NoOpAssetService),
                queueTexture = {},
            )

        val model =
            resolver.buildPreviewModel(
                environment = testEnvironment(),
                mode = EnvironmentResourceMode.Irradiance,
                selectedMipLevel = 0,
                selectedItemId = "posx",
                hoveredItemId = null,
            )

        assertEquals("environments/meadow/irradiance/posx.png", model.selectedItem?.resolvedPath)
    }

    private fun testEnvironment(): Environment =
        Environment(
            id = "meadow",
            name = "Meadow",
            irradiance = CubemapResource(path = "irradiance/{face}.png", resolution = 128, format = "PNG"),
            radiance =
                RadianceMipChain(
                    baseResolution = 256,
                    mips = listOf(RadianceMip(level = 0, roughness = 0f, path = "radiance/mip_0/{face}.png")),
                ),
            manifestPath = "environments/meadow/meadow.environment.json",
        )
}

private class TestSceneFiles(
    private val existingPaths: Set<String>,
) : SceneFileService {
    override fun writeText(
        path: String,
        text: String,
    ) = Unit

    override fun readText(path: String): String = error("not used")

    override fun ensureDirectories(path: String) = Unit

    override fun exists(path: String): Boolean = path.replace('\\', '/') in existingPaths
}

private object NoOpAssetService : AssetService {
    override fun queue(asset: AssetRef<*>) = Unit

    override fun update(budgetMs: Int): Float = 1f

    override fun isLoaded(asset: AssetRef<*>): Boolean = false

    override fun <T : Any> get(asset: AssetRef<T>): T = error("not used")

    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? = null

    override fun unload(asset: AssetRef<*>) = Unit
}
