plugins {
    id("java")
    id("kotlin")
    id("kotlin-kapt")
    id("org.jetbrains.compose")
}

kotlin {
    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sourceSets {
    getByName("main") {
        java.srcDir("src/")
    }
}

dependencies {
    api("com.badlogicgames.gdx:gdx:1.12.0")
    api("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    api("com.github.mgsx-dev.gdx-gltf:gltf:2.1.0")

    implementation(compose.desktop.common)
    // DI
    api("com.google.dagger:dagger:2.47")
    kapt("com.google.dagger:dagger-compiler:2.47")
}
