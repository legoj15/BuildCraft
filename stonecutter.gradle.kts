// ─── Stonecutter controller (root project build script) ─────────────────────
// This is the Tree controller, NOT the per-node build. The actual mod build lives
// in build.gradle.kts (the centralScript), which runs once per version node.
//
// moddev is declared here with `apply false` so its version is known to every node
// subproject; each node applies it (version-less) in build.gradle.kts.
plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.141" apply false
}

// The active node — what the IDE/runClient sees and what `compileJava` builds.
// Stonecutter's "Set active project to ..." task rewrites this line.
stonecutter active "26.1.2" /* [SC] DO NOT EDIT */
