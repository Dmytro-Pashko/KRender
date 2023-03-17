import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
    id("java")
    id("kotlin")
    id("kotlin-kapt")
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
    api("com.badlogicgames.gdx:gdx:1.11.0")
    api("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    // DI
    api("com.google.dagger:dagger:2.44.2")
    kapt("com.google.dagger:dagger-compiler:2.44.2")
}
