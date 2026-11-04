// The NeoForge half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose
// `loader` is neoforge. Everything ModDevGradle-specific lives here (the neoForge {} block, the NeoForge-only
// dependencies, mods.toml expansion, FML-JUnit unit tests, game tests, the shipped jar and buildAndCollect);
// build.gradle.kts keeps only what every loader shares. ModDevGradle's version is pinned in buildSrc/build.gradle.kts.
import buildcraft.gradle.BcNode
import buildcraft.gradle.DataDownport1211
import buildcraft.gradle.DataDownportPre263

plugins {
    id("net.neoforged.moddev")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
val minecraftVersion = property("minecraft_version") as String
val neoVersion = property("neo_version") as String
val jeiVersion = property("jei_version") as String
val reiVersion = property("rei_version") as String
// Which recipe viewer the dev runs (client, server, game tests, unit tests) load: JEI by default,
// `-Precipe_viewer=rei` swaps in REI instead. Both are compiled against on every node; only one is
// ever on the runtime classpath, since the two would fight over the same keys and screen space.
val recipeViewer = (findProperty("recipe_viewer") as String?)?.lowercase() ?: "jei"
if (recipeViewer != "jei" && recipeViewer != "rei") {
    throw GradleException("recipe_viewer must be 'jei' or 'rei', not '$recipeViewer'")
}
val modVersion = property("mod_version") as String
// Resolved at project scope (NOT inside tasks.processResources {}, where `property()` would
// resolve against the task, not the project). update_branch is root-only (inherited by nodes).
val updateBranch = property("update_branch") as String
val neoDepRange = property("neo_dep_range") as String
val mcDepRange = property("mc_dep_range") as String
// The Stonecutter flags build.gradle.kts worked out for this node (see buildcraft.gradle.BcNode).
val bcNode = extensions.getByType<BcNode>()
val isPre1_21_11 = bcNode.isPre1_21_11
val isPre1_21_10 = bcNode.isPre1_21_10
val isPre26_3 = bcNode.isPre26_3

repositories {
    mavenLocal()
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me/")
    // Fabric's maven, for fabric-loader alone (see the REI dependency below).
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter { includeModule("net.fabricmc", "fabric-loader") }
    }
    // The accessory-slot mods (see the AccessorySlots dependencies below).
    exclusiveContent {
        forRepository { maven("https://maven.theillusivec4.top/") }
        filter { includeGroup("top.theillusivec4.curios") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.wispforest.io/releases/") }
        filter { includeModule("io.wispforest", "accessories-neoforge") }
    }
    mavenCentral()
}

sourceSets {
    val main = getByName("main")
    named("test") {
        compileClasspath += main.compileClasspath + main.output
        runtimeClasspath += main.runtimeClasspath + main.output
    }
}

tasks.named<Copy>("processResources") {
    // Loot tables + advancements are authored in the 26.3 loot-condition form; every older node
    // rewrites its copy, and 26.3+ only validates (see DataDownportPre263). The 1.21.1 doLast below also
    // rewrites advancements, but only display.background (plus recipes and item models), a key
    // DataDownportPre263 never touches, so the order of the two hooks does not matter.
    if (isPre26_3) {
        doLast { DataDownportPre263.apply(destinationDir.resolve("data/buildcraftunofficial"), true) }
    } else {
        doLast { DataDownportPre263.apply(destinationDir.resolve("data/buildcraftunofficial"), false) }
    }
    if (isPre1_21_10) {
        doLast {
            DataDownport1211.downportRecipeDir1211(destinationDir.resolve("data/buildcraftunofficial/recipe"))
            DataDownport1211.downportAdvancementBackground1211(destinationDir.resolve("data/buildcraftunofficial/advancement"))
            // Synthesise classic models/item/ JSONs from the 1.21.4+ items/ definitions (see fn doc).
            DataDownport1211.generateOldItemModels1211(
                destinationDir.resolve("assets/buildcraftunofficial/items"),
                destinationDir.resolve("assets/buildcraftunofficial/models/item")
            )
        }
    }
}

// Test-only resources need the same 1.21.1 recipe down-port: the cycle-output game tests ship
// fixture recipes (test_cycle_a/b) under src/test/resources in the modern bare-string format, which
// 1.21.1's ingredient codec rejects. (No item-model synthesis here — test resources ship no items/.)
tasks.named<Copy>("processTestResources") {
    if (isPre1_21_10) {
        doLast {
            DataDownport1211.downportRecipeDir1211(destinationDir.resolve("data/buildcraftunofficial/recipe"))
        }
    }
}

// ─── NeoForge Configuration ─────────────────────────────────────────────────
neoForge {
    version = neoVersion

    // src/ lives at the Tree root, not in versions/<node>/, so resolve from rootProject.
    accessTransformers.from(rootProject.file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        register("client") {
            client()
            // Mirrors 1.12.2's VERSION.startsWith("$") fallback: any dev workspace is "DEV".
            // Enables BCLib.DEV-gated affordances (the /bcsoundtest command, debug logging).
            systemProperty("buildcraft.dev", "true")
            // Optional quick-play: boot straight into a saved world for headless in-client checks.
            // `./gradlew :26.1.2:runClient -PbcQuickPlay=<saveFolderName>`. Inert without the property
            // (moddev's --args does not forward, so it must be wired here). Revert needs nothing — gated.
            if (project.hasProperty("bcQuickPlay")) {
                programArguments.add("--quickPlaySingleplayer")
                programArguments.add(project.property("bcQuickPlay").toString())
            }
            // Opt-in BCDebugging tier: `-PbcDebug` (log tier) or `-PbcDebug=all` -> -Dbuildcraft.debug=<tier>.
            if (project.hasProperty("bcDebug")) {
                systemProperty("buildcraft.debug", project.property("bcDebug").toString().ifBlank { "log" })
            }
            // Optional game directory (node-relative), e.g. `-PbcRunDir=run-smoke`: the scripted in-client smoke
            // run (scripts/smoke/) boots from its own folder so the hand-used `run/` — its extra mods, saves and
            // options — never leaks into, or gets edited by, an automated pass.
            if (project.hasProperty("bcRunDir")) {
                gameDirectory = project.file(project.property("bcRunDir").toString())
            }
        }
        register("server") {
            server()
            systemProperty("buildcraft.dev", "true")
            if (project.hasProperty("bcDebug")) {
                systemProperty("buildcraft.debug", project.property("bcDebug").toString().ifBlank { "log" })
            }
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enableGameTest", "true")
            // Dev workspace, like every other BC run env (see the client run).
            systemProperty("buildcraft.dev", "true")
            gameDirectory = project.file("run-gameTestServer")
            // Optional test filter, on every node: `./gradlew :26.2:runGameTestServer -PbcTests=buildcraftunofficial:robot_*`
            // runs only the matching tests instead of the whole suite (a ResourceSelectorArgument glob —
            // `namespace:pattern`, `*`/`?` wildcards). Inert without the property: empty selection = run all.
            // 1.21.10+ hands the glob to the server's own `--tests` selector, which parses against the
            // TEST_INSTANCE registry. 1.21.1 has neither the registry nor the option (its server Main
            // rejects `--tests` outright) and its runner takes every registered TestFunction, so there the
            // same glob rides a system property and BuildCraftGameTests applies it at registration time.
            if (project.hasProperty("bcTests")) {
                if (!isPre1_21_10) {
                    programArguments.add("--tests")
                    programArguments.add(project.property("bcTests").toString())
                } else {
                    systemProperty("buildcraft.gameTestFilter", project.property("bcTests").toString())
                }
            }
            // The `fullTestSuite` tier runs this task on ALL six nodes under a tool that caps captured
            // stdout at 256 KB; the six server consoles at INFO total ~1.8 MB and blow that cap even on
            // a fully green run. Swap in the quiet log4j mirror (stdout Console appender at WARN; the
            // file appenders are untouched, so run-gameTestServer/logs/latest.log still gets full INFO,
            // and a failing game test still fails this task through the process exit code). This must
            // be a systemProperty, NOT jvmArgument: MDG writes jvmArguments BEFORE its own computed
            // args but appends systemProperties AFTER them in gameTestServerRunVmArgs.txt, and the JVM
            // resolves duplicate -D keys last-wins — so only the systemProperty form overrides MDG's
            // own -Dlog4j2.configurationFile. `-PbcLoudGameTestLogs` restores the INFO console.
            if (!project.hasProperty("bcLoudGameTestLogs")) {
                systemProperty(
                    "log4j2.configurationFile",
                    rootProject.file("gradle/gameTestServerLog4j2-quiet.xml").absolutePath
                )
            }
        }
    }

    mods {
        register("buildcraftunofficial") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["test"])
        }
    }

    // FML-JUnit unit tests: `test` boots a real FML loader before the JUnit engine, so tests can
    // class-load game classes (Entity, ItemStack, registries) that a bare JVM cannot — NeoForge's
    // AttachmentHolder.<clinit> asks FMLEnvironment.isProduction() and throws without a loader.
    unitTest {
        enable()
        testedMod = mods["buildcraftunofficial"]
    }
}

