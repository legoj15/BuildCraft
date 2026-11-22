// The MinecraftForge half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// forge (26.3-forge, 26.2-forge, 26.1.2-forge, 1.21.11-forge and 1.21.1-forge today). This is MinecraftForge, NOT NeoForge
// (bc.neoforge): a different plugin (ForgeGradle 7), a different loader API and a different metadata file. ForgeGradle's
// version is pinned in buildSrc/build.gradle.kts.
//
// Port in progress (#209), NOTHING SHIPS. The node compiles all of src/main/java except the recipe-viewer plugin packages
// of a viewer this node has no build of (see the sourceSets block), builds a jar collected beside the NeoForge and Fabric
// ones by buildAndCollect, and runs the dedicated server (slice F4, docs/forge-port.md). No unit tests (FML-JUnit has no
// Forge twin); the shared game tests run through `runGameTest` (slice F5, below), which nodeVerification runs.
// Its share of the root fullTestSuite is the compile, the transformed resources, the collected jar and the game tests.
//
// Forge on 26.x runs Mojang's official names (Minecraft ships unobfuscated), and Forge 52 (1.21.1) runs them too in dev and in
// production, so the shared source compiles against Forge directly and there is no reobfuscation step on any line: the
// mappings line only tells the mavenizer which names to build Forge in. The 1.21.1 line differs from the others in the event
// bus (EventBus 7 on 1.21.11 and 26.x, EventBus 6 on 1.21.1), which platform.forge.ForgeBus hides, and in the 1.21.1 data
// down-ports below; every pre-26.3 line also takes the loot/advancement down-port.
// ForgeGradle builds that patched Minecraft+Forge artifact itself, on demand, through the repositories below.

import buildcraft.gradle.AppendingLogStream
import buildcraft.gradle.AssembleGameTestMod
import buildcraft.gradle.DataDownport1211
import buildcraft.gradle.DataDownportPre263
import buildcraft.gradle.DataForge
import buildcraft.gradle.FabricGameTests
import buildcraft.gradle.WithMods
import java.time.Duration

