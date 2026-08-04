package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * UI-level actions for the Asset Browser scene.
 */
class AssetBrowserUiOperations(
    private val state: AssetBrowserState,
    private val context: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun exit() {
        state.statusMessage = "Exit requested."
        context.logger.info(TAG) { "Asset Browser exit requested from controls panel" }
        context.requestExit()
    }

    fun openAssetsFolder() {
        val directory = context.assetRegistry.baseDir()
        try {
            if (!directory.exists() || !directory.isDirectory) {
                state.statusMessage = "Assets folder does not exist: ${directory.path}"
                context.logger.warn(TAG) { "Asset Browser assets folder is unavailable path='${directory.path}'" }
                return
            }
            if (!Desktop.isDesktopSupported()) {
                state.statusMessage = "Opening folders is not supported on this desktop."
                context.logger.warn(TAG) { "Desktop.open is unavailable for assets folder path='${directory.path}'" }
                return
            }
            Desktop.getDesktop().open(directory)
            state.statusMessage = "Opened assets folder: ${directory.path}"
            context.logger.info(TAG) { "Opened Asset Browser assets folder path='${directory.path}'" }
        } catch (error: Exception) {
            state.statusMessage = "Open assets folder failed: ${error.message}"
            context.logger.error(TAG, error) { "Failed to open assets folder path='${directory.path}': ${error.message}" }
        }
    }

    fun cleanLogs() {
        context.logs.clear()
        val result = AssetBrowserLogCleaner.clean(savedLogsDirectory(context.assetRegistry.baseDir()))
        state.errorMessage =
            result.failures
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "Log cleanup failed: ") { failure -> "${failure.fileName} (${failure.message})" }
        state.statusMessage =
            if (result.failures.isEmpty()) {
                "Logs cleared. Deleted ${result.deletedCount} saved log file(s)."
            } else {
                "Logs cleared in memory. Deleted ${result.deletedCount} saved log file(s), failed ${result.failures.size}."
            }
    }

    fun saveUiLayout() {
        try {
            val config = layoutTracker.currentConfig()
            ImGuiLayoutConfigCodec.save(AssetBrowserUiLayoutDefaults.assetPath, config, context.sceneFiles)
            state.statusMessage = "UI layout saved."
            context.logger.info(TAG) {
                "Asset Browser UI layout saved path='${AssetBrowserUiLayoutDefaults.assetPath}' panels=${config.panels.size}"
            }
        } catch (error: Exception) {
            state.statusMessage = "UI layout save failed: ${error.message}"
            context.logger.error(TAG, error) {
                "Failed to save Asset Browser UI layout path='${AssetBrowserUiLayoutDefaults.assetPath}': ${error.message}"
            }
        }
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(AssetBrowserUiLayoutDefaults.config)
        state.statusMessage = "UI layout reset to default."
        context.logger.info(TAG) {
            "Asset Browser UI layout reset to default panels=${AssetBrowserUiLayoutDefaults.config.panels.size}"
        }
    }

    companion object {
        private const val TAG = "AssetBrowserUiOperations"

        private fun savedLogsDirectory(assetBaseDir: File): Path {
            val basePath = assetBaseDir.toPath().toAbsolutePath().normalize()
            return if (basePath.fileName?.toString().equals("assets", ignoreCase = true)) {
                basePath.resolve("logs")
            } else {
                basePath.resolve("assets").resolve("logs")
            }
        }
    }
}

internal data class AssetBrowserLogCleanupResult(
    val deletedCount: Int,
    val failures: List<AssetBrowserLogCleanupFailure>,
)

internal data class AssetBrowserLogCleanupFailure(
    val fileName: String,
    val message: String,
)

internal object AssetBrowserLogCleaner {
    fun clean(logsDirectory: Path): AssetBrowserLogCleanupResult {
        if (!Files.exists(logsDirectory)) {
            return AssetBrowserLogCleanupResult(deletedCount = 0, failures = emptyList())
        }
        if (!Files.isDirectory(logsDirectory)) {
            return AssetBrowserLogCleanupResult(
                deletedCount = 0,
                failures =
                    listOf(
                        AssetBrowserLogCleanupFailure(
                            logsDirectory.fileName?.toString() ?: logsDirectory.toString(),
                            "not a directory",
                        ),
                    ),
            )
        }

        var deletedCount = 0
        val failures = mutableListOf<AssetBrowserLogCleanupFailure>()
        Files.list(logsDirectory).use { entries ->
            entries
                .filter(Files::isRegularFile)
                .forEach { file ->
                    try {
                        Files.deleteIfExists(file)
                        deletedCount += 1
                    } catch (error: Exception) {
                        failures +=
                            AssetBrowserLogCleanupFailure(
                                file.fileName?.toString() ?: file.toString(),
                                error.message ?: error.javaClass.simpleName,
                            )
                    }
                }
        }
        return AssetBrowserLogCleanupResult(deletedCount = deletedCount, failures = failures)
    }
}
