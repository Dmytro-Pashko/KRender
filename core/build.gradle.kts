plugins {
    jacoco
}

val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project

val coverageExcludes =
    listOf(
        "**/generated/**",
        "**/*Test.*",
        "**/Main.*",
        "**/*Launcher.*",
    )

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(coverageExcludes)
            },
            fileTree(layout.buildDirectory.dir("classes/java/main")) {
                exclude(coverageExcludes)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    additionalSourceDirs.setFrom(files("src/main/kotlin", "src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/test.exec")
        },
    )

    reports {
        html.required.set(true)
        html.outputLocation.set(rootProject.layout.buildDirectory.dir("reports/coverage/unit/html"))
        xml.required.set(true)
        xml.outputLocation.set(rootProject.layout.buildDirectory.file("reports/coverage/unit/jacoco.xml"))
        csv.required.set(true)
        csv.outputLocation.set(rootProject.layout.buildDirectory.file("reports/coverage/unit/jacoco.csv"))
    }

    onlyIf {
        executionData.files.any { it.exists() }
    }
}
