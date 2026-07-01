import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

val kotlinVersion: String by project
val detektVersion: String by project
val ktlintGradleVersion: String by project
val krenderVersion: String by project

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        google()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.property("kotlinVersion")}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${project.property("kotlinVersion")}")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${project.property("detektVersion")}")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:${project.property("ktlintGradleVersion")}")
    }
}

apply(plugin = "io.gitlab.arturbosch.detekt")
apply(plugin = "org.jlleitschuh.gradle.ktlint")

val staticAnalysisExcludes =
    listOf(
        "**/build/**",
        "**/.gradle/**",
        "**/generated/**",
    )

val detektSources =
    files(
        fileTree(rootDir) {
            include("core/src/**/*.kt")
            include("engine/**/src/**/*.kt")
            include("games/**/src/**/*.kt")
            include("desktop-lwjgl3-win/src/**/*.kt")
            include("desktop-lwjgl3-macos/src/**/*.kt")
            include("desktop-lwjgl3-linux/src/**/*.kt")
            include("apps/**/src/**/*.kt")
            include("android/src/**/*.kt")
            include("**/*.kts")
            staticAnalysisExcludes.forEach(::exclude)
        },
    )

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
}

allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")

    extensions.configure<IdeaModel>("idea") {
        module {
            outputDir = file("build/classes/java/main")
            testOutputDir = file("build/classes/java/test")
        }
    }
}

extensions.configure<KtlintExtension>("ktlint") {
    ignoreFailures.set(false)
    outputToConsole.set(true)
    filter {
        include("**/src/**/*.kt")
        include("**/*.kts")
        staticAnalysisExcludes.forEach(::exclude)
    }
    kotlinScriptAdditionalPaths {
        include(
            fileTree(rootDir) {
                include("**/*.kts")
                staticAnalysisExcludes.forEach(::exclude)
            },
        )
    }
}

extensions.configure<DetektExtension>("detekt") {
    toolVersion = detektVersion
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    basePath = rootDir.absolutePath
}

tasks.withType<Detekt>().configureEach {
    setSource(detektSources)
    include("**/*.kt", "**/*.kts")
    staticAnalysisExcludes.forEach(::exclude)
    reports {
        html.required.set(true)
        md.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        html.outputLocation.set(file("$rootDir/build/reports/detekt/$name.html"))
        md.outputLocation.set(file("$rootDir/build/reports/detekt/$name.md"))
        xml.outputLocation.set(file("$rootDir/build/reports/detekt/$name.xml"))
        sarif.outputLocation.set(file("$rootDir/build/reports/detekt/$name.sarif"))
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    setSource(detektSources)
    include("**/*.kt", "**/*.kts")
    staticAnalysisExcludes.forEach(::exclude)
    baseline.set(file("$rootDir/config/detekt/baseline.xml"))
}

subprojects
    .filter { it.path != ":android" }
    .forEach { subproject ->
        subproject.apply(plugin = "java-library")
        subproject.apply(plugin = "org.jetbrains.kotlin.jvm")

        subproject.extensions.configure<JavaPluginExtension>("java") {
            sourceCompatibility = JavaVersion.VERSION_11
        }

        val generateAssetList =
            subproject.tasks.register("generateAssetList") {
                val assetsFolder = rootProject.file("assets")
                val assetsFile = assetsFolder.resolve("assets.txt")

                inputs.dir(assetsFolder)

                doLast {
                    if (assetsFile.exists()) {
                        assetsFile.delete()
                    }
                    val assetPaths =
                        assetsFolder
                            .walkTopDown()
                            .filter { it.isFile }
                            .map { file ->
                                val relativePath = assetsFolder.toPath().relativize(file.toPath())
                                relativePath.toString().replace('\\', '/')
                            }.sorted()

                    assetPaths.forEach { relativePath ->
                        assetsFile.appendText("$relativePath\n")
                    }
                }
            }

        subproject.tasks.named("processResources") {
            dependsOn(generateAssetList)
        }

        subproject.tasks.withType<JavaCompile>().configureEach {
            options.isIncremental = true
        }

        subproject.tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        }
    }

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    version = krenderVersion
    extensions.extraProperties["appName"] = "KRender"
    extensions.extraProperties["krenderVersion"] = krenderVersion

    repositories {
        mavenCentral()
        mavenLocal()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
        maven(url = "https://raw.githubusercontent.com/kotlin-graphics/mary/master")
        maven(url = "https://jitpack.io")
    }

    extensions.configure<KtlintExtension>("ktlint") {
        ignoreFailures.set(false)
        outputToConsole.set(true)
        filter {
            include("**/src/**/*.kt")
            include("**/*.kts")
            staticAnalysisExcludes.forEach(::exclude)
        }
    }
}

extensions.configure<EclipseModel>("eclipse") {
    project {
        name = "KRender-parent"
    }
}

tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs KRender static analysis checks and generates reports."
    dependsOn(":ktlintCheck")
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" })
    dependsOn("detekt")
}

tasks.register("unitTestCoverageReport") {
    group = "verification"
    description = "Generates the core JVM unit test coverage report."
    dependsOn(":core:jacocoTestReport")
}
