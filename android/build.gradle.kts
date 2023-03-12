import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("android")
    id("com.android.application")
    id("kotlin-kapt")
}

val assetsDir = rootProject.file("assets")

android {

    buildToolsVersion = "30.0.3"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.dpashko.krender"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        get("debug").apply {
            isMinifyEnabled = false
        }
    }

    kotlin {
        java {
            sourceCompatibility = VERSION_1_8
            targetCompatibility = VERSION_1_8
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs(assetsDir)
            jniLibs.srcDirs("libs")
        }
    }

    // Called every time gradle gets executed, takes the native dependencies of
    // the natives configuration, and extracts them to the proper libs/ folders
    // so they get packed with the APK.
    val copyAndroidNatives by tasks.register("copyAndroidNatives") {
        doFirst {
            file("libs/armeabi-v7a/").mkdirs()
            file("libs/arm64-v8a/").mkdirs()
            file("libs/x86_64/").mkdirs()
            file("libs/x86/").mkdirs()

            configurations.getByName("natives").copy().files.forEach { jar ->
                val outputDir: File? = when {
                    jar.name.endsWith("natives-arm64-v8a.jar") -> file("libs/arm64-v8a")
                    jar.name.endsWith("natives-armeabi-v7a.jar") -> file("libs/armeabi-v7a")
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

    tasks.matching { it.name.contains("assemble") }
        .configureEach {
            dependsOn("copyAndroidNatives")
        }
}

configurations {
    create("natives")
}

dependencies {
    api(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

    add("natives", "com.badlogicgames.gdx:gdx-platform:1.11.0:natives-armeabi-v7a")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.11.0:natives-arm64-v8a")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.11.0:natives-x86")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.11.0:natives-x86_64")
}
