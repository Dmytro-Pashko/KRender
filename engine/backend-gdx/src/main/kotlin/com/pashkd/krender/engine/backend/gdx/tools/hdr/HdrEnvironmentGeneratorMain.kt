package com.pashkd.krender.engine.backend.gdx.tools.hdr

import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import java.nio.file.Path

internal object HdrEnvironmentGeneratorMain {
    @JvmStatic
    fun main(rawArgs: Array<String>) {
        val args = rawArgs.dropWhile { it == COMMAND_NAME }
        require(args.isNotEmpty()) {
            "Usage: generate-hdr-env <manifest> [--skybox|--irradiance|--radiance|--all]"
        }
        val manifestPath = Path.of(args.first()).toAbsolutePath().normalize()
        val options = args.drop(1).toSet()
        val loaded = HdrEnvironmentManifestLoader.load(manifestPath)
        require(options.isNotEmpty()) { "At least one generator option is required." }
        if (SKYBOX_OPTION in options || ALL_OPTION in options) {
            val skybox = loaded.manifest.skybox ?: error("Manifest does not define a skybox.")
            val outputs = CubemapCrossSplitter().split(manifestPath, skybox)
            println("Generated ${outputs.size} skybox faces for '${loaded.manifest.name}'.")
        }
        val unsupported = options - setOf(SKYBOX_OPTION, ALL_OPTION)
        require(unsupported.isEmpty()) {
            "Unsupported generator options: ${unsupported.joinToString()}."
        }
    }

    private const val COMMAND_NAME = "generate-hdr-env"
    private const val SKYBOX_OPTION = "--skybox"
    private const val ALL_OPTION = "--all"
}
