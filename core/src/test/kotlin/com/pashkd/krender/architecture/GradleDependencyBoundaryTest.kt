package com.pashkd.krender.architecture

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GradleDependencyBoundaryTest {

    @Test
    fun `backend neutral modules do not depend on gdx backend artifacts`() {
        val violations = mutableListOf<String>()

        forbiddenDependencyChecks.forEach { check ->
            val buildFile = root.resolve(check.buildFile)
            assertTrue(buildFile.exists(), "Missing Gradle file: ${check.buildFile}")

            val text = buildFile.readText()
            check.forbiddenPatterns.forEach { pattern ->
                if (text.contains(pattern)) {
                    violations += "${check.module} must not declare `$pattern` in ${check.buildFile}"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString(
                separator = "\n",
                prefix = "Gradle dependency boundary violations:\n",
            ),
        )
    }

    private data class DependencyCheck(
        val module: String,
        val buildFile: String,
        val forbiddenPatterns: List<String>,
    )

    private companion object {
        private val root: Path = repositoryRoot()

        private val backendDependencyPatterns = listOf(
            "com.badlogicgames.gdx:gdx",
            "com.github.mgsx-dev.gdx-gltf:gltf",
            "imgui-gl",
            "project(':engine:backend-gdx')",
            "project(\":engine:backend-gdx\")",
        )

        private val forbiddenDependencyChecks = listOf(
            DependencyCheck(
                module = "core",
                buildFile = "core/build.gradle",
                forbiddenPatterns = backendDependencyPatterns,
            ),
            DependencyCheck(
                module = "engine:scene-player",
                buildFile = "engine/scene-player/build.gradle",
                forbiddenPatterns = backendDependencyPatterns,
            ),
            DependencyCheck(
                module = "games:woolboy",
                buildFile = "games/woolboy/build.gradle",
                forbiddenPatterns = backendDependencyPatterns + listOf(
                    "project(':engine:tools')",
                    "project(\":engine:tools\")",
                ),
            ),
            DependencyCheck(
                module = "engine:tools",
                buildFile = "engine/tools/build.gradle",
                forbiddenPatterns = listOf(
                    "com.github.mgsx-dev.gdx-gltf:gltf",
                    "imgui-gl",
                    "project(':engine:backend-gdx')",
                    "project(\":engine:backend-gdx\")",
                ),
            ),
        )

        private fun repositoryRoot(): Path {
            var current = Path("").toAbsolutePath().normalize()
            while (current.parent != null) {
                if (current.resolve("settings.gradle").exists()) return current
                current = current.parent
            }
            error("Could not locate repository root from '${Path("").toAbsolutePath().absolutePathString()}'")
        }
    }
}