plugins {
    // Already applied by build.gradle.kts; named here because ForgeGradle does not apply it, and without it this script
    // gets no typed sourceSets / implementation / compileOnly accessors (Loom's id brings them for bc.fabric).
    java
    id("net.minecraftforge.gradle")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
// The MC-line flags build.gradle.kts worked out from Stonecutter (the 1.21.1 line needs the data down-ports below).
val bcNode = extensions.getByType<buildcraft.gradle.BcNode>()
val isPre26_3 = bcNode.isPre26_3
val isPre1_21_10 = bcNode.isPre1_21_10
val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val modVersion = property("mod_version") as String
// The player-facing minimums mods.toml declares: their own properties, so a routine bump of the dev pin never raises what a
// Forge player must install (the Forge twins of bc.neoforge's neo_dep_range / mc_dep_range).
val forgeVersionRange = property("forge_version_range") as String
val forgeLoaderVersionRange = property("forge_loader_version_range") as String
val mcDepRange = property("mc_dep_range") as String
// The pack-format members of pack.mcmeta (min_format/max_format on 26.x, pack_format on 1.21.1).
val packFields = property("pack_fields") as String
val updateBranch = property("update_branch") as String
// The Forge mixin config (src/main/resources-forge): declared to Mixin by a dev run's launch argument and by the jar manifest.
val mixinConfig = "buildcraftunofficial.forge.mixins.json"
// The opt-in -P switches the NeoForge and Fabric runs read too, resolved once here at script scope.
val bcDebug = findProperty("bcDebug")?.toString()
val bcQuickPlay = findProperty("bcQuickPlay")?.toString()
// A node whose Forge loads a quick-played world before starting its game event bus (`quickplay_drops_game_events`, see
// versions/1.21.1-forge/gradle.properties): the world opens, then the integrated server crashes at its first loot roll.
if (bcQuickPlay != null && findProperty("quickplay_drops_game_events")?.toString() == "true") {
    logger.warn("-PbcQuickPlay on $name: this Forge drops every game-bus event during a quick-played world load " +
            "(the integrated server crashes at the first loot roll); open the save through Singleplayer instead")
}
val bcRunDir = findProperty("bcRunDir")?.let { file(it.toString()) }
// The two-client multiplayer smoke's switches (scripts/smoke/multiplayer/mp.py): the player's name and the server a client
// quick-plays into. Both are plain `-P` switches, so a hand-run second client works the same way.
val bcUser = findProperty("bcUser")?.toString()
val bcQuickPlayServer = findProperty("bcQuickPlayServer")?.toString()
val bcTests = findProperty("bcTests")?.toString()
// Third-party mods for dev runs, opt-in (`-PwithMods=alias[,alias...]`, see buildcraft.gradle.WithMods): the coordinates this
// node's `withmod.<alias>` properties name, empty without the property. Read once here; a task action must not capture it.
val withMods = WithMods.coordinates(project)
// The game-test server's working directory (gitignored, the twin of the NeoForge nodes' run-gameTestServer): the universe,
// config, logs/latest.log (the pass check below reads it) and console.log all land here.
val gameTestRunDir = file("run-gameTestServer")
// The node's JEI pin, if it has one (only 1.21.1-forge: JEI's 1.21.1 LTS branch has a Forge build; the 1.21.11, 26.1.2 and
// 26.2 Forge nodes wire none, and neither viewer has a Forge build for 26.3), and the viewer the dev runs load: JEI by
// default where it is pinned, `-Precipe_viewer=none` for none. No Forge node pins REI (it has no Forge build for 1.21.1 or
// 26.3), so `rei` is refused, as is any viewer on a node that pins none.
val jeiVersion = findProperty("jei_version")?.toString()
val recipeViewer = (findProperty("recipe_viewer") as String?)?.lowercase() ?: (if (jeiVersion != null) "jei" else "none")
if (recipeViewer != "none" && (recipeViewer != "jei" || jeiVersion == null)) {
    throw GradleException("recipe_viewer on this Forge node must be 'none'" +
            (if (jeiVersion != null) " or 'jei'" else "") + ", not '$recipeViewer'")
}

repositories {
    // ForgeGradle's repository helpers: the mavenizer (builds the patched, mapped Minecraft+Forge artifact on demand), Forge's
    // Maven, and Mojang's libraries Maven. Declared before mavenCentral so a Forge or Minecraft artifact never resolves
    // from anywhere else.
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    // JEI's own Maven, for a node that pins jei_version (see the dependencies below); filtered to JEI's group.
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com") }
        filter { includeGroup("mezz.jei") }
    }
    // Modrinth's maven, for the compile-only Open Parties and Claims jar alone (see the dependency below).
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    mavenCentral()
}

