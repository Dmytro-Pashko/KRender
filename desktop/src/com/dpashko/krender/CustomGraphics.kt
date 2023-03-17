package com.dpashko.krender

import com.badlogic.gdx.AbstractGraphics
import com.badlogic.gdx.Application
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.Graphics.BufferFormat
import com.badlogic.gdx.Graphics.GraphicsType
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.GLVersion
import com.badlogic.gdx.utils.GdxRuntimeException
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL32

class CustomGraphics(
    var gl20: GL20,
    var gl30: GL30?,
    private val windowHandle: Long,
) : AbstractGraphics() {

    private var backBufferWidth = 0
    private var backBufferHeight = 0
    private var logicalWidth = 0
    private var logicalHeight = 0
    private var bufferFormat: BufferFormat? = null
    private var lastFrameTime: Long = -1
    private var resetDeltaTime = false
    private var frameId: Long = 0

    private var isContinuous = true
    private var frameCounterStart: Long = 0
    private var frames = 0
    private var fps = 0
    private var deltaTime = 0f

    private var glVersion: GLVersion? = null

    var tmpBuffer = BufferUtils.createIntBuffer(1)
    var tmpBuffer2 = BufferUtils.createIntBuffer(1)
    var tmpBuffer3 = BufferUtils.createIntBuffer(1)
    var tmpBuffer4 = BufferUtils.createIntBuffer(1)

    init {
        updateFramebufferInfo()
        initiateGL()
        enableCubeMapSeamless(true)
    }

    private fun enableCubeMapSeamless(enable: Boolean) {
        if (enable) {
            gl20.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS)
        } else {
            gl20.glDisable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS)
        }
    }

    private fun updateFramebufferInfo() {
        GLFW.glfwGetFramebufferSize(windowHandle, tmpBuffer, tmpBuffer2)
        backBufferWidth = tmpBuffer[0]
        backBufferHeight = tmpBuffer2[0]
        GLFW.glfwGetWindowSize(windowHandle, tmpBuffer, tmpBuffer2)
        logicalWidth = tmpBuffer[0]
        logicalHeight = tmpBuffer2[0]
        bufferFormat = BufferFormat(8, 8, 8, 8, 8, 0, 0, false)
    }

    override fun isGL30Available() = gl30 != null

    override fun getGL20(): GL20 = gl20

    override fun getGL30(): GL30? = gl30

    override fun setGL20(gl20: GL20) {
        this.gl20 = gl20
    }

    override fun setGL30(gl30: GL30) {
        this.gl30 = gl30
    }

    override fun getWidth(): Int = logicalWidth

    override fun getHeight(): Int = logicalHeight

    override fun getBackBufferWidth(): Int = backBufferWidth

    override fun getBackBufferHeight(): Int = backBufferHeight

    override fun getSafeInsetLeft(): Int = 0

    override fun getSafeInsetTop(): Int = 0

    override fun getSafeInsetBottom(): Int = 0

    override fun getSafeInsetRight(): Int = 0

    override fun getFrameId(): Long = frameId

    override fun getDeltaTime(): Float = deltaTime

    override fun getFramesPerSecond(): Int = fps

    override fun getType(): GraphicsType = GraphicsType.LWJGL3

    override fun getGLVersion() = glVersion

    private fun initiateGL() {
        val versionString = gl20.glGetString(GL11.GL_VERSION)
        val vendorString = gl20.glGetString(GL11.GL_VENDOR)
        val rendererString = gl20.glGetString(GL11.GL_RENDERER)
        glVersion = GLVersion(
            Application.ApplicationType.Desktop,
            versionString,
            vendorString,
            rendererString
        )
    }

    override fun getPpiX(): Float {
        return ppcX * 2.54f
    }

    override fun getPpiY(): Float {
        return ppcY * 2.54f
    }

    override fun getPpcX(): Float {
        val monitor = monitor as Lwjgl3Graphics.Lwjgl3Monitor
        GLFW.glfwGetMonitorPhysicalSize(monitor.monitorHandle, tmpBuffer, tmpBuffer2)
        val sizeX = tmpBuffer[0]
        val mode = displayMode
        return mode.width / sizeX.toFloat() * 10
    }

    override fun getPpcY(): Float {
        val monitor = monitor as Lwjgl3Graphics.Lwjgl3Monitor
        GLFW.glfwGetMonitorPhysicalSize(monitor.monitorHandle, tmpBuffer, tmpBuffer2)
        val sizeY = tmpBuffer2[0]
        val mode = displayMode
        return mode.height / sizeY.toFloat() * 10
    }

    override fun supportsDisplayModeChange(): Boolean {
        return false
    }

    override fun getPrimaryMonitor(): Graphics.Monitor {
        return toLwjgl3Monitor(GLFW.glfwGetPrimaryMonitor())
    }

    override fun getMonitor(): Graphics.Monitor {
        return monitors.first()
    }

    override fun getMonitors(): Array<Graphics.Monitor> {
        val glfwMonitors = GLFW.glfwGetMonitors()
        val monitors = mutableListOf<Lwjgl3Monitor>()

        for (i in 0 until glfwMonitors!!.limit()) {
            monitors.add(toLwjgl3Monitor(glfwMonitors.get(i)))
        }

        return monitors.toTypedArray()
    }

    private fun toLwjgl3Monitor(glfwMonitor: Long): Lwjgl3Monitor {
        val tmp = BufferUtils.createIntBuffer(1)
        val tmp2 = BufferUtils.createIntBuffer(1)
        GLFW.glfwGetMonitorPos(glfwMonitor, tmp, tmp2)
        val virtualX = tmp[0]
        val virtualY = tmp2[0]
        val name = GLFW.glfwGetMonitorName(glfwMonitor)
        return Lwjgl3Monitor(glfwMonitor, virtualX, virtualY, name)
    }

    override fun getDisplayModes(): Array<Graphics.DisplayMode> {
        return Lwjgl3ApplicationConfiguration.getDisplayModes(monitor)
    }

    override fun getDisplayModes(monitor: Graphics.Monitor?): Array<Graphics.DisplayMode> {
        return Lwjgl3ApplicationConfiguration.getDisplayModes(monitor)
    }

    override fun getDisplayMode(): Graphics.DisplayMode {
        return Lwjgl3ApplicationConfiguration.getDisplayMode(monitor)
    }

    override fun getDisplayMode(monitor: Graphics.Monitor?): Graphics.DisplayMode {
        return Lwjgl3ApplicationConfiguration.getDisplayMode(monitor)
    }

    override fun setFullscreenMode(displayMode: Graphics.DisplayMode?): Boolean {
        return false
    }

    override fun setWindowedMode(width: Int, height: Int): Boolean {
        return true
    }

    override fun setTitle(title: String?) {
        GLFW.glfwSetWindowTitle(windowHandle, title ?: "")
    }

    override fun setUndecorated(undecorated: Boolean) {
        GLFW.glfwSetWindowAttrib(
            windowHandle,
            GLFW.GLFW_DECORATED,
            if (undecorated) GLFW.GLFW_FALSE else GLFW.GLFW_TRUE
        )
    }

    override fun setResizable(resizable: Boolean) {
        GLFW.glfwSetWindowAttrib(
            windowHandle, GLFW.GLFW_RESIZABLE,
            if (resizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
        )
    }

    override fun setVSync(vsync: Boolean) {
        GLFW.glfwSwapInterval(if (vsync) 1 else 0)
    }

    override fun setForegroundFPS(fps: Int) {}

    override fun getBufferFormat(): BufferFormat? = bufferFormat

    override fun supportsExtension(extension: String): Boolean {
        return GLFW.glfwExtensionSupported(extension)
    }

    override fun setContinuousRendering(isContinuous: Boolean) {
        this.isContinuous = isContinuous
    }

    override fun isContinuousRendering(): Boolean = isContinuous

    override fun requestRendering() {}

    override fun isFullscreen(): Boolean {
        return GLFW.glfwGetWindowMonitor(windowHandle) != 0L
    }

    override fun newCursor(pixmap: Pixmap?, xHotspot: Int, yHotspot: Int): Cursor {
        TODO("Not yet implemented")
    }

    override fun setCursor(cursor: Cursor?) {
    }

    override fun setSystemCursor(systemCursor: SystemCursor) {
        setSystemCursor(windowHandle, systemCursor)
    }

    private fun setSystemCursor(windowHandle: Long, cursor: SystemCursor) {
        if (cursor == SystemCursor.None) {
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
            return
        } else {
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            val handle: Long = when (cursor) {
                SystemCursor.Arrow -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
                }

                SystemCursor.Crosshair -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR)
                }

                SystemCursor.Hand -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
                }

                SystemCursor.HorizontalResize -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
                }

                SystemCursor.VerticalResize -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
                }

                SystemCursor.Ibeam -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
                }

                SystemCursor.NWSEResize -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR)
                }

                SystemCursor.NESWResize -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR)
                }

                SystemCursor.AllResize -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR)
                }

                SystemCursor.NotAllowed -> {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_NOT_ALLOWED_CURSOR)
                }

                else -> {
                    throw GdxRuntimeException("Unknown system cursor $cursor")
                }
            }
            if (handle == 0L) {
                return
            }
            GLFW.glfwSetCursor(windowHandle, handle)
        }
    }

    fun update() {
        val time = System.nanoTime()
        if (lastFrameTime == -1L) lastFrameTime = time
        if (resetDeltaTime) {
            resetDeltaTime = false
            deltaTime = 0f
        } else deltaTime = (time - lastFrameTime) / 1000000000.0f
        lastFrameTime = time
        if (time - frameCounterStart >= 1000000000) {
            fps = frames
            frames = 0
            frameCounterStart = time
        }
        frames++
        frameId++
    }
}

class Lwjgl3Monitor internal constructor(
    val monitorHandle: Long,
    virtualX: Int,
    virtualY: Int,
    name: String?
) :
    Graphics.Monitor(virtualX, virtualY, name)