pluginManagement {
    repositories {
        mavenLocal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.4"
}

stonecutter {
    create(rootProject) {
        // One node per MC line that shares this source tree. Node name == MC version,
        // which is what the //? if >=26.1.2 directives in src/ compare against.
        versions("26.1.1", "26.1.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "BuildCraft"