minecraft {
    // Mojang's official names, which the shared source is written in (and which Forge runs on, see the header).
    mappings("official", minecraftVersion)

    // Dev runs, in the node's run/ directory like the NeoForge and Fabric ones: `:<node>:runServer` and `runClient`.
    // Both carry the switches every BC run has: the dev workspace flag (BCLib.DEV-gated affordances: debug logging, the
    // /bcsoundtest command) and `-PbcDebug[=tier]` for BCDebugging. The recipe viewer is JEI where the node pins it
    // (1.21.1-forge) and none on the other Forge nodes, which pin no viewer; see recipeViewer above.
    runs {
        configureEach {
            workingDir.convention(layout.projectDirectory.dir("run"))
            // BuildCraft's mixin config (platform.forge.mixin). A dev run has no jar manifest to carry it (see the jar below),
            // so Mixin's own launch option declares it, and a `--mixin...` argument is also what makes FML enable Mixin in dev.
            args("--mixin.config", mixinConfig)
            systemProperty("buildcraft.dev", "true")
            if (bcDebug != null) {
                systemProperty("buildcraft.debug", bcDebug.ifBlank { "log" })
            }
        }
        register("client") {
            // The dev user is "Dev" on every loader (see bc.fabric): the owner-gated checks name the player by its UUID.
            // `-PbcUser=<name>` is the second player of a multiplayer run (it replaces the argument, it is not a second one).
            args("--username", bcUser ?: "Dev")
            // Optional quick-play and game directory, as on NeoForge: `-PbcQuickPlay=<save>` boots straight into a saved
            // world, `-PbcQuickPlayServer=<host:port>` straight into a server, `-PbcRunDir=<node-relative dir>` is the scripted
            // smoke run's own folder. ForgeGradle passes `--gameDir .`, so moving the working directory moves mods, config,
            // saves and options.txt with it.
            if (bcQuickPlay != null) {
                args("--quickPlaySingleplayer", bcQuickPlay)
            }
            if (bcQuickPlayServer != null) {
                args("--quickPlayMultiplayer", bcQuickPlayServer)
            }
            if (bcRunDir != null) {
                workingDir.set(bcRunDir)
            }
        }
        register("server") {
            // No console window on the dev server: its console is the Gradle task's, and "stop" on stdin ends it.
            args("--nogui")
            // `-PbcRunDir=<node-relative dir>` is the multiplayer smoke's own server directory (world, logs, server.properties).
            if (bcRunDir != null) {
                workingDir.set(bcRunDir)
            }
        }
        // Forge's own game-test run type (launch target forge_userdev_server_gametest): net.minecraft.gametest.Main, which Forge
        // patches to set forge.enableGameTest and forge.gameTestServer and to run its mod loader before the server exists. There
        // is no NeoForge-style `type = "gameTestServer"` to set: the run inherits the userdev config of the same name. The tasks
        // this makes are runGameTestServer (the main source set alone) and runTestGameTestServer (main plus the test source
        // set in one mod, the one the shared game tests need); `runGameTest` below is the front door.
        register("gameTestServer") {
            workingDir.set(gameTestRunDir)
            // Optional filter, as on the other nodes: `-PbcTests=buildcraftunofficial:pipe_*` (vanilla's own `--tests` selector,
            // a namespaced glob against the test_instance registry). 1.21.1's server has no `--tests` and runs every registered
            // TestFunction, so there the glob rides a system property and LegacyGameTestRegistry applies it, as on NeoForge.
            if (bcTests != null) {
                if (!isPre1_21_10) {
                    args("--tests", bcTests)
                } else {
                    systemProperty("buildcraft.gameTestFilter", bcTests)
                }
            }
            // -PbcGameTestTrace logs every test's start and end (buildcraft.lib.test.GameTestTrace): the way to name the tests
            // in flight when a product exception in a level tick crashes the server.
            if (hasProperty("bcGameTestTrace")) {
                systemProperty("buildcraft.gameTestTrace", "true")
            }
        }
    }
}

// The access transformer is the NeoForge node's own file, src/main/resources/META-INF/accesstransformer.cfg: ForgeGradle 7
// hands it to the mavenizer, which applies it while it builds the Minecraft+Forge artifact, so the same entries open the
// same members (Forge 26.3 runs the official names the file is written in). Set on the extension, not inside a
// minecraft.dependency {} block: that block is a Groovy-closure owner, and Kotlin's Action overload of it fails with a
// ClassCastException (the lambda is handed the plain dependency, not the closure owner). A dependency with none of its own
// inherits this one.
// Resolved from the root, as bc.neoforge does, because src/ lives at the tree root, not under versions/<node>/.
minecraft.getAccessTransformer().from(rootProject.file("src/main/resources/META-INF/accesstransformer.cfg"))

// A recipe viewer's plugin packages (compat/jei, compat/rei in every subsystem) are compiled only on a node that pins that
// viewer: the pin is the node's `jei_version` / `rei_version` property, which the other loaders' nodes all carry. Of the
// Forge nodes only 1.21.1-forge pins one (JEI); 1.21.11-forge, 26.1.2-forge and 26.2-forge wire none, neither viewer has
// a Forge build for 26.3, and REI has none for 1.21.1 either. Nothing outside those packages names them (the viewers find their plugins themselves), so leaving them out
// costs nothing. A node that pins a viewer gets its compile-only APIs below, and the exclusion stops on its own.
val viewerlessExcludes = buildList {
    if (findProperty("jei_version") == null) add("**/compat/jei/**")
    if (findProperty("rei_version") == null) add("**/compat/rei/**")
}

