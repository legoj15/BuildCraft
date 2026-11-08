// The Fabric half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// fabric (one `<mc>-fabric` node per MC line). Loom's version is pinned in buildSrc/build.gradle.kts.
//
// Phase 2a (#98): the node compiles all of BuildCraft (src/main/java; NeoForge-only code sits in platform/neoforge,
// whose files are empty on this node) and builds a jar, collected beside the NeoForge ones by buildAndCollect.
// Nothing ships until the user says so. No unit tests (FML-JUnit has no Fabric twin); Phase 2c added the game tests
// (below). Its share of the root fullTestSuite is the compile, the class-tweaker validation, the transformed
// resources, the jar and the game tests.
//
// Loom on 26.x does not remap: Minecraft ships unobfuscated, so there is no mappings line and the mod dependencies
// go on the plain Java configurations. On the 1.21.x lines (obfuscated) the node uses Loom's remap mode instead:
// Mojang's official mappings, the mod* remapping configurations (modConfig below), and remapJar as the shipped jar.
// build.gradle.kts picks the mode before applying this plugin (the `net.fabricmc.fabric-loom[-remap]` id).

import buildcraft.gradle.AppendingLogStream
import buildcraft.gradle.BcNode
import buildcraft.gradle.DataDownport1211
import buildcraft.gradle.DataDownportPre263
import buildcraft.gradle.DataFabric
import java.time.Duration

plugins {
    // Already applied by build.gradle.kts in the node's mode; naming it here only gives this script Loom's DSL.
    id("fabric-loom")
}

