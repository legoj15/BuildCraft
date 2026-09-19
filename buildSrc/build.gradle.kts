// Build-script helpers shared by every Stonecutter node's central build.gradle.kts. Gradle
// compiles this build before the main one; call sites do `import buildcraft.gradle.DataDownport1211`.
// Helpers live HERE rather than as build-script functions because Gradle's configuration cache
// cannot serialize script-object references captured by task actions (doLast blocks), while a
// buildSrc class is a plain loadable call target.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}