sourceSets {
    named("main") {
        // The whole of BuildCraft's source, less the viewer packages above. The filter applies to whichever directory
        // Stonecutter points the source set at (src/ raw, or this node's generated copy).
        java { exclude(viewerlessExcludes) }
        // BC's assets and data are shared; the loader metadata is not. src/main/resources-forge adds this node's own
        // (mods.toml, pack.mcmeta, the mixin config) and the NeoForge-only file is dropped. META-INF/accesstransformer.cfg
        // STAYS: FML reads that name from a Forge mod jar as well (and the mavenizer already applied the same file at
        // compile time, so at run time it is idempotent). The NeoForge-shaped data JSON is rewritten after the copy
        // (DataForge, below).
        resources {
            srcDir(rootProject.file("src/main/resources-forge"))
            exclude("META-INF/neoforge.mods.toml")
            // The Curios/Accessories slot tags (and the Curios entity assignment under BC's own namespace) put the goggles
            // in a slot those mods own; ForgeAccessorySlots never looks (and neither has a Forge 26.3 build), so the jar does
            // not advertise a slot BC does not read. (bc.fabric leaves the inert data/buildcraftunofficial/curios in.)
            exclude("data/accessories/**", "data/curios/**", "data/*/accessories/**", "data/*/curios/**")
        }
    }
    named("test") {
        // The shared game-test bodies (src/test/java through Stonecutter's copy for this node, so the `//? if forge` arms are
        // live), less what only another loader can compile. There are no unit tests here (FML-JUnit has no Forge twin;
        // docs/platform-layer.md, test plan), so every JUnit file is dropped by content (a game-test body never imports
        // org.junit), and so is buildcraft/platform/neoforge: the NeoForge implementations' tests and NeoForgeGameTestHooks,
        // all typed in NeoForge classes outside any directive. platform/fabric stays: its files are whole-file
        // `//? if fabric` comments here, except FabricAdapterTester, whose body is a bare success off Fabric. The Forge
        // hook is platform/forge/ForgeGameTestHooks.
        // A game test this node cannot run sits in a `//? if neoforge || fabric {` arm of BuildCraftGameTests.registerAll under a
        // `// Not on Forge: <reason>` line, and processTestResources drops its manifest (below).
        java {
            exclude("buildcraft/platform/neoforge/**")
            exclude { !it.isDirectory && it.file.readText().contains("import org.junit") }
        }
        // The test source set sees everything the main one does, as bc.neoforge's does (Forge's java plugin only adds main's
        // output and its implementation configuration, not main's compile-only ones).
        compileClasspath += sourceSets["main"].compileClasspath
    }
}

dependencies {
    // Forge itself: the patched, Mojang-named Minecraft plus Forge's own classes and libraries (FML included). That brings
    // jsr305's javax.annotation (@Nullable/@Nonnull, used throughout BC) along, as ModDevGradle does on NeoForge, so the
    // compile-only line bc.fabric needs for it is not repeated here.
    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    // jspecify's @Nullable/@NonNull: Minecraft brings it from 1.21.11; the 1.21.1 compile needs it named (as on bc.fabric).
    if (bcNode.isPre1_21_11) compileOnly("org.jspecify:jspecify:1.0.0")

    // Open Parties and Claims' server API, which lib.compat.claims.OpenPartiesAndClaims names behind a loaded-mod check:
    // compile-only against the jar this node's `withmod.opac` alias names, as bc.neoforge and bc.fabric do.
    compileOnly(WithMods.compileCoordinate(project, "opac", "maven.modrinth:open-parties-and-claims")) { isTransitive = false }

    // -PwithMods: the requested third-party mods and the libraries their aliases name, on every Forge run (client, server, game
    // tests) as on the NeoForge and Fabric nodes. Not transitive: Modrinth's maven carries no dependency metadata, so each alias
    // lists what its mod needs.
    withMods.forEach { coordinate -> runtimeOnly(coordinate) { isTransitive = false } }

    // JEI on a node that pins it: its common and Forge APIs to compile the compat/jei plugins against, as bc.neoforge does,
    // and its Forge jar in the dev runtime unless `-Precipe_viewer=none`. FML finds a mod jar on the dev classpath by its
    // mods.toml; JEI's Forge build runs on Mojang's names, as Forge 52 does, so it needs no remapping.
    if (jeiVersion != null) {
        compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
        compileOnly("mezz.jei:jei-$minecraftVersion-forge-api:$jeiVersion")
        if (recipeViewer == "jei") runtimeOnly("mezz.jei:jei-$minecraftVersion-forge:$jeiVersion")
    }
}

// Stonecutter has to emit this node's sources before they compile: a compile against a stale or empty generated copy would
// pass this node's compile gate vacuously (the edge bc.fabric states for the same reason).
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No unit tests: FML-JUnit has no Forge twin (docs/platform-layer.md, test plan). The game tests are below.
tasks.named("test") { enabled = false }

