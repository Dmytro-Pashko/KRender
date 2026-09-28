val kotlinVersion: String by project

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
