val kotlinVersion: String by project
val gdxVersion: String by project
val lwjgl3Version: String by project

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    // Temporary editor-preview adapter dependency. Keep GDX usage limited to BackendBoundaryTest allowlisted files.
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.kotlin-graphics.imgui:imgui-core:1.89.7-1")
    implementation("com.twelvemonkeys.imageio:imageio-hdr:3.13.1")
    implementation("org.lwjgl:lwjgl:$lwjgl3Version")
    implementation("org.lwjgl:lwjgl-tinyexr:$lwjgl3Version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl3Version:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl3Version:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl3Version:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl:$lwjgl3Version:natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-tinyexr:$lwjgl3Version:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-tinyexr:$lwjgl3Version:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-tinyexr:$lwjgl3Version:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-tinyexr:$lwjgl3Version:natives-macos-arm64")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
