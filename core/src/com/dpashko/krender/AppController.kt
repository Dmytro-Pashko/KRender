package com.dpashko.krender

//import com.dpashko.krender.scene.terrain.generator.TerrainGeneratorResult
import com.dpashko.krender.scene.SceneFactory
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.editor.EditorResult
import com.dpashko.krender.scene.navigator.Navigator
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**

The interface for application lifecycle listeners.
 */
interface AppController {

    /**

    Called when the application is first created.
     */
    fun create()

    /**

    Called every frame to update and render the application.
     */
    fun update(delta: Float)

    /**

    Called when the application is paused, such as when the user switches to a different app.
     */
    fun pause()

    /**

    Called when the application is resumed, such as when the user returns to the app or screen.
     */
    fun resume()

    /**

    Called when the application is about to be disposed, such as when the user closes the app.
     */
    fun dispose()

    fun resize(width: Int, height: Int)
}

/**
 * Main app controller that initiates and handles app components e.g scenes lifecycle, navigation.
 */
@Singleton
class AppControllerImpl @Inject constructor(
    private val sceneFactory: SceneFactory
) : AppController, Navigator<Any> {

    private val sceneStack = Stack<BaseScene<*, *>>()

    /**
     * Initialize any app-scoped components here.
     */
    override fun create() {
        println("Started AppController initialization.")
        // Initialize any app-scoped components here

        val entryPointScene = sceneFactory.getEditorScene()
        pushScene(entryPointScene)
        println("AppController initialized.")
    }

    override fun update(delta: Float) {
        activeScene()?.update(delta)
        activeScene()?.render()
    }

    /**
     * Pause the current scene.
     */
    override fun pause() {
        activeScene()?.pause()
    }

    /**
     * Resume the current scene.
     */
    override fun resume() {
        activeScene()?.resume()
    }

    /**
     * Stop and destroy the current scene and dispose of any app-scoped components.
     */
    override fun dispose() {
        sceneStack.forEach { it.dispose() }
        sceneStack.clear()
        println("AppController disposed.")
    }

    override fun resize(width: Int, height: Int) {
        activeScene()?.resize(width, height)
    }

    override fun navigateTo(action: Any) {
        println("Received navigation action: $action")
        when (action) {
            is EditorResult -> handleEditorSceneResult(action)
//            is TerrainGeneratorResult -> handleTerrainGenerator(action)
        }
    }

    private fun handleEditorSceneResult(result: EditorResult) {
//        when (result) {
//            EditorResult.GENERATE_TERRAIN -> pushScene(sceneFactory.getTerrainGeneratorScene())
//        }
    }

//    private fun handleTerrainGenerator(result: TerrainGeneratorResult) {
//        when (result) {
//            TerrainGeneratorResult.COMPLETED -> popScene()
//        }
//    }

    private fun activeScene(): BaseScene<*, *>? {
        return if (sceneStack.isNotEmpty()) {
            sceneStack.peek()
        } else {
            null
        }
    }

    /**
     * Adds the specified scene to the top of the scene stack and sets it as the active scene.
     *
     * @param scene The scene to add.
     */
    private fun pushScene(scene: BaseScene<*, *>) {
        activeScene()?.pause()
        sceneStack.push(scene)
        scene.create()
    }

    /**
     * Replaces the active scene with the specified scene and sets it as the active scene.
     *
     * @param scene The scene to replace the active scene with.
     */
    private fun replaceScene(scene: BaseScene<*, *>) {
        activeScene()?.dispose()
        sceneStack.pop()
        sceneStack.push(scene)
        scene.create()
    }

    /**
     * Removes the active scene from the top of the scene stack and sets the previous scene as the active scene.
     */
    private fun popScene() {
        activeScene()?.dispose()
        sceneStack.pop()
        activeScene()?.resume()
    }
}