val bcNode = extensions.getByType<BcNode>()
val isPre26_3 = bcNode.isPre26_3
val isPre1_21_10 = bcNode.isPre1_21_10
// Remap mode (the obfuscated 1.21.x lines).
val remap = bcNode.isPre26_1
// A mod dependency's configuration: the plain one without remapping, Loom's mod* twin with it (modImplementation...).
fun modConfig(name: String) = if (remap) "mod" + name.replaceFirstChar { it.uppercase() } else name

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
val minecraftVersion = property("minecraft_version") as String
val fabricLoaderVersion = property("fabric_loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String
val forgeConfigApiPortVersion = property("forge_config_api_port_version") as String
val modMenuVersion = property("mod_menu_version") as String
val jeiVersion = property("jei_version") as String
val reiVersion = property("rei_version") as String
// The REI API's own dependencies (its Fabric POM puts them at runtime scope), the versions that POM pins.
val reiBasicMathVersion = property("rei_basic_math_version") as String
val reiArchitecturyVersion = property("rei_architectury_version") as String
// Team Reborn Energy and Common Protection API: the builds for this MC line.
val teamRebornEnergyVersion = property("team_reborn_energy_version") as String
val commonProtectionApiVersion = property("common_protection_api_version") as String
// The other player-facing floors in fabric.mod.json, per line.
val forgeConfigApiPortDepRange = property("forge_config_api_port_dep_range") as String
val commonProtectionApiDepRange = property("common_protection_api_dep_range") as String
// Mixin's compatibilityLevel for both mixin configs: JAVA_21 on the 1.21.x lines, JAVA_25 on 26.x.
val mixinJavaLevel = "JAVA_" + (property("java_version") as String)
// The player-facing minimums fabric.mod.json declares: their own properties, so a routine bump of the dev pins above
// never raises what a Fabric player must install (the Fabric twins of bc.neoforge's neo_dep_range).
val fabricLoaderDepRange = property("fabric_loader_dep_range") as String
val fabricApiDepRange = property("fabric_api_dep_range") as String
// The opt-in -P switches the NeoForge runs read too (bc.neoforge), resolved once here at script scope.
val bcDebug = findProperty("bcDebug")?.toString()
val bcQuickPlay = findProperty("bcQuickPlay")?.toString()
val bcTests = findProperty("bcTests")?.toString()

sourceSets {
    named("main") {
        // All of src/main/java compiles here (whichever directory Stonecutter points the source set at: src/ raw, or
        // this node's generated copy). NeoForge-only code sits in platform/neoforge, empty on this node.
        // BC's assets and data are shared; the loader metadata is not. src/main/resources-fabric adds this node's
        // own (fabric.mod.json, the class tweaker) and the NeoForge-only files are dropped. The NeoForge-shaped data
        // JSON is rewritten for Fabric after the copy (DataFabric, below).
        resources {
            srcDir(rootProject.file("src/main/resources-fabric"))
            exclude("META-INF/neoforge.mods.toml", "META-INF/accesstransformer.cfg")
            // The Curios/Accessories slot tags put the goggles in a slot those mods own; neither has a Fabric 26.3 build
            // and FabricAccessorySlots never looks, so the jar does not advertise a slot BC does not read.
            exclude("data/accessories/**", "data/curios/**")
        }
    }
    named("test") {
        // Unit tests stay NeoForge-only: FML-JUnit has no Fabric twin (docs/platform-layer.md, test plan).
        java { exclude("**") }
        resources { exclude("**") }
    }
}

// ─── Game tests ──────────────────────────────────────────────────────────────
// Fabric API's game-test runner (fabric-gametest-api-v1, `-Dfabric-api.gametest`) over the SAME game-test bodies the
// NeoForge nodes run: Loom's `gametest` source set compiles this node's view of src/test/java (Stonecutter's copy, so
// the `//? if fabric` arms are live) and src/test/resources, as a second mod (buildcraftunofficial_gametest,
// src/test/resources-fabric/fabric.mod.json) whose entrypoint registers the tests through BuildCraft's own registrar
// (BuildCraftGameTests.register). `runGameTest` is the task; nodeVerification runs it.
//
// What the Fabric set leaves out, by file (the set is curated here, never by per-file loader wrappers):
// - every JUnit file: unit tests stay NeoForge-only (FML-JUnit has no Fabric twin; docs/platform-layer.md test plan);
// - buildcraft/platform/neoforge/**: tests of the NeoForge implementations, and NeoForgeGameTestHooks, the NeoForge
//   hook that makes the same registration from FMLConstructModEvent;
// - NeoForge-typed test fixtures, each named below with why (none today: the claim mods and saved-data tests reach
//   the loader through LoaderTestEvents and loader arms).
// A game test the Fabric node cannot run sits in a `//? if neoforge {` arm of BuildCraftGameTests.registerAll with
// its reason, and the manifests of tests not registered here are dropped from the resources (FabricGameTests).
val fabricGameTestExcludes = listOf(
    "buildcraft/platform/neoforge/**",
)
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "buildcraftunofficial_gametest"
        enableGameTests = true
        // The client game test (BCFabricClientGameTest, runClientGameTest below): an in-world visual check.
        enableClientGameTests = true
        // EULA accepted (user, 2026-11-06). Loom writes it only into the client game test's run dir (its
        // multiplayer phase starts a real dedicated server, which will not run without it); runGameTest's
        // headless server never asks.
        eula = true
        clearRunDirectory = false
    }
}
sourceSets {
    named("gametest") {
        val testSet = sourceSets["test"]
        java {
            setSrcDirs(testSet.java.srcDirs)
            fabricGameTestExcludes.forEach { exclude(it) }
            // JUnit files, by content: the unit tests import org.junit, no game-test body does.
            exclude { !it.isDirectory && it.file.readText().contains("import org.junit") }
        }
        resources {
            // golden/ included: the golden save-format game tests read it too.
            setSrcDirs(testSet.resources.srcDirs + rootProject.file("src/test/resources-fabric"))
        }
    }
}
dependencies {
    // javax.annotation and the config API, as for main.
    "gametestCompileOnly"("com.google.code.findbugs:jsr305:3.0.2")
    "gametestCompileOnly"("com.electronwill.night-config:core:3.8.4")
    "gametestCompileOnly"("com.electronwill.night-config:toml:3.8.4")
    // Team Reborn Energy at run time, so the FE tests exercise FabricEnergyStorages (it is optional in the jar).
    // (Remap mode: on the main dev runtime instead; the gametest set has no mod* configuration of its own.)
    if (remap) {
        "modLocalRuntime"("teamreborn:energy:$teamRebornEnergyVersion") { isTransitive = false }
    } else {
        "gametestRuntimeOnly"("teamreborn:energy:$teamRebornEnergyVersion") { isTransitive = false }
    }
}
// The gametest set reads the TEST source set's Stonecutter copy, so it waits for that copy, not its own.
tasks.named<JavaCompile>("compileGametestJava") {
    dependsOn("stonecutterGenerateTest")
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}
tasks.named<ProcessResources>("processGametestResources") {
    dependsOn("stonecutterGenerateTest")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // The registrations this node compiles (BuildCraftGameTests as Stonecutter wrote it for this node): a manifest
    // naming a test function this node does not register would fail that test at run time, so it is dropped.
    val registrar = sourceSets["test"].java.srcDirs.map { it.resolve("buildcraft/BuildCraftGameTests.java") }
    inputs.files(registrar).optional()
    val mixinLevel = mapOf("mixin_java_level" to mixinJavaLevel)
    inputs.properties(mixinLevel)
    filesMatching("buildcraftunofficial_gametest.mixins.json") { expand(mixinLevel) }
    doLast {
        buildcraft.gradle.FabricGameTests.dropUnregisteredManifests(destinationDir, registrar.first { it.isFile })
    }
    // The test fixture recipes need the same 1.21.1 down-port as the NeoForge node's test resources.
    if (isPre1_21_10) {
        doLast { DataDownport1211.downportRecipeDir1211(destinationDir.resolve("data/buildcraftunofficial/recipe")) }
    }
}
// The quiet console of bc.neoforge's gameTestServer: the server's stdout (INFO, and a progress bar line per test
// tick, tens of MB for the whole suite) goes to build/run/gameTest/console.log instead of Gradle's output, and
// latest.log/debug.log are untouched. `-PbcLoudGameTestLogs` keeps it on the console. Then the pass check: a run must
// end with vanilla's "All N required tests passed" (N > 0), so an empty or aborted run cannot pass on its exit code.
// The closures use only local Files: the configuration cache cannot serialise script-level values.
tasks.named<JavaExec>("runGameTest") {
    // Dev workspace, like every BC run (BCLib.DEV-gated affordances). Set on the task: Loom's RunConfigSettings
    // property setters are deprecated.
    systemProperty("buildcraft.dev", "true")
    // No fabric-api.gametest.report-file: its XML reporter replaces vanilla's log reporter, so a failure would
    // reach no log until the run ends. A failed required test fails the task through the exit code.
    // Optional filter, as on NeoForge: `-PbcTests=buildcraftunofficial:pipe_*` (vanilla's test selector glob).
    if (bcTests != null) {
        systemProperty("fabric-api.gametest.filter", bcTests)
    }
    val runDir = project.file("build/run/gameTest")
    val consoleLog = runDir.resolve("console.log")
    val serverLog = runDir.resolve("logs/latest.log")
    val loud = project.hasProperty("bcLoudGameTestLogs")
    // A wall-clock guard: the whole suite runs in a few minutes, so a run still going after 20 has hung (a test that
    // never starts is failed by the game-test mod's GameTestInfoChunkWaitMixin; this catches anything else).
    timeout.set(Duration.ofMinutes(20))
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
                "The Fabric game-test run exited 0 without 'All N required tests passed' (N > 0) in $serverLog; " +
                    "its console is in $consoleLog."
            )
        }
    }
}

