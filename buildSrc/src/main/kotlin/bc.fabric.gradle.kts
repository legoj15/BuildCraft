// The Fabric half of the per-node build: applied by build.gradle.kts as `bc.$loader` on every node whose `loader` is
// fabric (today only 26.3-fabric). Loom's version is pinned in buildSrc/build.gradle.kts.
//
// Phase 2 spike (#100): COMPILE ONLY, NOTHING SHIPS. The node compiles buildcraft/platform/** from main (the
// loader-neutral contracts, the binder with its fabric arm live, and buildcraft.platform.fabric) plus the vanilla-only
// BCFluidStack in buildcraft/api/fluids, and nothing else:
// no resources, no tests, no jar, no buildAndCollect. Its share of the root fullTestSuite is compileJava alone.
//
// Loom on 26.x does not remap: Minecraft ships unobfuscated, so there is no mappings line and the mod dependencies
// go on the plain Java configurations rather than the mod* remapping ones.

plugins {
    id("net.fabricmc.fabric-loom")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
val minecraftVersion = property("minecraft_version") as String
val fabricLoaderVersion = property("fabric_loader_version") as String
val fabricApiVersion = property("fabric_api_version") as String

sourceSets {
    named("main") {
        // Only the platform layer compiles here until the rest of BC has a Fabric route. The filter applies to
        // whichever directory Stonecutter points the source set at (src/ raw, or this node's generated copy).
        // buildcraft/api/fluids is the loader-neutral BCFluidStack the platform converts (vanilla types only).
        java { include("buildcraft/platform/**", "buildcraft/api/fluids/**") }
        // BC's resources are NeoForge's (mods.toml, the data down-ports); a Fabric node ships none yet.
        resources { exclude("**") }
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

// Loom registers this itself; declared so the source of fabric-loader / fabric-api is visible here.
repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

// Stonecutter already runs stonecutterGenerate before compileJava (observed on a clean versions/26.3-fabric/build),
// but the edge is stated, as bc.neoforge does for createMinecraftArtifacts: a compile against a stale or empty
// generated copy would pass this node's only gate vacuously.
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }

// No tests and no jar: the node exists only to prove the platform layer compiles against Fabric.
tasks.named("test") { enabled = false }
tasks.named("jar") { enabled = false }

// What the root `fullTestSuite` (stonecutter.gradle.kts) runs on this node: the compile alone. The NeoForge twin in
// bc.neoforge adds unit tests, game tests and the jar.
tasks.register("nodeVerification") {
    group = "verification"
    description = "This Fabric node's share of fullTestSuite: compileJava and the class-tweaker validation (nothing ships yet)."
    dependsOn("compileJava", "validateAccessWidener")
}
