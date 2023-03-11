import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    kotlin("android")
    id("com.android.application")
    id("kotlin-kapt")
}

android {
    buildToolsVersion = "30.0.3"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.dpashko.vektor"
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

    sourceSets["main"].apply {
        java {
            srcDir("src/main/java")
        }
        assets {
            srcDir("../resources")
        }
        jniLibs {
            srcDir("libs")
        }
    }
}

dependencies {
    api(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
}
