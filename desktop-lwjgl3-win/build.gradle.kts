import io.github.fourlastor.construo.ConstruoPluginExtension
import io.github.fourlastor.construo.Target
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.github.fourlastor:construo:2.1.0")
        if (providers.gradleProperty("enableGraalNative").orElse("false").get() == "true") {
            classpath("org.graalvm.buildtools.native:org.graalvm.buildtools.native.gradle.plugin:0.9.28")
        }
    }
}

plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

apply(plugin = "io.github.fourlastor.construo")

val gdxVersion: String by project
val lwjgl3Version: String by project
val graalHelperVersion: String by project
val krenderVersion: String by project
val enableGraalNative = providers.gradleProperty("enableGraalNative").orElse("false")
val appName = extra["appName"] as String
val mainClassName = "com.pashkd.krender.lwjgl3.WinLwjgl3Launcher"

sourceSets {
    main {
        resources.srcDir(rootProject.file("assets"))
    }
}

tasks.named<ProcessResources>("processResources") {
    exclude("**/*.lck")
}

application {
    mainClass.set(mainClassName)
    applicationName = "$appName-win"
}

eclipse.project.name = "$appName-desktop-lwjgl3-win"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(11)
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine:backend-gdx"))
    implementation(project(":engine:tools"))
    implementation(project(":engine:scene-player"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    if (enableGraalNative.get() == "true") {
        implementation("io.github.berstanio:gdx-svmhelper-backend-lwjgl3:$graalHelperVersion")
    }

    constraints {
        implementation("org.lwjgl:lwjgl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-glfw:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-openal:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-opengl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-stb:$lwjgl3Version")
    }
}

val forwardedRouteProperties =
    listOf(
        "krender.scene",
        "krender.model.path",
        "krender.model",
        "krender.texture.atlas.path",
        "krender.scene.path",
        "krender.scene.name",
        "krender.terrain.path",
        "krender.ui.scene.path",
    )

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    forwardedRouteProperties.forEach { propertyName ->
        if (project.hasProperty(propertyName)) {
            jvmArgs("-D$propertyName=${project.property(propertyName)}")
        }
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("$appName-$krenderVersion-win.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude(
        "macos/arm64/**",
        "macos/x64/**",
        "linux/arm32/**",
        "linux/arm64/**",
        "linux/x64/**",
        "**/*.dylib",
        "**/*.so",
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/maven/**",
    )
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

tasks.register("jarWin") {
    group = "build"
    description = "Builds the Windows desktop launcher JAR."
    dependsOn(tasks.named("jar"))
}

tasks.register("dist") {
    group = "build"
    description = "Builds the Windows desktop distribution JAR using the legacy gdx-setup task name."
    dependsOn(tasks.named("jar"))
}

extensions.configure<ConstruoPluginExtension>("construo") {
    name.set(appName)
    humanName.set(appName)
    jlink {
        guessModulesFromJar.set(false)
        modules.addAll("java.base", "java.management", "java.desktop", "jdk.unsupported")
    }
    targets.register("winX64", Target.Windows::class.java) {
        architecture.set(Target.Architecture.X86_64)
        icon.set(rootProject.file("assets/logo/Krender_logo.ico"))
        jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.10_7.zip")
    }
}

if (enableGraalNative.get() == "true") {
    apply(from = file("nativeimage.gradle.kts"))
}
