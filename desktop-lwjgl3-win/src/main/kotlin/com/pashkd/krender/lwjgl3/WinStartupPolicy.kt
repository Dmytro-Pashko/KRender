package com.pashkd.krender.lwjgl3

import com.badlogic.gdx.Version
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader

object WinStartupPolicy {
    fun prepare() {
        val programData = System.getenv("ProgramData") ?: "C:\\Temp"
        val previousTmpDir = System.getProperty("java.io.tmpdir", programData)
        val previousUser = System.getProperty("user.name", "libGDX_User")
        System.setProperty("java.io.tmpdir", "$programData\\libGDX-temp")
        System.setProperty(
            "user.name",
            "User_${previousUser.hashCode()}_GDX${Version.VERSION}".replace('.', '_'),
        )
        Lwjgl3NativesLoader.load()
        System.setProperty("java.io.tmpdir", previousTmpDir)
        System.setProperty("user.name", previousUser)
    }
}
