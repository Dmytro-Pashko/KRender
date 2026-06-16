import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

val gdxVersion: String by project
val lwjgl3Version: String by project

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

application {
    mainClass.set("com.pashkd.krender.woolboy.app.WoolboyDesktopLauncher")
    applicationName = "woolboy-demo"
}

sourceSets {
    main {
        resources {
            srcDir(project(":games:woolboy").file("src/main/resources"))
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("assets")) {
        include("shaders/**")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine:backend-gdx"))
    implementation(project(":games:woolboy"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    // Pin LWJGL modules so the standalone app inherits the same Java 25+ compatibility as the SDK desktop host.
    constraints {
        implementation("org.lwjgl:lwjgl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-glfw:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-openal:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-opengl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-stb:$lwjgl3Version")
    }
}

// Builds a standalone Woolboy desktop JAR with game assets and shared shaders bundled into the archive.
tasks.register<Jar>("woolboyJar") {
    group = "build"
    description = "Builds the standalone Woolboy demo executable JAR with bundled game assets."
    archiveFileName.set("woolboy-demo.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.named("classes"))
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Enable-Native-Access" to "ALL-UNNAMED",
            "Multi-Release" to "true",
        )
    }
    doLast {
        archiveFile.get().asFile.setExecutable(true, false)
    }
}

tasks.named("build") {
    dependsOn(tasks.named("woolboyJar"))
}
