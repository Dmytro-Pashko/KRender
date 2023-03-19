import org.gradle.api.JavaVersion.VERSION_1_8

plugins {
	id("java")
	id("kotlin")
}


version = 1.0

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
	api("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.11.0")
	api("com.badlogicgames.gdx:gdx-platform:1.11.0:natives-desktop")
}


