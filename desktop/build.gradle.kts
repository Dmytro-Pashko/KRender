import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    // Plugin for Jar files creation.
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("java")
    id("kotlin")
    id("org.jetbrains.compose")
}

val assetsDir = rootProject.file("assets")

version = 1.0

kotlin {
    java {
        sourceCompatibility = VERSION_17
        targetCompatibility = VERSION_17
    }
}

sourceSets {
    getByName("main") {
        java.srcDir("src/")
        resources.srcDir(assetsDir)
    }
}

tasks {
    // run ./gradle :desktop:shadowJar task in order to assemble executable Jar file
    // in build/libs/ directory.
    withType<ShadowJar> {
        configurations.add(project.configurations.getAt("compileClasspath"))
        from(assetsDir)
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(project.version.toString())

        manifest {
            attributes["Main-Class"] = "com.dpashko.krender.DesktopLauncher"
        }
        doLast {
            println(
                "Jar file successfully assembled.\n${this@withType.archiveFile.get().asFile}"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.0")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.0:natives-desktop")
    implementation(compose.desktop.currentOs)
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
}
