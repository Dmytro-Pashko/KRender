package com.pashkd.krender.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Enforces the backend-abstraction boundary documented in AGENTS.md:
 * only files under `com.pashkd.krender.engine.backend.gdx` may import `com.badlogic.gdx`.
 *
 * This is a build-time ratchet: the [KNOWN_VIOLATIONS] allowlist captures pre-existing
 * leaks so the boundary cannot regress further. New `com.badlogic.gdx` imports in any
 * other package fail the build. When a known violation is removed, delete it from the
 * allowlist so it cannot silently come back.
 */
class BackendBoundaryTest {
    @Test
    fun `only the gdx backend package imports com_badlogic_gdx`() {
        val sourceRoot = resolveSourceRoot()
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file -> sourceRoot.toRelativePath(file) }
            .filterNot { relativePath -> relativePath.startsWith(BACKEND_PACKAGE_PATH) }
            .filter { relativePath -> File(sourceRoot, relativePath).importsGdx() }
            .toSortedSet()

        val unexpected = violations - KNOWN_VIOLATIONS
        if (unexpected.isNotEmpty()) {
            fail(
                "Files outside '$BACKEND_PACKAGE_PATH' must not import 'com.badlogic.gdx'. " +
                    "New violations:\n" + unexpected.joinToString("\n") { "  - $it" } +
                    "\nMove the LibGDX-specific code into the backend package, or pass data across the " +
                    "boundary as backend-neutral types.",
            )
        }
    }

    private fun File.importsGdx(): Boolean =
        useLines { lines ->
            lines.any { line -> line.trimStart().startsWith("import com.badlogic.gdx") }
        }

    private fun File.toRelativePath(file: File): String =
        file.relativeTo(this).invariantSeparatorsPath

    private fun resolveSourceRoot(): File {
        val candidates = listOf(
            File("src/main/kotlin"),
            File("core/src/main/kotlin"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate core main source root from '${File(".").absolutePath}'")
    }

    private companion object {
        const val BACKEND_PACKAGE_PATH = "com/pashkd/krender/engine/backend/gdx/"

        /**
         * Pre-existing boundary leaks (LibGDX `Gdx`, `Pixmap`, and JSON utilities used directly
         * in core packages). These are tolerated tech debt; do not add new entries.
         */
        val KNOWN_VIOLATIONS = setOf(
            "com/pashkd/krender/engine/ui/editor/ImGuiLayoutConfigLoader.kt",
            "com/pashkd/krender/engine/ui/editor/ImGuiLayoutConfigCodec.kt",
            "com/pashkd/krender/engine/terrain/TerrainMaterialPreviewBaker.kt",
        )
    }
}
