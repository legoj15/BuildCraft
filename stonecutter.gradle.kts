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
// Registered on the ROOT — it's a cross-version stage, so it runs exactly ONCE (fullTestSuite, also a
// root task, depends on it directly). The harness under testing/ is machine-specific and gitignored, so
// this stage SELF-SKIPS when the script is absent (e.g. a third-party clone) instead of failing.
val releaseMatrixScript = rootProject.file("testing/Invoke-ReleaseTests.ps1")
tasks.register<Exec>("runReleaseMatrix") {
    group = "verification"
    description = "Production boot+connect matrix (testing/Invoke-ReleaseTests.ps1, PowerShell 7). Skipped if the harness isn't checked out."
    workingDir = rootProject.projectDir
    // -SkipBuild: fullTestSuite already ran buildAndCollect (fresh +mc jars), so the harness must NOT
    // start a nested `gradlew buildAndCollect` inside this running build (it would deadlock on the
    // project lock). The harness still wipes each server's mods/ and copies the fresh jar every run.
    // (Run the script directly — `Invoke-ReleaseTests.ps1` without -SkipBuild — for a standalone rebuild.)
    commandLine("pwsh", "-NoProfile", "-File", releaseMatrixScript.absolutePath, "-SkipBuild")
    onlyIf {
        val present = releaseMatrixScript.exists()
        if (!present) logger.lifecycle("runReleaseMatrix: testing/Invoke-ReleaseTests.ps1 not present — skipping the production boot+connect stage.")
        present
    }
}

// ─── Full pre-release verification — EVERY node ──────────────────────────────
// The single "test the whole release" entry point: `./gradlew fullTestSuite`. Defined on the ROOT and
// registered EXPLICITLY against every Stonecutter node (enumerated from the tree via `subprojects` —
// each version is a subproject: `:1.21.1`, `:26.1.2`, …), so it can't silently shrink to one line the
// way an unqualified subproject-name cascade can. That implicit cascade is exactly how the 1.21.1
// game-test crash went unnoticed — a `:<node>:` qualifier (or a broken node) quietly dropped coverage.
// Here every node's unit tests + headless game tests + jar build are hard dependencies; drop a node
// only by removing it from the tree.
//
// No active-node switching is needed: each node compiles its OWN stonecutterGenerate output, so
// `:1.21.1:test` and `:26.2:test` are correct in one invocation regardless of which node is "active"
// (see build.gradle.kts, the stonecutter{} replacements comment). Task PATH STRINGS keep the wiring
// lazy — subprojects aren't forced to configure at root-eval time.
//
// buildAndCollect produces the fresh +mc jars the boot/connect matrix stages; it's a dependency here so
// the OUTER Gradle builds them and the matrix can run with -SkipBuild (a nested buildAndCollect would
// deadlock on the project lock). The matrix itself auto-skips when the gitignored testing/ harness is
// absent, so third-party clones still get all nodes' unit + game tests.
val perNodeVerification = subprojects.flatMap { node ->
    listOf("${node.path}:test", "${node.path}:runGameTestServer", "${node.path}:buildAndCollect")
}
tasks.register("fullTestSuite") {
    group = "verification"
    description = "ALL nodes: unit tests + headless game tests + jar build, then the production boot/connect matrix (matrix auto-skips if the testing/ harness is absent)."
    dependsOn(perNodeVerification)
    dependsOn(tasks.named("runReleaseMatrix"))
}
// The long production matrix runs LAST — after every node's tests pass and every fresh jar is built.
tasks.named("runReleaseMatrix").configure {
    mustRunAfter(perNodeVerification)
}