// ─── Game tests (F5) ─────────────────────────────────────────────────────────
// `:<node>:runGameTest` runs the SAME game-test bodies the NeoForge and Fabric nodes run, on Forge's own game-test server.
// - The tests are part of BuildCraft's mod, not a second mod: test_instance manifests, structures and BuildCraft's classes are
//   one pack, and platform/forge/ForgeGameTestHooks (an automatic @Mod.EventBusSubscriber) calls
//   BuildCraftGameTests.register from FMLConstructModEvent (on 1.21.1, which has no dynamic test registry, it registers the
//   test-only blocks there and feeds vanilla's GameTestRegistry from RegisterGameTestsEvent). Forge finds a mod in dev by the META-INF/mods.toml of a classpath
//   directory (there is no way to say "these two directories are one mod": FG7's `mods { sources(a, b) }` is not read by
//   this FML), and Java refuses two modules that both hold buildcraft.transport.block ("Modules buildcraftunofficial and test
//   export package ..."), which the tests, sharing packages with the code they test, would be. So the test run's classpath
//   swaps the two source set outputs for ONE directory, assembleGameTestMod: main's output (classes, the transformed
//   resources, mods.toml, the mixin config) with the tests' classes and resources copied over it. The jar never sees it.
// - The test source set reads the TEST copy of Stonecutter's output, so it waits for that copy, not for main's.
tasks.named("compileTestJava") { dependsOn("stonecutterGenerateTest") }
val assembleGameTestMod = tasks.register<AssembleGameTestMod>("assembleGameTestMod") {
    group = "verification"
    description = "BuildCraft's mod directory with the game tests merged in: what the Forge game-test run loads as one mod."
    mainOutput.from(sourceSets["main"].output)
    testOutput.from(sourceSets["test"].output)
    modDir.set(layout.buildDirectory.dir("gameTestMod"))
}
sourceSets.named("test") {
    // FG7 builds the `runTest...` tasks from this source set's runtime classpath, read lazily when a task runs.
    runtimeClasspath = project.files(assembleGameTestMod) + (runtimeClasspath - output - sourceSets["main"].output)
}
tasks.named<ProcessResources>("processTestResources") {
    dependsOn("stonecutterGenerateTest")
    // The registrations this node compiles (BuildCraftGameTests as Stonecutter wrote it for this node): a manifest naming a test
    // function this node does not register would fail that test at run time ("no such function"), so it is dropped, as on
    // Fabric; a registered test without a manifest fails the build.
    val registrar = sourceSets["test"].java.srcDirs.map { it.resolve("buildcraft/BuildCraftGameTests.java") }
    inputs.files(registrar).optional()
    doLast {
        FabricGameTests.dropUnregisteredManifests(destinationDir, registrar.first { it.isFile })
    }
    // The cycle-output tests' fixture recipes are in the modern format, which 1.21.1's ingredient codec rejects: the same
    // down-port bc.neoforge and bc.fabric run on their test resources.
    if (isPre1_21_10) {
        doLast { DataDownport1211.downportRecipeDir1211(destinationDir.resolve("data/buildcraftunofficial/recipe")) }
    }
}

