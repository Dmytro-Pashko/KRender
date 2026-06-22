package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Disposable
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.tools.skin.SkinAssetResolver
import com.pashkd.krender.engine.tools.skin.SkinLoadResult
import com.pashkd.krender.engine.tools.skin.SkinProblem
import com.pashkd.krender.engine.tools.skin.SkinProblemCategory
import com.pashkd.krender.engine.tools.skin.SkinProblemSeverity
import com.pashkd.krender.engine.tools.skin.SkinProjectLoader
import com.pashkd.krender.engine.tools.skin.SkinValidationContext
import com.pashkd.krender.engine.tools.skin.SkinValidator
import com.pashkd.krender.engine.tools.skin.sortedForDisplay

class LoadedSkinHandle internal constructor(
    internal val skin: Skin,
) : Disposable {
    override fun dispose() {
        skin.dispose()
    }
}

class SkinReloadService(
    private val logger: Logger,
    private val assetResolver: SkinAssetResolver,
    private val projectLoader: SkinProjectLoader,
    private val validators: List<SkinValidator>,
) : Disposable {
    private var currentHandle: LoadedSkinHandle? = null
    val currentSkinHandle: LoadedSkinHandle? get() = currentHandle

    fun reload(inputPath: String?): SkinLoadResult {
        currentHandle?.dispose()
        currentHandle = null

        val project = assetResolver.resolve(inputPath)
        val baseResult =
            if (project == null && !inputPath.isNullOrBlank()) {
                SkinLoadResult(
                    problems =
                        listOf(
                            SkinProblem(
                                severity = SkinProblemSeverity.Error,
                                category = SkinProblemCategory.Project,
                                message = "Selected skin path was not found.",
                                source = inputPath,
                            ),
                        ),
                )
            } else {
                projectLoader.inspect(project)
            }
        val skinFile = baseResult.project?.skinFile
        val problems = baseResult.problems.toMutableList()

        if (skinFile != null) {
            try {
                val skin = Skin(Gdx.files.absolute(skinFile.absolutePath))
                currentHandle = LoadedSkinHandle(skin)
            } catch (error: Exception) {
                problems +=
                    SkinProblem(
                        severity = SkinProblemSeverity.Error,
                        category = SkinProblemCategory.Loading,
                        message = error.message ?: error::class.simpleName ?: "Unknown Skin load error.",
                        source = skinFile.path,
                    )
                logger.warn(TAG, error) { "Skin Editor failed to load skin path='${skinFile.path}': ${error.message}" }
            }
        }

        val result = baseResult.copy(problems = problems, previewSkinAvailable = currentHandle != null)
        val validationProblems = validators.flatMap { validator -> validator.validate(SkinValidationContext(result)) }
        return result.copy(problems = (result.problems + validationProblems).sortedForDisplay())
    }

    override fun dispose() {
        currentHandle?.dispose()
        currentHandle = null
    }

    private companion object {
        private const val TAG = "SkinReloadService"
    }
}
