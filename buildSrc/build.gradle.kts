// Build-script helpers and the per-loader convention plugins shared by every Stonecutter node's central
// build.gradle.kts. Gradle compiles this build before the main one.
// - Helpers (buildcraft.gradle.*): call sites do `import buildcraft.gradle.DataDownport1211`. They live HERE
//   rather than as build-script functions because Gradle's configuration cache cannot serialize script-object
//   references captured by task actions (doLast blocks), while a buildSrc class is a plain loadable call target.
// - Convention plugins (src/main/kotlin/bc.<loader>.gradle.kts): build.gradle.kts applies `bc.$loader`, so a
//   NeoForge node gets ModDevGradle, a Fabric node gets Loom and a Forge node gets ForgeGradle, never two on one node
//   (docs/platform-layer.md).
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases")
    // Loom and its own libraries (tiny-remapper, mapping-io, ...).
    maven("https://maven.fabricmc.net/")
    // ModDevGradle's gradle-idea-ext dependency is published only to the plugin portal.
    gradlePluginPortal()
    // ForgeGradle 7 and the libraries it publishes beside itself (the mavenizer, ...).
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    // The loader build plugins. Their versions live HERE, not in a plugins {} block: a plugin already on the
    // buildSrc classpath must be applied without a version, so the convention plugins say only `id(...)`.
    // ModDevGradle (NeoForge nodes).
    implementation("net.neoforged:moddev-gradle:2.0.147")
    // Loom (Fabric nodes). 1.19.x needs Gradle 9.8 (the wrapper's version, and what Fabric's own 26.3 template uses).
    implementation("net.fabricmc:fabric-loom:1.19.3")
    // ForgeGradle 7 (MinecraftForge, NOT NeoForge: Forge nodes). The artifact behind the plugin id
    // net.minecraftforge.gradle, which the plugin marker net.minecraftforge.gradle:net.minecraftforge.gradle.gradle.plugin
    // points at; named directly because buildSrc applies plugins by id from its classpath, not through a marker lookup.
    implementation("net.minecraftforge:forgegradle:7.0.40")
}
