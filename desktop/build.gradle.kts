import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    // Plugin for Jar files creation.
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("java")
    id("kotlin")
}

val assetsDir = rootProject.file("assets")

kotlin {
    java {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }
}

java.sourceSets["main"].java {
    srcDir("src/")
}
java.sourceSets["main"].resources {
    srcDir(assetsDir)
}

tasks {
    val tmpResDir = project.file("$buildDir/tmp/res/")

    register("prepareAssets", Copy::class.java) {
        from(assetsDir)
        into(project.file("$tmpResDir/assets"))

        doLast {
            println("Resource preparation complete.")
        }
    }

    // run ./gradle :desktop:shadowJar task in order to assemble executable Jar file
    // in build/libs/ directory.
    withType<ShadowJar> {
        configurations.add(project.configurations.getAt("compileClasspath"))
        from(project.file("$tmpResDir"))

        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("debug")
        archiveVersion.set(rootProject.version.toString())

        manifest {
            attributes["Main-Class"] = "com.dpashko.krender.DesktopLauncher"
        }
        doLast {
            println(
                "Jar file successfully assembled. ${this@withType.archiveFile.get().asFile}"
            )
        }
        dependsOn("prepareAssets")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.11.0")
    implementation("com.badlogicgames.gdx:gdx-platform:1.11.0:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.11.0:natives-desktop")
}
