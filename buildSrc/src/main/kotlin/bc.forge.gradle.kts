// The MinecraftForge half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// forge (today only 26.3-forge). This is MinecraftForge, NOT NeoForge (bc.neoforge): a different plugin (ForgeGradle 7), a
// different loader API and a different metadata file. ForgeGradle's version is pinned in buildSrc/build.gradle.kts.
//
// Port in progress (#209), NOTHING SHIPS. The node compiles all of src/main/java except the recipe-viewer plugin packages
// of a viewer this node has no build of (see the sourceSets block), builds a jar collected beside the NeoForge and Fabric
// ones by buildAndCollect, and runs the dedicated server (slice F4, docs/forge-port.md). No unit tests (FML-JUnit has no
// Forge twin) and no game tests yet (F5). Its share of the root fullTestSuite is the compile, the transformed resources
// and the collected jar.
//
// Forge on 26.x runs Mojang's official names (Minecraft ships unobfuscated), so the shared source compiles against Forge
// directly and there is no reobfuscation step: the mappings line only tells the mavenizer which names to build Forge in.
// ForgeGradle builds that patched Minecraft+Forge artifact itself, on demand, through the repositories below.

import buildcraft.gradle.DataForge
import buildcraft.gradle.WithMods

plugins {
    // Already applied by build.gradle.kts; named here because ForgeGradle does not apply it, and without it this script
    // gets no typed sourceSets / implementation / compileOnly accessors (Loom's id brings them for bc.fabric).
    java
    id("net.minecraftforge.gradle")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
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
val bcRunDir = findProperty("bcRunDir")?.let { file(it.toString()) }

repositories {
    // ForgeGradle's repository helpers: the mavenizer (builds the patched, mapped Minecraft+Forge artifact on demand), Forge's
    // Maven, and Mojang's libraries Maven. Declared before mavenCentral so a Forge or Minecraft artifact never resolves
    // from anywhere else.
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
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

    // Dev runs, in the node's run/ directory like the NeoForge and Fabric ones: `:26.3-forge:runServer` and `runClient`.
    // Both carry the switches every BC run has: the dev workspace flag (BCLib.DEV-gated affordances: debug logging, the
    // /bcsoundtest command) and `-PbcDebug[=tier]` for BCDebugging. A Forge dev run loads no recipe viewer (neither JEI nor
    // REI has a Forge build for 26.3), so there is no recipe_viewer switch.
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
            args("--username", "Dev")
            // Optional quick-play and game directory, as on NeoForge: `-PbcQuickPlay=<save>` boots straight into a saved
            // world, `-PbcRunDir=<node-relative dir>` is the scripted smoke run's own folder. ForgeGradle passes
            // `--gameDir .`, so moving the working directory moves mods, config, saves and options.txt with it.
            if (bcQuickPlay != null) {
                args("--quickPlaySingleplayer", bcQuickPlay)
            }
            if (bcRunDir != null) {
                workingDir.set(bcRunDir)
            }
        }
        register("server") {
            // No console window on the dev server: its console is the Gradle task's, and "stop" on stdin ends it.
            args("--nogui")
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
// viewer: the pin is the node's `jei_version` / `rei_version` property, which the other loaders' nodes all carry and this
// node does not (neither JEI nor REI has a Forge build for 26.3). Nothing outside those packages names them (the viewers
// find their plugins themselves), so leaving them out costs nothing here. A Forge node that gains a viewer sets the
// property and adds the matching compile-only dependencies, as bc.neoforge does; the exclusion then stops on its own.
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
            // in a slot those mods own; neither has a Forge 26.3 build and ForgeAccessorySlots never looks, so the jar does
            // not advertise a slot BC does not read. (bc.fabric leaves the inert data/buildcraftunofficial/curios in.)
            exclude("data/accessories/**", "data/curios/**", "data/*/accessories/**", "data/*/curios/**")
        }
    }
    named("test") {
        // Unit tests stay NeoForge-only: FML-JUnit has no Forge twin (docs/platform-layer.md, test plan).
        java { exclude("**") }
        resources { exclude("**") }
    }
}

dependencies {
    // Forge itself: the patched, Mojang-named Minecraft plus Forge's own classes and libraries (FML included). That brings
    // jsr305's javax.annotation (@Nullable/@Nonnull, used throughout BC) along, as ModDevGradle does on NeoForge, so the
    // compile-only line bc.fabric needs for it is not repeated here.
    implementation(minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))

    // Open Parties and Claims' server API, which lib.compat.claims.OpenPartiesAndClaims names behind a loaded-mod check:
    // compile-only against the jar this node's `withmod.opac` alias names, as bc.neoforge and bc.fabric do.
    compileOnly(WithMods.compileCoordinate(project, "opac", "maven.modrinth:open-parties-and-claims")) { isTransitive = false }
}

// Stonecutter has to emit this node's sources before they compile: a compile against a stale or empty generated copy would
// pass this node's compile gate vacuously (the edge bc.fabric states for the same reason).
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No unit tests: FML-JUnit has no Forge twin (docs/platform-layer.md, test plan). Game tests come in slice F5.
tasks.named("test") { enabled = false }

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
    // The NeoForge -> Forge data transform (conditions, components ingredients, biome modifiers, fluid_container item
    // models), then the guard that nothing NeoForge-keyed is left. No down-port runs first: 26.3 is the line BC's data is
    // authored for (a 1.21.1 Forge node would run DataDownport1211 here, as bc.neoforge and bc.fabric do).
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
// collected jar. The NeoForge twin in bc.neoforge adds unit tests and game tests; the Forge game tests come with slice F5.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Forge node's share of fullTestSuite: compileJava, the Forge data transform and the collected jar (no game tests yet)."
    dependsOn("compileJava", "processResources", "jar", "buildAndCollect")
}
