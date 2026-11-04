// The Fabric half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// fabric (today only 26.3-fabric). Loom's version is pinned in buildSrc/build.gradle.kts.
//
// Phase 2a (#98): the node compiles all of BuildCraft (src/main/java; NeoForge-only code sits in platform/neoforge,
// whose files are empty on this node) and builds a jar, collected beside the NeoForge ones by buildAndCollect.
// Nothing ships until the user says so. No tests (FML-JUnit has no Fabric twin). Its share of the root
// fullTestSuite is the compile, the class-tweaker validation, the transformed resources and the jar.
//
// Loom on 26.x does not remap: Minecraft ships unobfuscated, so there is no mappings line and the mod dependencies
// go on the plain Java configurations rather than the mod* remapping ones.

import buildcraft.gradle.DataFabric

plugins {
    id("net.fabricmc.fabric-loom")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
val minecraftVersion = property("minecraft_version") as String
val fabricLoaderVersion = property("fabric_loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String
val forgeConfigApiPortVersion = property("forge_config_api_port_version") as String
val jeiVersion = property("jei_version") as String
val reiVersion = property("rei_version") as String

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
        }
    }
    named("test") {
        // Unit tests stay NeoForge-only: FML-JUnit has no Fabric twin (docs/platform-layer.md, test plan).
        java { exclude("**") }
        resources { exclude("**") }
    }
}

// The Fabric twin of META-INF/accesstransformer.cfg, in the 26.x class-tweaker format (classTweaker v1 official:
// 26.x is unobfuscated). It lives outside src/main/resources so the NeoForge jar never carries it, and Loom applies
// it to the Minecraft jar this node compiles against.
loom {
    accessWidenerPath.set(rootProject.file("src/main/resources-fabric/buildcraftunofficial.classtweaker"))
}

val modVersion = property("mod_version") as String
tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "mod_version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "fabric_loader_version" to fabricLoaderVersion,
        "fabric_api_version" to fabricApiVersion,
    )
    inputs.properties(replaceProperties)
    // escapeBackslash: the template engine would otherwise turn the JSON escape backslash-n in the
    // description into a raw newline, which is not valid JSON.
    filesMatching("fabric.mod.json") { expand(replaceProperties) { escapeBackslash = true } }
    // A file present in both resource trees is a mistake, not something to drop silently.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // The NeoForge -> Fabric data transform (conditions, components ingredients, biome modifiers, fluid_container
    // item models), then the guard that nothing NeoForge-keyed is left. FabricDataServices is an input: the
    // transform checks the biome features it adds against the biome-modifier JSON it drops.
    val fabricDataCode = rootProject.file("src/main/java/buildcraft/platform/fabric/FabricDataServices.java")
    inputs.file(fabricDataCode)
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
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    // NeoForge's config API on Fabric (FabricConfigRegistrar). Its night-config libraries ride inside it as nested
    // jars at runtime; the compile needs them directly because ModConfigSpec's signatures name them.
    implementation("maven.modrinth:forge-config-api-port:$forgeConfigApiPortVersion")
    compileOnly("com.electronwill.night-config:core:3.8.4")
    compileOnly("com.electronwill.night-config:toml:3.8.4")
    // javax.annotation (@Nullable/@Nonnull, used throughout BC): ModDevGradle brings it transitively on NeoForge.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // Recipe viewers, compile-only like on NeoForge (BC's plugins are found by the viewers' own entrypoints).
    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$minecraftVersion-fabric-api:$jeiVersion")
    compileOnly("me.shedaniel:RoughlyEnoughItems-api-fabric:$reiVersion")
    // REI's Fabric api POM puts its own API's dependencies at runtime scope, so the compile names them: its geometry
    // (cloth basic-math: Point, Rectangle) and architectury (CompoundEventResult, the FluidStack its fluid entries
    // carry), the versions that POM pins.
    compileOnly("me.shedaniel.cloth:basic-math:0.6.1") { isTransitive = false }
    compileOnly("dev.architectury:architectury-fabric:22.0.3") { isTransitive = false }
    // Team Reborn Energy, Fabric's de-facto FE/RF API, compile-only and optional at runtime (FabricEnergyStorages
    // touches it only when the mod is loaded). 5.0.0 is the newest build; it was compiled against
    // fabric-transfer-api-v1 7.0.0, whose BlockApiLookup/TransactionContext signatures its EnergyStorage names are
    // unchanged in the 8.x this node has, so its own transitive transfer-api pin is left out.
    compileOnly("teamreborn:energy:5.0.0") { isTransitive = false }
}

// Stonecutter already runs stonecutterGenerate before compileJava (observed on a clean versions/26.3-fabric/build),
// but the edge is stated, as bc.neoforge does for createMinecraftArtifacts: a compile against a stale or empty
// generated copy would pass this node's only gate vacuously.
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No unit tests: FML-JUnit has no Fabric twin (docs/platform-layer.md, test plan).
tasks.named("test") { enabled = false }

// ─── Distribution JAR ────────────────────────────────────────────────────────
// The NeoForge jar's name (bc.neoforge) plus the loader: BuildCraftRedux-<mod_version>+mc<mc>-fabric.jar, so both
// loaders' jars for one MC version sit side by side in build/libs/<mod_version>/. Loom on 26.x does not remap, so
// `jar` is the shipped jar. The licence texts ride along exactly as in the NeoForge jar.
tasks.named<Jar>("jar") {
    archiveBaseName = "BuildCraftRedux"
    archiveVersion = "$modVersion+mc$minecraftVersion"
    archiveClassifier = "fabric"
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

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the compile, the class tweaker, the
// transformed resources and the collected jar. The NeoForge twin in bc.neoforge adds unit tests and game tests.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Fabric node's share of fullTestSuite: compileJava, the class-tweaker validation, the Fabric data transform and the collected jar."
    dependsOn("compileJava", "validateAccessWidener", "processResources", "jar", "buildAndCollect")
}
