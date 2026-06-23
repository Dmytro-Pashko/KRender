package com.pashkd.krender.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Enforces the backend-abstraction boundary documented in AGENTS.md §6:
 *
 * - **Rule A** — `import com.badlogic.gdx` is allowed only in backend/platform modules.
 * - **Rule B** — `import net.mgsx.gltf` is allowed only in `engine:backend-gdx`.
 * - **Rule C** — `core` must never import `engine.backend.gdx`.
 * - **Rule D** — backend implementation imports are allowed only in backend/platform modules.
 */
class BackendBoundaryTest {
    private data class SourceRoot(
        val module: String,
        val path: String,
    )

    private data class SourceFile(
        val module: String,
        val sourceRoot: String,
        val relativePath: String,
        val file: File,
    ) {
        val workspacePath: String = "$sourceRoot/$relativePath"
    }

    private data class ImportViolation(
        val module: String,
        val sourceRoot: String,
        val relativePath: String,
        val importLine: String,
        val rule: String,
    )

    @Test
    fun `Rule A - LibGDX imports stay in backend and platform launcher modules`() {
        val violations =
            collectViolations(
                rule = "A: LibGDX import outside backend/platform modules",
                importPrefix = "import com.badlogic.gdx",
                allowed = { source ->
                    source.workspacePath.startsWith(BACKEND_GDX_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_WIN_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_MACOS_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_LINUX_ROOT) ||
                        source.workspacePath.startsWith(ANDROID_ROOT) ||
                        source.workspacePath.startsWith(WOOLBOY_DESKTOP_ROOT) ||
                        source.workspacePath in KNOWN_TOOL_GDX_IMPORTS
                },
            )
        assertNoViolations(violations)
    }

    @Test
    fun `Rule B - glTF imports stay in backend-gdx`() {
        val violations =
            collectViolations(
                rule = "B: glTF import outside engine:backend-gdx",
                importPrefix = "import net.mgsx.gltf",
                allowed = { source -> source.workspacePath.startsWith(BACKEND_GDX_ROOT) },
            )
        assertNoViolations(violations)
    }

    @Test
    fun `Rule C - core must not import backend packages`() {
        val violations =
            collectViolations(
                rule = "C: core imports backend",
                importPrefix = "import com.pashkd.krender.engine.backend.gdx",
                allowed = { source -> !source.workspacePath.startsWith(CORE_ROOT) },
            )
        assertNoViolations(violations)
    }

    @Test
    fun `Rule D - backend imports stay in backend and platform launchers`() {
        val violations =
            collectViolations(
                rule = "D: backend import outside backend/platform modules",
                importPrefix = "import com.pashkd.krender.engine.backend.gdx",
                allowed = { source ->
                    source.workspacePath.startsWith(BACKEND_GDX_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_WIN_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_MACOS_ROOT) ||
                        source.workspacePath.startsWith(DESKTOP_LWJGL3_LINUX_ROOT) ||
                        source.workspacePath.startsWith(ANDROID_ROOT) ||
                        source.workspacePath.startsWith(WOOLBOY_DESKTOP_ROOT)
                },
            )
        assertNoViolations(violations)
    }

    private fun collectViolations(
        rule: String,
        importPrefix: String,
        allowed: (SourceFile) -> Boolean,
    ): List<ImportViolation> =
        sourceFiles()
            .filterNot(allowed)
            .flatMap { source ->
                source.file
                    .readLines()
                    .filter { line -> line.trimStart().startsWith(importPrefix) }
                    .map { importLine ->
                        ImportViolation(
                            module = source.module,
                            sourceRoot = source.sourceRoot,
                            relativePath = source.relativePath,
                            importLine = importLine.trim(),
                            rule = rule,
                        )
                    }
            }.toList()

