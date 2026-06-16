import io.github.fourlastor.construo.ConstruoPluginExtension
import io.github.fourlastor.construo.Target
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Locale

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
}

apply(plugin = "io.github.fourlastor.construo")
apply(plugin = "org.jetbrains.kotlin.jvm")

val gdxVersion: String by project
val lwjgl3Version: String by project
val graalHelperVersion: String by project
val projectVersion: String by project
val enableGraalNative = providers.gradleProperty("enableGraalNative").orElse("false")
val appName = extra["appName"] as String

sourceSets {
    main {
        resources.srcDir(rootProject.file("assets"))
    }
}

tasks.named<ProcessResources>("processResources") {
    exclude("**/*.lck")
}

application {
    mainClass.set("com.pashkd.krender.lwjgl3.Lwjgl3Launcher")
    applicationName = appName
}

eclipse.project.name = "$appName-desktop-lwjgl3"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.named<JavaCompile>("compileJava") {
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(11)
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    // Desktop host composes reusable backend, tools, and scene-player route modules into one runnable SDK app.
    implementation(project(":core"))
    implementation(project(":engine:backend-gdx"))
    implementation(project(":engine:tools"))
    implementation(project(":engine:scene-player"))

    if (enableGraalNative.get() == "true") {
        implementation("io.github.berstanio:gdx-svmhelper-backend-lwjgl3:$graalHelperVersion")
    }

    // Pin LWJGL modules so the desktop launcher keeps Java 25+ compatibility even through transitive LibGDX versions.
    constraints {
        implementation("org.lwjgl:lwjgl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-glfw:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-openal:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-opengl:$lwjgl3Version")
        implementation("org.lwjgl:lwjgl-stb:$lwjgl3Version")
    }
}

val os = System.getProperty("os.name").lowercase(Locale.ROOT)
val forwardedRouteProperties =
    listOf(
        "krender.scene",
        "krender.model.path",
        "krender.model",
        "krender.scene.path",
        "krender.scene.name",
        "krender.terrain.path",
        "krender.ui.scene.path",
    )

// Launches from the shared assets directory and forwards route properties so Gradle runs can open specific assets/scenes.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    // LWJGL and ImGui use native access on recent JDKs; this keeps Java 24+ runs warning-free and functional.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    forwardedRouteProperties.forEach { propertyName ->
        if (project.hasProperty(propertyName)) {
            jvmArgs("-D$propertyName=${project.property(propertyName)}")
        }
    }
    if (os.contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("$appName-$projectVersion.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/maven/**")
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

// Builds a macOS-only runnable JAR by removing Windows and Linux native libraries from the standard desktop archive.
tasks.register("jarMac") {
    group = "build"
    description = "Builds a macOS-only desktop JAR with non-macOS native libraries removed."
    dependsOn("jar")
    tasks.named<Jar>("jar") {
        archiveFileName.set("$appName-$projectVersion-mac.jar")
        exclude(
            "windows/x86/**",
            "windows/x64/**",
            "linux/arm32/**",
            "linux/arm64/**",
            "linux/x64/**",
            "**/*.dll",
            "**/*.so",
            "META-INF/INDEX.LIST",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/maven/**",
        )
    }
}

// Builds a Linux-only runnable JAR by removing Windows and macOS native libraries from the standard desktop archive.
tasks.register("jarLinux") {
    group = "build"
    description = "Builds a Linux-only desktop JAR with non-Linux native libraries removed."
    dependsOn("jar")
    tasks.named<Jar>("jar") {
        archiveFileName.set("$appName-$projectVersion-linux.jar")
        exclude(
            "windows/x86/**",
            "windows/x64/**",
            "macos/arm64/**",
            "macos/x64/**",
            "**/*.dll",
            "**/*.dylib",
            "META-INF/INDEX.LIST",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/maven/**",
        )
    }
}

// Builds a Windows-only runnable JAR by removing macOS and Linux native libraries from the standard desktop archive.
tasks.register("jarWin") {
    group = "build"
    description = "Builds a Windows-only desktop JAR with non-Windows native libraries removed."
    dependsOn("jar")
    tasks.named<Jar>("jar") {
        archiveFileName.set("$appName-$projectVersion-win.jar")
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
    }
}

extensions.configure<ConstruoPluginExtension>("construo") {
    name.set(appName)
    humanName.set(appName)
    jlink {
        guessModulesFromJar.set(false)
        modules.addAll("java.base", "java.management", "java.desktop", "jdk.unsupported")
    }

    targets.register("linuxX64", Target.Linux::class.java) { target ->
        target.architecture.set(Target.Architecture.X86_64)
        target.jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.10_7.tar.gz")
    }
    targets.register("macM1", Target.MacOs::class.java) { target ->
        target.architecture.set(Target.Architecture.AARCH64)
        target.jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.10_7.tar.gz")
        target.identifier.set("com.pashkd.krender.$appName")
        target.macIcon.set(project.file("icons/logo.icns"))
    }
    targets.register("macX64", Target.MacOs::class.java) { target ->
        target.architecture.set(Target.Architecture.X86_64)
        target.jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_mac_hotspot_21.0.10_7.tar.gz")
        target.identifier.set("com.pashkd.krender.$appName")
        target.macIcon.set(project.file("icons/logo.icns"))
    }
    targets.register("winX64", Target.Windows::class.java) { target ->
        target.architecture.set(Target.Architecture.X86_64)
        target.icon.set(project.file("icons/logo.png"))
        target.jdkUrl.set("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.10%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.10_7.zip")
    }
}

// Keeps the historical gdx-setup task name as an alias for the runnable desktop JAR.
tasks.register("dist") {
    group = "build"
    description = "Builds the desktop distribution JAR using the legacy gdx-setup task name."
    dependsOn("jar")
}

distributions {
    main {
        contents {
            into("libs") {
                configurations.runtimeClasspath.get().files
                    .filter { it.name != tasks.named<Jar>("jar").get().archiveFile.get().asFile.name }
                    .forEach { exclude(it.name) }
            }
        }
    }
}

tasks.named("startScripts") {
    dependsOn(":desktop-lwjgl3:jar")
}
tasks.named<CreateStartScripts>("startScripts") {
    classpath = tasks.named<Jar>("jar").get().outputs.files
}

if (enableGraalNative.get() == "true") {
    apply(from = file("nativeimage.gradle.kts"))
}
