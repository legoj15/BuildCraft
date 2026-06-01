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
        // One node per MC LINE (a real Java/mapping cliff). The 26.1.x line is a single node
        // compiled against 26.1.2; within-line patch differences (26.1 / 26.1.1 / 26.1.2) are
        // absorbed at runtime — reflection + common APIs (see lib.misc.BreakEventCompat) — so ONE
        // jar covers all of 26.1.x. A future line like 1.21.11 (Java 21) would be a second node
        // using //? if directives for the cross-cliff differences.
        versions("26.1.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "BuildCraft"