// The game-test server's console (INFO, and a progress line per test tick) goes to run-gameTestServer/console.log instead of
// Gradle's output, as on the Fabric node; logs/latest.log is untouched. `-PbcLoudGameTestLogs` keeps it on the console. Then the
// pass check: a run must end with vanilla's "All N required tests passed" (N > 0), so an empty or aborted run cannot pass on
// its exit code alone (Forge's bootstrap hands the exit code of the game's main on, but a run that finds no test exits 0).
// The closures use only local Files: the configuration cache cannot serialise script-level values.
tasks.withType<JavaExec>().configureEach {
    if (name != "runTestGameTestServer") return@configureEach
    val runDir = project.file("run-gameTestServer")
    val consoleLog = runDir.resolve("console.log")
    val serverLog = runDir.resolve("logs/latest.log")
    val loud = project.hasProperty("bcLoudGameTestLogs")
    // A wall-clock guard: the whole suite runs in minutes, so a run still going after 25 has hung.
    timeout.set(Duration.ofMinutes(25))
    doFirst {
        if (serverLog.exists() && !serverLog.delete()) {
            throw GradleException("Cannot delete the previous game-test log $serverLog (locked by another process?)")
        }
        if (!loud) {
            runDir.mkdirs()
            consoleLog.writeText("")
            standardOutput = AppendingLogStream(consoleLog)
        }
    }
    doLast {
        val text = if (serverLog.isFile) serverLog.readText() else ""
        if (!Regex("""All [1-9]\d* required tests passed""").containsMatchIn(text)) {
            throw GradleException(
                "The Forge game-test run exited 0 without 'All N required tests passed' (N > 0) in $serverLog; " +
                    "its console is in $consoleLog."
            )
        }
    }
}
// The Fabric node's task name, for the same command on every loader. Part of nodeVerification, as on Fabric.
tasks.register("runGameTest") {
    group = "verification"
    description = "Runs every shared game test on Forge's game-test server (the pass check reads run-gameTestServer/logs/latest.log)."
    dependsOn("runTestGameTestServer")
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "mod_version" to modVersion,
        "update_branch" to updateBranch,
        "forge_version_range" to forgeVersionRange,
        "forge_loader_version_range" to forgeLoaderVersionRange,
        "mc_dep_range" to mcDepRange,
        "pack_fields" to packFields,
    )
    inputs.properties(replaceProperties)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties)
    }
    // A file present in both resource trees is a mistake, not something to drop silently.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // The loot/advancement down-port every pre-26.3 node applies (26.3 only validates), and the 1.21.1 recipe, advancement and
    // item-model one, exactly as bc.neoforge and bc.fabric run them, and BEFORE the Forge transform, which expects their
    // output (on 1.21.1 the fluid-container models it rewrites are the classic models/item files the down-port wrote).
    // Constant branches: a doLast must not capture the script-level flag (configuration cache).
    if (isPre26_3) {
        doLast { DataDownportPre263.apply(destinationDir.resolve("data/buildcraftunofficial"), true) }
    } else {
        doLast { DataDownportPre263.apply(destinationDir.resolve("data/buildcraftunofficial"), false) }
    }
    if (isPre1_21_10) {
        doLast {
            DataDownport1211.downportRecipeDir1211(destinationDir.resolve("data/buildcraftunofficial/recipe"))
            DataDownport1211.downportAdvancementBackground1211(destinationDir.resolve("data/buildcraftunofficial/advancement"))
            DataDownport1211.generateOldItemModels1211(
                destinationDir.resolve("assets/buildcraftunofficial/items"),
                destinationDir.resolve("assets/buildcraftunofficial/models/item")
            )
        }
    }
    // The NeoForge -> Forge data transform (conditions, components ingredients, biome modifiers, fluid_container item
    // models), then the guard that nothing NeoForge-keyed is left.
    doLast {
        DataForge.apply(destinationDir)
        DataForge.verifyNoNeoForgeData(destinationDir)
    }
}

// ─── Distribution JAR ────────────────────────────────────────────────────────
// The NeoForge jar's name (bc.neoforge) plus the loader: BuildCraftRedux-<mod_version>+mc<mc>-forge.jar, so every loader's
// jar for one MC version sits side by side in build/libs/<mod_version>/. Forge on 26.x runs official names, so `jar` is the
// shipped jar (no reobfuscation). The licence texts ride along exactly as in the other jars.
tasks.named<Jar>("jar") {
    archiveBaseName = "BuildCraftRedux"
    archiveVersion = "$modVersion+mc$minecraftVersion"
    archiveClassifier = "forge"
    // How an installed Forge mod declares its mixin config: the manifest attribute Mixin's default platform agent reads
    // (a jar in mods/ is a container FML hands to Mixin). A dev run has no manifest, hence the launch argument above.
    manifest { attributes("MixinConfigs" to mixinConfig) }
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE.API")) { into("META-INF") }
    from(rootProject.file("LICENSE.MMPL")) { into("META-INF") }
    from(rootProject.file("NOTICE.md")) { into("META-INF") }
}

// The twin of bc.neoforge's buildAndCollect: this node's jar into the same build/libs/<mod_version>/.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    dependsOn("assemble")
}

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the compile, the transformed resources and the
// collected jar and the game tests. The NeoForge twin in bc.neoforge adds unit tests (there are none on Forge: FML-JUnit has no twin).
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Forge node's share of fullTestSuite: compileJava, the Forge data transform, the collected jar and the game tests."
    dependsOn("compileJava", "processResources", "jar", "buildAndCollect", "runGameTest")
}