    private fun assertNoViolations(violations: List<ImportViolation>) {
        if (violations.isEmpty()) return
        val grouped = violations.groupBy { it.rule }
        val message =
            buildString {
                appendLine("Backend boundary violations detected:")
                appendLine()
                grouped.forEach { (rule, items) ->
                    appendLine("  Rule $rule")
                    items.forEach { violation ->
                        appendLine("    module=${violation.module}")
                        appendLine("    sourceRoot=${violation.sourceRoot}")
                        appendLine("    path=${violation.relativePath}")
                        appendLine("      ${violation.importLine}")
                    }
                }
                appendLine()
                appendLine("Move implementation code to a backend/platform module, or pass data across the boundary as backend-neutral types.")
            }
        fail(message)
    }

    private fun sourceFiles(): Sequence<SourceFile> {
        val root = repositoryRoot()
        return SOURCE_ROOTS
            .asSequence()
            .map { sourceRoot -> sourceRoot to File(root, sourceRoot.path) }
            .filter { (_, directory) -> directory.isDirectory }
            .flatMap { (sourceRoot, directory) ->
                directory
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .map { file ->
                        SourceFile(
                            module = sourceRoot.module,
                            sourceRoot = sourceRoot.path,
                            relativePath = file.relativeTo(directory).invariantSeparatorsPath,
                            file = file,
                        )
                    }
            }
    }

    private fun repositoryRoot(): File {
        var current = File(".").canonicalFile
        while (current.parentFile != null) {
            if (File(current, "settings.gradle").isFile) return current
            current = current.parentFile
        }
        error("Could not locate repository root from '${File(".").canonicalPath}'")
    }

    private companion object {
        const val CORE_ROOT = "core/src/main/kotlin/"
        const val BACKEND_GDX_ROOT = "engine/backend-gdx/src/main/kotlin/"
        const val DESKTOP_LWJGL3_WIN_ROOT = "desktop-lwjgl3-win/src/main/kotlin/"
        const val DESKTOP_LWJGL3_MACOS_ROOT = "desktop-lwjgl3-macos/src/main/kotlin/"
        const val DESKTOP_LWJGL3_LINUX_ROOT = "desktop-lwjgl3-linux/src/main/kotlin/"
        const val ANDROID_ROOT = "android/src/main/kotlin/"
        const val WOOLBOY_DESKTOP_ROOT = "apps/woolboy-desktop/src/main/kotlin/"

        val SOURCE_ROOTS =
            listOf(
                SourceRoot("core", "core/src/main/kotlin"),
                SourceRoot("engine:tools", "engine/tools/src/main/kotlin"),
                SourceRoot("engine:scene-player", "engine/scene-player/src/main/kotlin"),
                SourceRoot("engine:backend-gdx", "engine/backend-gdx/src/main/kotlin"),
                SourceRoot("games:woolboy", "games/woolboy/src/main/kotlin"),
                SourceRoot("apps:woolboy-desktop", "apps/woolboy-desktop/src/main/kotlin"),
                SourceRoot("desktop-lwjgl3-win", "desktop-lwjgl3-win/src/main/kotlin"),
                SourceRoot("desktop-lwjgl3-macos", "desktop-lwjgl3-macos/src/main/kotlin"),
                SourceRoot("desktop-lwjgl3-linux", "desktop-lwjgl3-linux/src/main/kotlin"),
                SourceRoot("android", "android/src/main/kotlin"),
            )

        /**
         * Known editor-only GDX helpers remain explicit so new engine:tools GDX imports still fail Rule A.
         */
        val KNOWN_TOOL_GDX_IMPORTS =
            setOf(
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/terraineditor/TerrainMaterialPreviewBaker.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/GdxBitmapFontPreviewLoader.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/GdxSkinEditorPreview.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/GdxSkinResourcePreview.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/GdxSkinStyleEditApplier.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/SafeWidgetBuilder.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/skin/gdx/SkinReloadService.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/uicomposer/gdx/GdxUiComposerSkinMetadataReader.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/uicomposer/gdx/GdxUiSceneBuilder.kt",
                "engine/tools/src/main/kotlin/com/pashkd/krender/engine/tools/uicomposer/gdx/GdxUiScenePreview.kt",
            )
    }
}
