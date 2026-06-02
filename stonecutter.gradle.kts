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

// ─── Production boot+connect matrix (PowerShell) ─────────────────────────────
// Registered on the ROOT so `gradlew fullTestSuite` runs the cross-version matrix exactly ONCE, even
// though fullTestSuite is per-node. The harness under testing/ is machine-specific and gitignored, so
// this stage SELF-SKIPS when the script is absent (e.g. a third-party clone) instead of failing.
val releaseMatrixScript = rootProject.file("testing/Invoke-ReleaseTests.ps1")
tasks.register<Exec>("runReleaseMatrix") {
    group = "verification"
    description = "Production boot+connect matrix (testing/Invoke-ReleaseTests.ps1, PowerShell 7). Skipped if the harness isn't checked out."
    workingDir = rootProject.projectDir
    commandLine("pwsh", "-NoProfile", "-File", releaseMatrixScript.absolutePath)
    onlyIf {
        val present = releaseMatrixScript.exists()
        if (!present) logger.lifecycle("runReleaseMatrix: testing/Invoke-ReleaseTests.ps1 not present — skipping the production boot+connect stage.")
        present
    }
}
