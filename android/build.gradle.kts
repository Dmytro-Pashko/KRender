import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
        google()
    }
}

apply(plugin = "com.android.application")
apply(plugin = "kotlin-android")

val gdxVersion: String by project
val krenderVersion: String by project
val androidAssetsDir =
    layout.buildDirectory
        .dir("generated/filtered-android-assets")
        .get()
        .asFile
val natives by configurations.creating

// Filters desktop/editor-only files out of the shared assets tree before Android packaging.
val prepareAndroidAssets =
    tasks.register<Sync>("prepareAndroidAssets") {
        group = "build"
        description = "Copies shared runtime assets into a filtered Android assets directory."
        from("../assets") {
            exclude("blender_sources/**", "logs/**", "imgui.log*")
        }
        into(androidAssetsDir)
    }

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.pashkd.krender"
    compileSdk = 35
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
            aidl.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
            renderscript.setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf(androidAssetsDir))
            jniLibs.setSrcDirs(listOf("libs"))
        }
    }
    androidResources {
        noCompress.addAll(listOf("glb", "gltf", "bin", "png", "jpg", "jpeg", "ktx", "ktx2", "json", "krscene"))
    }
    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/robovm/ios/robovm.xml",
                    "META-INF/DEPENDENCIES.txt",
                    "META-INF/DEPENDENCIES",
                    "META-INF/dependencies.txt",
                    "**/*.gwt.xml",
                    "META-INF/linux/**",
                    "META-INF/macos/**",
                    "META-INF/windows/**",
                ),
            )
            pickFirsts.addAll(
                listOf(
                    "META-INF/LICENSE.txt",
                    "META-INF/LICENSE",
                    "META-INF/license.txt",
                    "META-INF/LGPL2.1",
                    "META-INF/NOTICE.txt",
                    "META-INF/NOTICE",
                    "META-INF/notice.txt",
                ),
            )
        }
    }
    defaultConfig {
        applicationId = "com.pashkd.krender"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = krenderVersion
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
}

extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

repositories {
    google()
}

dependencies {
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.5")
    add("implementation", "com.badlogicgames.gdx:gdx:$gdxVersion")
    add("implementation", "com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    add("implementation", project(":core")) {
        exclude(group = "com.github.kotlin-graphics.imgui")
        exclude(group = "org.lwjgl")
    }
    add("implementation", project(":engine:backend-gdx")) {
        exclude(group = "com.github.kotlin-graphics.imgui")
        exclude(group = "org.lwjgl")
    }
    add("implementation", project(":engine:scene-player")) {
        exclude(group = "com.github.kotlin-graphics.imgui")
        exclude(group = "org.lwjgl")
    }

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
}

// Extracts LibGDX native libraries into Android jniLibs so the APK can load platform-specific binaries.
tasks.register("copyAndroidNatives") {
    group = "build"
    description = "Extracts LibGDX Android native libraries into jniLibs."
    doFirst {
        file("libs/armeabi-v7a/").mkdirs()
        file("libs/arm64-v8a/").mkdirs()
        file("libs/x86_64/").mkdirs()
        file("libs/x86/").mkdirs()

        natives.copy().files.forEach { jar ->
            val outputDir =
                when {
                    jar.name.endsWith("natives-armeabi-v7a.jar") -> file("libs/armeabi-v7a")
                    jar.name.endsWith("natives-arm64-v8a.jar") -> file("libs/arm64-v8a")
                    jar.name.endsWith("natives-x86_64.jar") -> file("libs/x86_64")
                    jar.name.endsWith("natives-x86.jar") -> file("libs/x86")
                    else -> null
                }
            if (outputDir != null) {
                copy {
                    from(zipTree(jar))
                    into(outputDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareAndroidAssets)
}

tasks.matching { it.name.lowercase().contains("lint") }.configureEach {
    dependsOn(prepareAndroidAssets)
}

// Starts the installed Android app through adb, resolving the SDK from local.properties or ANDROID_SDK_ROOT.
tasks.register<Exec>("run") {
    group = "application"
    description = "Launches the installed Android application on a connected device or emulator."
    val sdkRoot =
        project.file("../local.properties").takeIf { it.exists() }?.let { localProperties ->
            val properties = Properties()
            localProperties.inputStream().use { properties.load(it) }
            properties.getProperty("sdk.dir")
        } ?: System.getenv("ANDROID_SDK_ROOT")

    commandLine(
        "$sdkRoot/platform-tools/adb",
        "shell",
        "am",
        "start",
        "-n",
        "com.pashkd.krender/com.pashkd.krender.android.AndroidLauncher",
    )
}

eclipse.project.name = "${extra["appName"]}-android"
