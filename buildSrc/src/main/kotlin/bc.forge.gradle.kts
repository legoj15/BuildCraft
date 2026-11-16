// The MinecraftForge half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// forge (today only 26.3-forge). This is MinecraftForge, NOT NeoForge (bc.neoforge): a different plugin (ForgeGradle 7), a
// different loader API and a different metadata file. ForgeGradle's version is pinned in buildSrc/build.gradle.kts.
//
// Toolchain spike (#209): COMPILE ONLY, NOTHING SHIPS. The node compiles the loader-neutral platform contracts, the binder
// with its forge arm live, and buildcraft.platform.forge (ForgeLoaderServices today), and nothing else: no resources, no
// tests, no jar, no buildAndCollect. Its share of the root fullTestSuite is compileJava alone.
//
// Forge on 26.x runs Mojang's official names (Minecraft ships unobfuscated), so the shared source compiles against Forge
// directly and there is no reobfuscation step: the mappings line only tells the mavenizer which names to build Forge in.
// ForgeGradle builds that patched Minecraft+Forge artifact itself, on demand, through the repositories below.

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
    mavenCentral()
}

minecraft {
    // Mojang's official names, which the shared source is written in (and which Forge runs on, see the header).
    mappings("official", minecraftVersion)
}

sourceSets {
    named("main") {
        // Only the platform layer compiles here until the rest of BC has a Forge route. The filter applies to whichever
        // directory Stonecutter points the source set at (src/ raw, or this node's generated copy). The contracts name two
        // vanilla-only api packages (BCFluidStack, the BCCapability token: buildcraft/platform/package-info.java), so those
        // compile too; every other buildcraft.api package stays out because it is not part of the contract.
        java {
            include("buildcraft/platform/**")
            include("buildcraft/api/fluids/**", "buildcraft/api/capability/**")
        }
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
}

// Stonecutter has to emit this node's sources before they compile: a compile against a stale or empty generated copy would
// pass this node's only gate vacuously (the edge bc.fabric states for the same reason).
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No tests and no jar: the node exists only to prove the platform layer compiles against Forge.
tasks.named("test") { enabled = false }
tasks.named("jar") { enabled = false }

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the compile alone. The NeoForge twin in
// bc.neoforge adds unit tests, game tests and the jar. There is no buildAndCollect here: the root never names it, and an
// unqualified `buildAndCollect` runs only on the nodes that have one.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Forge node's share of fullTestSuite: compileJava only (compile-only spike, nothing ships)."
    dependsOn("compileJava")
}
