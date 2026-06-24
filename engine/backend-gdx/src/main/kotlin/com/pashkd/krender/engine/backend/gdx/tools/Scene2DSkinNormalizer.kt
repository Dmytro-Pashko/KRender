package com.pashkd.krender.engine.backend.gdx.tools

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Normalizes libGDX Scene2D skin files from relaxed libGDX syntax into strict JSON.
 *
 * The input files can use unquoted keys, omitted commas, and other libGDX-specific syntax that
 * the editor asset scanner cannot parse as canonical JSON. This CLI converts those files in place.
 */
object Scene2DSkinNormalizer {
    private val logger: Logger = Logger.getLogger(Scene2DSkinNormalizer::class.java.name)

    @JvmStatic
    fun main(args: Array<String>) {
        val options = parseOptions(args)
        val files = collectFiles(options.targets)
        if (files.isEmpty()) {
            logger.warning("No Scene2D skin JSON files were found for normalization.")
            return
        }

        var normalizedCount = 0
        var unchangedCount = 0
        var failedCount = 0

        files.forEach { file ->
            when (normalizeFile(file, options.dryRun)) {
                NormalizeResult.Normalized -> normalizedCount += 1
                NormalizeResult.Unchanged -> unchangedCount += 1
                NormalizeResult.Failed -> failedCount += 1
            }
        }

        logger.info(
            "Scene2D skin normalization completed. files=${files.size} " +
                "normalized=$normalizedCount unchanged=$unchangedCount failed=$failedCount dryRun=${options.dryRun}",
        )
    }

    private fun parseOptions(args: Array<String>): NormalizeOptions {
        var dryRun = false
        val targets = mutableListOf<Path>()
        args.forEach { arg ->
            when (arg) {
                "--dry-run" -> dryRun = true
                else -> targets.add(Path.of(arg).toAbsolutePath().normalize())
            }
        }
        if (targets.isEmpty()) {
            targets.add(Path.of("assets/ui/skins").toAbsolutePath().normalize())
        }
        return NormalizeOptions(targets = targets, dryRun = dryRun)
    }

    private fun collectFiles(targets: List<Path>): List<Path> =
        targets
            .flatMap { target ->
                when {
                    !Files.exists(target) -> {
                        logger.warning("Skipping missing path '${target.pathString}'.")
                        emptyList()
                    }

                    target.isDirectory() ->
                        Files
                            .walk(target)
                            .use { stream ->
                                stream
                                    .filter { path -> Files.isRegularFile(path) && path.extension.equals("json", ignoreCase = true) }
                                    .sorted(compareBy<Path> { it.pathString.lowercase() })
                                    .toList()
                            }

                    target.extension.equals("json", ignoreCase = true) -> listOf(target)
                    else -> {
                        logger.warning("Skipping unsupported target '${target.pathString}'. Expected a .json file or directory.")
                        emptyList()
                    }
                }
            }

    private fun normalizeFile(
        file: Path,
        dryRun: Boolean,
    ): NormalizeResult {
        val original =
            runCatching { Files.readString(file, StandardCharsets.UTF_8) }.getOrElse { error ->
                logger.log(Level.WARNING, "Failed to read '${file.pathString}': ${error.message}", error)
                return NormalizeResult.Failed
            }
        val normalized =
            runCatching { normalizeText(original) }.getOrElse { error ->
                logger.log(Level.WARNING, "Failed to normalize '${file.pathString}': ${error.message}", error)
                return NormalizeResult.Failed
            }
        if (normalized == original) {
            logger.info("Already normalized: ${file.pathString}")
            return NormalizeResult.Unchanged
        }

        if (dryRun) {
            logger.info("Would normalize: ${file.pathString}")
            return NormalizeResult.Normalized
        }

        return runCatching {
            Files.writeString(file, normalized, StandardCharsets.UTF_8)
            logger.info("Normalized: ${file.pathString}")
            NormalizeResult.Normalized
        }.getOrElse { error ->
            logger.log(Level.WARNING, "Failed to write '${file.pathString}': ${error.message}", error)
            NormalizeResult.Failed
        }
    }

    private fun normalizeText(text: String): String {
        val parsed = JsonReader().parse(text)
        val normalized = parsed.prettyPrint(JsonWriter.OutputType.json, 120).trimEnd()
        return normalized + System.lineSeparator()
    }

    private data class NormalizeOptions(
        val targets: List<Path>,
        val dryRun: Boolean,
    )

    private enum class NormalizeResult {
        Normalized,
        Unchanged,
        Failed,
    }
}