// ─── Client game test ────────────────────────────────────────────────────────
// `:26.3-fabric:runClientGameTest`: Fabric API's client game-test runner (fabric-client-gametest-api-v1,
// `-Dfabric.client.gametest`) boots the dev client, runs the game-test mod's `fabric-client-gametest` entrypoint
// (BCFabricClientGameTest: a flat world, a rig of BuildCraft content, the held fluid items, two GUIs and the guide
// book) and exits. Screenshots land in build/run/clientGameTest/screenshots/ for a person to look at; the test itself
// fails on a BuildCraft ERROR or missing-model/texture log line, a GUI that does not open, or a magenta-heavy shot.
// It needs a real GL context, which a CI runner may lack, so it is NOT part of nodeVerification (and Loom does not
// hang it on `check`).
tasks.named<JavaExec>("runClientGameTest") {
    systemProperty("buildcraft.dev", "true")
    val runDir = project.file("build/run/clientGameTest")
    val consoleLog = runDir.resolve("console.log")
    val clientLog = runDir.resolve("logs/latest.log")
    val shots = runDir.resolve("screenshots")
    val loud = project.hasProperty("bcLoudGameTestLogs")
    timeout.set(Duration.ofMinutes(15))
    doFirst {
        if (clientLog.exists() && !clientLog.delete()) {
            throw GradleException("Cannot delete the previous client game-test log $clientLog (locked by another process?)")
        }
        // Screenshots carry a run-counter prefix; clear the last run's so the folder holds this run's alone.
        shots.deleteRecursively()
        if (!loud) {
            runDir.mkdirs()
            consoleLog.writeText("")
            standardOutput = AppendingLogStream(consoleLog)
        }
    }
    doLast {
        // The test logs this only after every check passed; an aborted run can still exit 0.
        val text = if (clientLog.isFile) clientLog.readText() else ""
        if (!text.contains("BuildCraft client game test: all checks passed")) {
            throw GradleException(
                "The Fabric client game test exited 0 without its pass line in $clientLog; its console is in $consoleLog."
            )
        }
    }
}

