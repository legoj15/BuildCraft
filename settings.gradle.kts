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
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    create(rootProject) {
        // One node per MC LINE (a real Java/mapping cliff). The 26.1.x line is a single node
        // compiled against 26.1.2; within-line patch differences (26.1 / 26.1.1 / 26.1.2) are
        // absorbed at runtime — reflection + common APIs (see lib.misc.BreakEventCompat) — so ONE
        // jar covers all of 26.1.x. The 1.21.11 line (Java 21, pre-CalVer) is a second node;
        // cross-cliff differences (chiefly the 26.1 GUI-render refactor) use //? if directives.
        versions("26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.1")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "BuildCraft"
