import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("android")
    id("com.android.application")
    id("kotlin-kapt")
    id("org.jetbrains.compose")
}

val assetsDir = rootProject.file("assets")

android {

    namespace = "com.dpashko.krender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dpashko.krender"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        get("debug").apply {
            isMinifyEnabled = false
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

}

configurations {
    create("natives")
}

dependencies {
    api(project(":core"))
    api(project(":gdx-backend-android"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")

    implementation("androidx.compose.material3:material3:1.1.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.7.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.appcompat:appcompat:1.6.1")

    add("natives", "com.badlogicgames.gdx:gdx-platform:1.12.0:natives-armeabi-v7a")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.12.0:natives-arm64-v8a")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.12.0:natives-x86")
    add("natives", "com.badlogicgames.gdx:gdx-platform:1.12.0:natives-x86_64")
}
