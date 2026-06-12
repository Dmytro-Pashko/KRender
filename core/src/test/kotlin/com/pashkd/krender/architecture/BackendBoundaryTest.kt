package com.pashkd.krender.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Enforces the backend-abstraction boundary documented in AGENTS.md §6:
 *
 * - **Rule A** — `import com.badlogic.gdx` is allowed only inside `engine.backend.gdx`.
 * - **Rule B** — `import net.mgsx.gltf` is allowed only inside `engine.backend.gdx`.
 * - **Rule C** — `engine.api` must never import `engine.backend.gdx`.
 * - **Rule D** — No files outside `engine.backend.gdx` should import backend packages
 *   (known exceptions are allow-listed below).
 *
 * Each rule has an explicit allowlist of pre-existing leaks. The test reports the offending
 * file path, the forbidden import line, and the violated rule so failures are actionable.
 * When a known violation is cleaned up, **remove it from the allowlist** to prevent regression.
 */
class BackendBoundaryTest {

    private data class ImportViolation(
        val relativePath: String,
        val importLine: String,
        val rule: String,
    )

    // ------------------------------------------------------------------
    // Rule A — LibGDX imports outside backend
    // ------------------------------------------------------------------

    @Test
    fun `Rule A - only the gdx backend package imports com_badlogic_gdx`() {
        val violations = collectViolations(
            rule = "A: LibGDX import outside backend",
            importPrefix = "import com.badlogic.gdx",
            allowedPaths = { it.startsWith(BACKEND_PACKAGE_PATH) },
            allowlist = KNOWN_LIBGDX_VIOLATIONS,
        )
        assertNoViolations(violations)
    }

    // ------------------------------------------------------------------
    // Rule B — glTF imports outside backend
    // ------------------------------------------------------------------

    @Test
    fun `Rule B - only the gdx backend package imports net_mgsx_gltf`() {
        val violations = collectViolations(
            rule = "B: glTF import outside backend",
            importPrefix = "import net.mgsx.gltf",
            allowedPaths = { it.startsWith(BACKEND_PACKAGE_PATH) },
            allowlist = KNOWN_GLTF_VIOLATIONS,
        )
        assertNoViolations(violations)
    }

    // ------------------------------------------------------------------
    // Rule C — engine.api must not import backend packages
    // ------------------------------------------------------------------

    @Test
    fun `Rule C - engine_api must not import backend packages`() {
        val violations = collectViolations(
            rule = "C: engine.api imports backend",
            importPrefix = "import com.pashkd.krender.engine.backend.gdx",
            allowedPaths = { !it.startsWith(ENGINE_API_PATH) },
            allowlist = emptySet(),
            scopePath = ENGINE_API_PATH,
        )
        assertNoViolations(violations)
    }

    // ------------------------------------------------------------------
    // Rule D — backend imports from non-backend files
    // ------------------------------------------------------------------

    @Test
    fun `Rule D - non-backend files should not import backend packages`() {
        val violations = collectViolations(
            rule = "D: backend import outside backend",
            importPrefix = "import com.pashkd.krender.engine.backend.gdx",
            allowedPaths = { it.startsWith(BACKEND_PACKAGE_PATH) },
            allowlist = KNOWN_BACKEND_IMPORT_VIOLATIONS,
        )
        assertNoViolations(violations)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Scans source files, collects [ImportViolation]s for any import line starting with
     * [importPrefix] in files where [allowedPaths] returns false, minus the [allowlist].
     *
     * If [scopePath] is non-null, only files under that path are scanned (used for Rule C).
     */
    private fun collectViolations(
        rule: String,
        importPrefix: String,
        allowedPaths: (String) -> Boolean,
        allowlist: Set<String>,
        scopePath: String? = null,
    ): List<ImportViolation> {
        val sourceRoot = resolveSourceRoot()
        return sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { file -> sourceRoot.toRelativePath(file) }
            .filter { relativePath ->
                if (scopePath != null) relativePath.startsWith(scopePath) else true
            }
            .filterNot { relativePath -> allowedPaths(relativePath) }
            .filterNot { relativePath -> relativePath in allowlist }
            .flatMap { relativePath ->
                File(sourceRoot, relativePath)
                    .readLines()
                    .filter { line -> line.trimStart().startsWith(importPrefix) }
                    .map { importLine -> ImportViolation(relativePath, importLine.trim(), rule) }
            }
            .toList()
    }

    private fun assertNoViolations(violations: List<ImportViolation>) {
        if (violations.isEmpty()) return
        val grouped = violations.groupBy { it.rule }
        val message = buildString {
            appendLine("Backend boundary violations detected:")
            appendLine()
            grouped.forEach { (rule, items) ->
                appendLine("  Rule $rule")
                items.forEach { v ->
                    appendLine("    ${v.relativePath}")
                    appendLine("      ${v.importLine}")
                }
            }
            appendLine()
            appendLine("Move the dependency into the backend package, or pass data across the boundary as backend-neutral types.")
        }
        fail(message)
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
        const val ENGINE_API_PATH = "com/pashkd/krender/engine/api/"

        /**
         * Pre-existing boundary leaks: LibGDX `Gdx`, `Pixmap`, and JSON utilities used directly
         * in core packages. These are tolerated tech debt; do not add new entries.
         */
        val KNOWN_LIBGDX_VIOLATIONS = setOf(
            "com/pashkd/krender/engine/ui/editor/ImGuiLayoutConfigLoader.kt",
            "com/pashkd/krender/engine/ui/editor/ImGuiLayoutConfigCodec.kt",
            "com/pashkd/krender/engine/terrain/TerrainMaterialPreviewBaker.kt",
        )

        /**
         * No pre-existing glTF leaks outside the backend. Keep this empty.
         */
        val KNOWN_GLTF_VIOLATIONS = emptySet<String>()

        /**
         * Pre-existing backend import leaks from non-backend files:
         * - `Main.kt` — bootstrap entry point that instantiates `GdxEngineApplication`.
         * - `UiComposerScene.kt` — uses backend-specific `GdxUiScenePreview` and
         *   `GdxUiComposerSkinMetadataReader` for live UI composer preview.
         *
         * These should eventually be abstracted behind core interfaces. Do not add new entries.
         */
        val KNOWN_BACKEND_IMPORT_VIOLATIONS = setOf(
            "com/pashkd/krender/Main.kt",
            "com/pashkd/krender/game/UiComposerScene.kt",
        )
    }
}
