// The MinecraftForge half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// forge (today only 26.3-forge). This is MinecraftForge, NOT NeoForge (bc.neoforge): a different plugin (ForgeGradle 7), a
// different loader API and a different metadata file. ForgeGradle's version is pinned in buildSrc/build.gradle.kts.
//
// Port in progress (#209), COMPILE ONLY, NOTHING SHIPS. The node compiles all of src/main/java except the recipe-viewer
// plugin packages of a viewer this node has no build of (see the sourceSets block): no resources, no tests, no jar, no
// buildAndCollect. Its share of the root fullTestSuite is compileJava alone. Resources, the jar and a runtime follow in
// slice F4 (docs/forge-port.md).
//
// Forge on 26.x runs Mojang's official names (Minecraft ships unobfuscated), so the shared source compiles against Forge
// directly and there is no reobfuscation step: the mappings line only tells the mavenizer which names to build Forge in.
// ForgeGradle builds that patched Minecraft+Forge artifact itself, on demand, through the repositories below.

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
        // BC's resources are NeoForge's (mods.toml, the data down-ports); a Forge node ships none yet.
        resources { exclude("**") }
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
// pass this node's only gate vacuously (the edge bc.fabric states for the same reason).
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No tests and no jar yet: the node exists to prove the shared source compiles against Forge (slice F4 adds the jar).
tasks.named("test") { enabled = false }
tasks.named("jar") { enabled = false }

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the compile alone. The NeoForge twin in
// bc.neoforge adds unit tests, game tests and the jar. There is no buildAndCollect here: the root never names it, and an
// unqualified `buildAndCollect` runs only on the nodes that have one.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Forge node's share of fullTestSuite: compileJava only (compile-only, nothing ships yet)."
    dependsOn("compileJava")
}
