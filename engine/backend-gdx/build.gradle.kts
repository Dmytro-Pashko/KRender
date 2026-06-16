val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val gdxVersion: String by project
val gltfVersion: String by project
val graalHelperVersion: String by project
val enableGraalNative = providers.gradleProperty("enableGraalNative").orElse("false")

dependencies {
    implementation(project(":core"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.mgsx-dev.gdx-gltf:gltf:$gltfVersion")

    implementation("com.github.kotlin-graphics.imgui:imgui-core:1.89.7-1")
    implementation("com.github.kotlin-graphics.imgui:imgui-gl:1.89.7-1")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    if (enableGraalNative.get() == "true") {
        implementation("io.github.berstanio:gdx-svmhelper-annotations:$graalHelperVersion")
    }
}