dependencies {
    // Recipe viewers: both APIs on every node; the runtime jar is the one `recipe_viewer` picks.
    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$minecraftVersion-neoforge-api:$jeiVersion")
    // REI's api POM brings cloth-config and architectury along (its fluid entries are architectury FluidStacks).
    compileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:$reiVersion")
    // REI's classes keep Fabric's @Environment(EnvType.CLIENT); without the enum on the compile classpath javac
    // prints "unknown enum constant EnvType.CLIENT" for every one it reads, a warning no -Xlint flag silences.
    // Only those two annotation types are wanted, never Fabric code: compile-only, without dependencies.
    compileOnly("net.fabricmc:fabric-loader:0.16.9") { isTransitive = false }
    if (recipeViewer == "rei") {
        runtimeOnly("me.shedaniel:RoughlyEnoughItems-neoforge:$reiVersion")
        // REI's POM pins an older architectury than players get; 22.0.3 fails a mixin on our 26.3 pin. Run the
        // line's newest instead (Gradle resolves the higher of the two).
        runtimeOnly("dev.architectury:architectury-neoforge:${property("architectury_version")}")
    } else {
        runtimeOnly("mezz.jei:jei-$minecraftVersion-neoforge:$jeiVersion")
    }

    // Accessory-slot mods, so Robot Goggles worn in a Curios or Accessories slot count (buildcraft.lib.compat.AccessorySlots).
    // Compile-only: BC names their API only behind ModList checks. Accessories has no build past 1.21.10, so it is
    // pinned only on those nodes; only its AccessoriesCapability is used, so its owo-lib dependency stays off the path.
    compileOnly("top.theillusivec4.curios:curios-neoforge:${property("curios_version")}:api")
    (findProperty("accessories_version") as String?)?.let {
        compileOnly("io.wispforest:accessories-neoforge:$it") { isTransitive = false }
    }

    // GuideME (AE2's guide library), opt-in for dev runs: `./gradlew :26.3:runClient -PwithGuideMe` loads it so the
    // generated guide under assets/buildcraftunofficial/guides/ can be checked in-game. BC has no code dependency
    // on it (the guide is resources only), so it stays off the default runtime and the test runs.
    if (project.hasProperty("withGuideMe")) runtimeOnly("org.appliedenergistics:guideme:${property("guideme_version")}")

    // jspecify @Nullable/@NonNull: NeoForge exposes it transitively on 1.21.11+ / 26.1, but the
    // 21.10.x line does not put it on the compile classpath — add it explicitly for that node.
    if (isPre1_21_11) compileOnly("org.jspecify:jspecify:1.0.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    // The unit-test JVM is a dev workspace, like every other BC run env (client, server and
    // gameTestServer all set -Dbuildcraft.dev=true). No content is dev-gated, so the registry is
    // the same either way; the flag only turns on dev affordances such as BCDebugging's logging.
    systemProperty("buildcraft.dev", "true")

    // CopyrightHeaderTester and StonecutterDirectiveGuardTester read source files themselves off
    // disk, so their real inputs are the source TEXT — not the compiled classes Gradle normally tracks.
    // A header or directive-arm edit is comment-only, produces byte-identical bytecode, and would
    // otherwise leave `test` UP-TO-DATE: the guard would silently stop guarding exactly when someone
    // re-introduces a bad header or a mangled directive arm. Declaring the trees as an input closes
    // that hole. Path-insensitive because only the file CONTENTS matter.
    inputs.files(
        rootProject.fileTree("src/main/java") { include("**/*.java") },
        rootProject.fileTree("src/test/java") { include("**/*.java") }
    ).withPropertyName("sourceHeaders").withPathSensitivity(PathSensitivity.RELATIVE)

    // The directive guard walks all of src/, not just the Java trees: the other extensions Stonecutter
    // processes (resources such as accesstransformer.cfg) can carry a directive too. Keep this list in
    // step with the guard's JAVA_LIKE and NOT_JUDGED sets, minus java, which the input above covers.
    inputs.files(
        rootProject.fileTree("src") {
            include(listOf("scala", "sc", "groovy", "gradle", "json5", "kt", "kts", "fsh", "vsh", "cfg", "aw",
                "accesswidener", "ct", "classtweaker", "yml", "yaml").map { "**/*.$it" })
        }
    ).withPropertyName("stonecutterOtherSources").withPathSensitivity(PathSensitivity.RELATIVE)

    // Same hole, second door: noticeFileListsEveryMmplFile cross-checks NOTICE.md against those headers,
    // so NOTICE.md is an input too. Verified empirically — deleting a path from NOTICE.md left `test`
    // UP-TO-DATE and the guard silent until this line existed.
    inputs.file(rootProject.file("NOTICE.md"))
        .withPropertyName("licenceNotice").withPathSensitivity(PathSensitivity.NONE)

    // StonecutterDirectiveGuardTester decides which directive arms are live from the `stonecutter active` line.
    inputs.file(rootProject.file("stonecutter.gradle.kts"))
        .withPropertyName("activeNode").withPathSensitivity(PathSensitivity.NONE)

    // Code-inspecting tests (ForRemovalApiGuardTester, BlocksAtlasIdTester, via buildcraft.lib.test.MainSourceSet)
    // must read THIS node's view of the main code: the active node compiles src/main/java raw, every other node a
    // Stonecutter-generated copy. Resolved here while the task is configured, as plain strings — the configuration
    // cache cannot carry a closure back to the script. The classes dirs are already test inputs (runtime classpath);
    // the generated sources derive from src/main/java, which the sourceHeaders input above covers.
    val mainSet = sourceSets["main"]
    systemProperty("buildcraft.test.mainSourceDirs",
        mainSet.java.srcDirs.joinToString(File.pathSeparator) { it.absolutePath })
    systemProperty("buildcraft.test.mainClassesDirs",
        mainSet.output.classesDirs.files.joinToString(File.pathSeparator) { it.absolutePath })
}

tasks.processResources {
    val replaceProperties = mapOf(
        "mod_version" to modVersion,
        "update_branch" to updateBranch,
        "neo_dep_range" to neoDepRange,
        "mc_dep_range" to mcDepRange
    )
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ─── Distribution JAR ────────────────────────────────────────────────────────
tasks.jar {
    archiveBaseName = "BuildCraftRedux"
    // Axis A (mod_version, CalVer) + Axis B (minecraft_version) as a SemVer build-metadata tag.
    // The +mc<mc> coordinate lives ONLY in the jar filename — never in the mods.toml version —
    // because NeoForge orders versions Maven-style, where '+...' would corrupt update ordering.
    // The tag IS the node's minecraft_version (e.g. +mc26.1.2), so there's no separate jar-tag
    // property to keep in sync — one variable drives both the build target and the filename.
    archiveVersion = "$modVersion+mc$minecraftVersion"

    // Ship the licence texts inside the jar. Not cosmetic: the jar carries the buildcraft.api classes,
    // which are MIT rather than MPL, and the MIT terms require the notice to be "included in all copies
    // or substantial portions of the Software" — a jar with none was a distribution the licence did not
    // permit. LICENSE.API is the text those ~70 api files already point at by name. LICENSE.MMPL covers the
    // 38 files carried over from 1.7.10/1.12.2 whose headers cite mod-buildcraft.com: MMPL has no
    // notice-inclusion clause of its own, but a jar that names a licence it does not carry is a jar nobody
    // offline can read the terms of. NOTICE.md is the map from file to licence.
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE.API")) { into("META-INF") }
    from(rootProject.file("LICENSE.MMPL")) { into("META-INF") }
    from(rootProject.file("NOTICE.md")) { into("META-INF") }
}

tasks.register<JavaExec>("dumpMethods") {
    mainClass = "buildcraft.TestDump"
    classpath = sourceSets["main"].runtimeClasspath
}

// ─── Stonecutter wiring ──────────────────────────────────────────────────────
// ModDevGradle must wait for Stonecutter to emit this node's preprocessed sources
// before it resolves/compiles them, or compileJava races stonecutterGenerate.
tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

// ─── 1.21.1: a game-test server that never started must fail the run ───────────
// 1.21.1's net.minecraft.server.Main catches every Exception thrown during startup, logs "Failed to start the
// minecraft server" and returns normally, so a mod-loading error or an empty test list leaves runGameTestServer
// green with exit code 0 (the 1.21.10+ nodes exit non-zero). fullTestSuite trusts that exit code, so on this node
// the task also reads the server's own log. A run that got through its tests printed the "GAME TESTS COMPLETE"
// summary and then "All N required tests passed"; a run with failures prints "N required tests failed" instead
// (the exit status is that count, which wraps to 0 at 256 on POSIX, so the log is the only reliable word), and a failed
// startup prints the start-failure line. The previous log is deleted first so a run that wrote none cannot pass on
// the last run's; a log that cannot be deleted (locked by a viewer or scanner) fails the run for the same reason,
// as its stale completion line would otherwise stand in for this run's. The closures use only a local File and
// locals, never a script-level val or the script logger: the configuration cache cannot serialise those.
if (isPre1_21_10) {
    tasks.matching { it.name == "runGameTestServer" }.configureEach {
        val serverLog = layout.projectDirectory.file("run-gameTestServer/logs/latest.log").asFile
        doFirst {
            if (serverLog.exists() && !serverLog.delete()) {
                throw GradleException(
                    "Cannot delete the previous game-test log $serverLog (locked by another process?): its " +
                        "completion line would pass this run even if the server wrote nothing. Close whatever " +
                        "holds it open and re-run."
                )
            }
        }
        doLast {
            val text = if (serverLog.isFile) serverLog.readText() else ""
            val completed = text.contains("GAME TESTS COMPLETE")
            // A positive count: "All 0 required tests passed" is an empty run, not a pass.
            val allRequiredPassed = Regex("""All [1-9]\d* required tests passed""").containsMatchIn(text)
            val requiredFailed = Regex("""\d+ required tests failed""").containsMatchIn(text)
            val startFailed = text.contains("Failed to start the minecraft server")
            if (!completed || !allRequiredPassed || requiredFailed || startFailed) {
                throw GradleException(
                    "The game-test server exited 0 without a clean pass: $serverLog needs the 'GAME TESTS COMPLETE' " +
                        "summary and 'All N required tests passed' with N > 0, and neither 'N required tests failed' nor " +
                        "'Failed to start the minecraft server' (1.21.1's server Main swallows startup failures, " +
                        "and its exit status is the failed-test count, which wraps to 0 at 256 on POSIX). " +
                        "completed=$completed, allRequiredPassed=$allRequiredPassed, requiredFailed=$requiredFailed, " +
                        "startFailed=$startFailed."
                )
            }
        }
    }
}

// `./gradlew buildAndCollect` at the Tree root cascades to every version node and
// gathers each node's jar into build/libs/<mod_version>/ (the +mc<mc> names keep them distinct).
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.jar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    dependsOn("assemble")
}

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: unit tests, headless game tests and
// the jar. The node owns the list because only the node knows its loader; a Fabric node's twin is in bc.fabric.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This NeoForge node's share of fullTestSuite: unit tests, headless game tests and the collected jar."
    dependsOn("test", "runGameTestServer", "buildAndCollect")
}