// ─── Dev runs ────────────────────────────────────────────────────────────────
// Loom's default `:26.3-fabric:runClient` / `runServer` get the switches every BC NeoForge run has (bc.neoforge): the dev
// workspace flag (BCLib.DEV-gated affordances: the /bcsoundtest command, debug logging), `-PbcDebug[=tier]` for
// BCDebugging and, on the client, `-PbcQuickPlay=<saveFolderName>` to boot straight into a saved world. They sit on the
// run tasks because Loom's RunConfigSettings setters are deprecated. `-PbcRunDir` has no twin: Loom sets the working
// directory itself at launch (its only setter, runDir(), is deprecated).
listOf("runClient", "runServer").forEach { name ->
    tasks.named<JavaExec>(name) {
        systemProperty("buildcraft.dev", "true")
        if (bcDebug != null) {
            systemProperty("buildcraft.debug", bcDebug.ifBlank { "log" })
        }
        if (name == "runClient" && bcQuickPlay != null) {
            args("--quickPlaySingleplayer", bcQuickPlay)
        }
    }
}

// The Fabric twin of META-INF/accesstransformer.cfg, in the 26.x class-tweaker format (classTweaker v1 official:
// 26.x is unobfuscated). It lives outside src/main/resources so the NeoForge jar never carries it, and Loom applies
// it to the Minecraft jar this node compiles against. Unlike the AT, a class tweaker names member descriptors, and
// the 1.21.x lines are obfuscated (Mojang names, `named` namespace) with different members: a node whose directory
// holds buildcraftunofficial-named.classtweaker uses that one instead, and its jar carries it in place of the shared.
val nodeClassTweaker = project.file("buildcraftunofficial-named.classtweaker")
val classTweakerName = if (nodeClassTweaker.isFile) nodeClassTweaker.name else "buildcraftunofficial.classtweaker"
loom {
    accessWidenerPath.set(if (nodeClassTweaker.isFile) nodeClassTweaker
        else rootProject.file("src/main/resources-fabric/buildcraftunofficial.classtweaker"))
}
if (nodeClassTweaker.isFile) {
    tasks.named<ProcessResources>("processResources") {
        exclude("buildcraftunofficial.classtweaker")
        from(nodeClassTweaker)
    }
}

val modVersion = property("mod_version") as String
tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "mod_version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "fabric_loader_dep_range" to fabricLoaderDepRange,
        "fabric_api_dep_range" to fabricApiDepRange,
        "forge_config_api_port_dep_range" to forgeConfigApiPortDepRange,
        "common_protection_api_dep_range" to commonProtectionApiDepRange,
        "mixin_java_level" to mixinJavaLevel,
        "class_tweaker" to classTweakerName,
    )
    inputs.properties(replaceProperties)
    // escapeBackslash: the template engine would otherwise turn the JSON escape backslash-n in the
    // description into a raw newline, which is not valid JSON.
    filesMatching(listOf("fabric.mod.json", "buildcraftunofficial.mixins.json")) {
        expand(replaceProperties) { escapeBackslash = true }
    }
    // A file present in both resource trees is a mistake, not something to drop silently.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // The NeoForge -> Fabric data transform (conditions, components ingredients, biome modifiers, fluid_container
    // item models), then the guard that nothing NeoForge-keyed is left. FabricDataServices is an input: the
    // transform checks the biome features it adds against the biome-modifier JSON it drops.
    val fabricDataCode = rootProject.file("src/main/java/buildcraft/platform/fabric/FabricDataServices.java")
    inputs.file(fabricDataCode)
    // The loot/advancement down-port every pre-26.3 node applies (26.3 only validates), and the 1.21.1 recipe,
    // advancement and item-model one: bc.neoforge runs the same two. Before the Fabric transform, which expects them.
    // (Constant branches, as in bc.neoforge: a doLast must not capture the script-level flag.)
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
    doLast {
        DataFabric.apply(destinationDir, fabricDataCode)
        DataFabric.verifyNoNeoForgeData(destinationDir)
    }
}

