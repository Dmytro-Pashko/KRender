import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    id("java")
    id("kotlin")
    id("kotlin-kapt")
    id("org.jetbrains.compose") version "1.4.1"
}

kotlin {
    java {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }
}

sourceSets {
    getByName("main") {
        java.srcDir("src/")
    }
}

dependencies {
    api("com.badlogicgames.gdx:gdx:1.12.0")
    api("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    // DI
    api("com.google.dagger:dagger:2.47")
    kapt("com.google.dagger:dagger-compiler:2.47")

    implementation(compose.desktop.currentOs)
}
