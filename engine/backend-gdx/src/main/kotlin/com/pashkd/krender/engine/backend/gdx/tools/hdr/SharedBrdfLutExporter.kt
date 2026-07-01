package com.pashkd.krender.engine.backend.gdx.tools.hdr

import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifest
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class SharedBrdfLutExporter {
    fun export(
        manifestPath: Path,
        manifest: HdrEnvironmentManifest,
    ): Path {
        val targetPath = HdrEnvironmentManifestLoader.resolve(manifestPath, manifest.brdfLut.path)
        Files.createDirectories(targetPath.parent)
        javaClass.classLoader.getResourceAsStream(BUNDLED_BRDF_LUT)?.use { input ->
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
        } ?: error("Bundled gdx-gltf BRDF LUT is unavailable: $BUNDLED_BRDF_LUT")
        return targetPath
    }

    companion object {
        const val BUNDLED_BRDF_LUT = "net/mgsx/gltf/shaders/brdfLUT.png"
    }
}
