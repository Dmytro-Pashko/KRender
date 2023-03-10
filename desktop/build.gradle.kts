import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    // Plugin for Jar files creation.
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("java")
    id("kotlin")
}

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
    srcDir("../assets")
}

/**
 * Copy resources into build dir for including into final jar file.
 */
val prepareResourceTask = tasks.register("prepareResources", Copy::class.java) {
    from(File(rootProject.projectDir.path + "/assets"))
    into(File(project.buildDir.path + "/tmp/res/assets"))
}.get()

val prepareJarTask: ShadowJar = tasks.withType<ShadowJar> {
    configurations.add(project.configurations["compileClasspath"])
    from(File(project.buildDir.path + "/tmp/res/"))

    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("debug")
    archiveVersion.set(rootProject.version.toString())

    manifest {
        attributes["Main-Class"] = "com.dpashko.vektor.DesktopLauncher"
    }
}.first()

prepareJarTask.dependsOn(prepareResourceTask)

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.11.0")
    implementation("com.badlogicgames.gdx:gdx-platform:1.11.0:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.11.0:natives-desktop")
}
