package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentValidatorTest {
    @Test
    fun `validator accepts cubemap templates when all generated faces exist`() {
        val environment =
            Environment(
                id = "meadow",
                name = "Meadow",
                irradiance = CubemapResource(path = "irradiance/{face}.png", resolution = 128, format = "PNG"),
                radiance =
                    RadianceMipChain(
                        baseResolution = 256,
                        mips =
                            listOf(
                                RadianceMip(level = 0, roughness = 0f, path = "radiance/mip_0/{face}.png"),
                                RadianceMip(level = 1, roughness = 1f, path = "radiance/mip_1/{face}.png"),
                            ),
                    ),
                manifestPath = "environments/meadow/meadow.environment.json",
            )

        val existingPaths =
            buildSet {
                EnvironmentPathResolver.orderedFaceNames.forEach { face ->
                    add("environments/meadow/irradiance/$face.png")
                    add("environments/meadow/radiance/mip_0/$face.png")
                    add("environments/meadow/radiance/mip_1/$face.png")
                }
            }

        val report = EnvironmentValidator.validate(environment, TestSceneFiles(existingPaths))

        assertTrue(report.issues.none { it.code == EnvironmentValidator.Codes.IRRADIANCE_FILE_MISSING })
        assertTrue(report.issues.none { it.code == EnvironmentValidator.Codes.RADIANCE_MIP_MISSING })
    }

    @Test
    fun `resolve path does not prepend manifest directory twice`() {
        assertEquals(
            "environments/meadow/irradiance/posx.png",
            EnvironmentPathResolver.resolvePath(
                manifestPath = "environments/meadow/meadow.environment.json",
                relativePath = "environments/meadow/irradiance/posx.png",
            ),
        )
    }
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