// Loom registers this itself; declared so the source of fabric-loader / fabric-api is visible here.
repositories {
    maven("https://maven.fabricmc.net/")
    // The recipe viewers' own mavens (JEI; REI and its architectury/cloth-config).
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me/")
    // Modrinth's maven, for Forge Config API Port alone.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    // modmuss50's maven, for Team Reborn Energy alone.
    exclusiveContent {
        forRepository { maven("https://maven.modmuss50.me/") }
        filter { includeGroup("teamreborn") }
    }
    // Nucleoid's maven, for Common Protection API alone.
    exclusiveContent {
        forRepository { maven("https://maven.nucleoid.xyz/") }
        filter { includeGroup("eu.pb4") }
    }
    // TerraformersMC's maven, for Mod Menu alone.
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/releases/") }
        filter { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    if (remap) {
        "mappings"(loom.officialMojangMappings())
    }
    modConfig("implementation")("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modConfig("implementation")("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    // NeoForge's config API on Fabric (FabricConfigRegistrar). Its night-config libraries ride inside it as nested
    // jars at runtime; the compile needs them directly because ModConfigSpec's signatures name them.
    modConfig("implementation")("maven.modrinth:forge-config-api-port:$forgeConfigApiPortVersion")
    compileOnly("com.electronwill.night-config:core:3.8.4")
    compileOnly("com.electronwill.night-config:toml:3.8.4")
    // javax.annotation (@Nullable/@Nonnull, used throughout BC): ModDevGradle brings it transitively on NeoForge.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // Recipe viewers, compile-only like on NeoForge (BC's plugins are found by the viewers' own entrypoints).
    modConfig("compileOnly")("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    modConfig("compileOnly")("mezz.jei:jei-$minecraftVersion-fabric-api:$jeiVersion")
    modConfig("compileOnly")("me.shedaniel:RoughlyEnoughItems-api-fabric:$reiVersion")
    // REI's Fabric api POM puts its own API's dependencies at runtime scope, so the compile names them: its geometry
    // (cloth basic-math: Point, Rectangle) and architectury (CompoundEventResult, the FluidStack its fluid entries
    // carry), the versions that POM pins.
    compileOnly("me.shedaniel.cloth:basic-math:$reiBasicMathVersion") { isTransitive = false }
    modConfig("compileOnly")("dev.architectury:architectury-fabric:$reiArchitecturyVersion") { isTransitive = false }
    // Team Reborn Energy, Fabric's de-facto FE/RF API, compile-only and optional at runtime (FabricEnergyStorages
    // touches it only when the mod is loaded). 5.0.0 is the newest build; it was compiled against
    // fabric-transfer-api-v1 7.0.0, whose BlockApiLookup/TransactionContext signatures its EnergyStorage names are
    // unchanged in the 8.x this node has, so its own transitive transfer-api pin is left out.
    modConfig("compileOnly")("teamreborn:energy:$teamRebornEnergyVersion") { isTransitive = false }
    // Common Protection API, the API Fabric claim mods answer "may this player break, place or use here" through
    // (FabricProtection). MIT, so it ships inside the jar (Loom's jar-in-jar `include`, carrying its own licence
    // file), as Fabric mods conventionally ship it; the loader keeps the newest copy when several mods bundle it.
    modConfig("implementation")("eu.pb4:common-protection-api:$commonProtectionApiVersion") { isTransitive = false }
    include("eu.pb4:common-protection-api:$commonProtectionApiVersion")
    // Mod Menu in the dev client only (Loom's localRuntime: never compiled against, published or shipped), so
    // runClient has the mod list whose config button opens the screen FabricClientConfigScreens registers. Its POM's
    // runtime deps are Fabric Loader and Fabric API modules this node already has; Placeholder API rides inside it.
    modConfig("localRuntime")("com.terraformersmc:modmenu:$modMenuVersion") { isTransitive = false }
}

// Stonecutter already runs stonecutterGenerate before compileJava (observed on a clean versions/26.3-fabric/build),
// but the edge is stated, as bc.neoforge does for createMinecraftArtifacts: a compile against a stale or empty
// generated copy would pass this node's only gate vacuously.
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// The deprecated-API guard (AGENTS.md: no deprecated APIs; suppress only on the narrowest element, with a one-line
// why). ForRemovalApiGuardTester is a unit test, so it runs on the NeoForge nodes only and never sees Fabric API; here
// javac itself is the guard: the main and gametest compiles lint deprecation and removal and fail on any warning
// (-Werror), so an unsuppressed [deprecation] and every [removal] stops the build. Both compiles emit no other warning
// today; a new non-lint warning would fail them too and is fixed the same way. `-PbcLint=...` (the opt-in lint audit,
// build.gradle.kts) turns -Werror off so the audit can list every category's hits without failing on them.
val deprecationGuarded = listOf("compileJava", "compileGametestJava")
deprecationGuarded.forEach { name ->
    tasks.named<JavaCompile>(name) {
        options.compilerArgs.add("-Xlint:deprecation,removal")
        if (!project.hasProperty("bcLint")) {
            options.compilerArgs.add("-Werror")
        }
    }
}
tasks.register("checkDeprecations") {
    group = "verification"
    description = "Compiles the main and gametest source sets with -Xlint:deprecation,removal -Werror (the Fabric node's deprecated-API guard)."
    dependsOn(deprecationGuarded)
}

// No unit tests: FML-JUnit has no Fabric twin (docs/platform-layer.md, test plan).
tasks.named("test") { enabled = false }

// ─── Distribution JAR ────────────────────────────────────────────────────────
// The NeoForge jar's name (bc.neoforge) plus the loader: BuildCraftRedux-<mod_version>+mc<mc>-fabric.jar, so both
// loaders' jars for one MC version sit side by side in build/libs/<mod_version>/. Loom on 26.x does not remap, so
// `jar` is the shipped jar; in remap mode (1.21.x) remapJar is, and `jar` is its dev (Mojang-named) input.
// The licence texts ride along exactly as in the NeoForge jar.
val shippedJar = if (remap) "remapJar" else "jar"
if (remap) {
    // The dev jar keeps its own classifier, so it never overwrites the shipped one in build/libs.
    tasks.named<Jar>("jar") { archiveClassifier = "fabric-dev" }
}
tasks.named<AbstractArchiveTask>(shippedJar) {
    archiveBaseName = "BuildCraftRedux"
    archiveVersion = "$modVersion+mc$minecraftVersion"
    archiveClassifier = "fabric"
}
// On the dev jar, which in remap mode is remapJar's input, so the shipped jar carries them either way.
tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("LICENSE.API")) { into("META-INF") }
    from(rootProject.file("LICENSE.MMPL")) { into("META-INF") }
    from(rootProject.file("NOTICE.md")) { into("META-INF") }
}

// The twin of bc.neoforge's buildAndCollect: this node's jar into the same build/libs/<mod_version>/.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named<AbstractArchiveTask>(shippedJar).flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    dependsOn("assemble")
}

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the deprecation-guarded compiles, the class tweaker, the
// transformed resources, the collected jar and the game tests. The NeoForge twin in bc.neoforge adds unit tests.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Fabric node's share of fullTestSuite: compileJava, the deprecated-API guard, the class-tweaker validation, the Fabric data transform, the collected jar and the game tests."
    dependsOn("compileJava", "checkDeprecations", "validateAccessWidener", "processResources", shippedJar,
        "buildAndCollect", "runGameTest")
}
