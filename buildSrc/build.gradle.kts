// Build-script helpers and the per-loader convention plugins shared by every Stonecutter node's central
// build.gradle.kts. Gradle compiles this build before the main one.
// - Helpers (buildcraft.gradle.*): call sites do `import buildcraft.gradle.DataDownport1211`. They live HERE
//   rather than as build-script functions because Gradle's configuration cache cannot serialize script-object
//   references captured by task actions (doLast blocks), while a buildSrc class is a plain loadable call target.
// - Convention plugins (src/main/kotlin/bc.<loader>.gradle.kts): build.gradle.kts applies `bc.$loader`, so a
//   NeoForge node gets ModDevGradle and a Fabric node gets Loom, never both (docs/platform-layer.md).
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
}

dependencies {
    // The loader build plugins. Their versions live HERE, not in a plugins {} block: a plugin already on the
    // buildSrc classpath must be applied without a version, so the convention plugins say only `id(...)`.
    // ModDevGradle (NeoForge nodes).
    implementation("net.neoforged:moddev-gradle:2.0.147")
    // Loom (Fabric nodes). 1.15.5 is the newest Loom whose Gradle API floor (9.2.0) the wrapper (9.3.1) meets:
    // 1.16.x needs 9.4, 1.17.x 9.5, 1.18.x 9.7 and 1.19.x 9.8 (the version Fabric's own 26.3 template uses).
    implementation("net.fabricmc:fabric-loom:1.15.5")
}
