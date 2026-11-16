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
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // One node per MC LINE (a real Java/mapping cliff). The 26.1.x line is a single node
        // compiled against 26.1.2; within-line patch differences (26.1 / 26.1.1 / 26.1.2) are
        // absorbed at runtime — reflection + common APIs (see platform.neoforge.NeoForgeBreakEvent) — so ONE
        // jar covers all of 26.1.x. The 1.21.11 line (Java 21, pre-CalVer) is a second node;
        // cross-cliff differences (chiefly the 26.1 GUI-render refactor) use //? if directives.
        // 26.3 is the newest line and the primary (from its first release; it supplants 26.2).
        versions("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.1")
        // Fabric nodes are `<mc>-fabric`: the project name (and versions/<name>/) carries the suffix, the second
        // argument is the Minecraft version stonecutter.current.version reports. The loader itself comes from that
        // node's `loader=fabric`, never from the name. 26.3-fabric builds all of BuildCraft and its own jar
        // (#98; see buildSrc bc.fabric.gradle.kts and docs/platform-layer.md).
        version("26.3-fabric", "26.3")
        version("26.2-fabric", "26.2")
        version("26.1.2-fabric", "26.1.2")
        version("1.21.11-fabric", "1.21.11")
        version("1.21.10-fabric", "1.21.10")
        version("1.21.1-fabric", "1.21.1")
        // Forge nodes are `<mc>-forge`, named like the Fabric ones: MinecraftForge, NOT NeoForge (#209). 26.3-forge is the
        // port in progress: it builds all of src/main/java bar the recipe-viewer plugins into a jar, boots a dedicated server
        // and runs the shared game tests, and ships nothing yet (see buildSrc bc.forge.gradle.kts).
        version("26.3-forge", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "BuildCraft"
