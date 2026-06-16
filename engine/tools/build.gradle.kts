val kotlinVersion: String by project
val gdxVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    // Temporary editor-preview adapter dependency. Keep GDX usage limited to BackendBoundaryTest allowlisted files.
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.github.kotlin-graphics.imgui:imgui-core:1.89.7-1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
