// Central (per-node) build script. Runs once per Stonecutter version node.
// It holds only what every loader shares (the Stonecutter constants and replacements, the Java toolchain, javac
// flags), then applies the node's loader convention plugin, `bc.$loader` (buildSrc/src/main/kotlin/): bc.neoforge
// brings ModDevGradle and everything NeoForge-specific, bc.fabric brings Loom. Never both on one node, and never two
// plugin DSLs inline behind conditionals (docs/platform-layer.md). The loader plugins' versions are pinned in
// buildSrc/build.gradle.kts.
import buildcraft.gradle.BcNode

plugins {
    java
}

// Per-node Java toolchain: 26.1+ needs Java 25; the 1.21.11 node (pre-CalVer) overrides to 21.
val javaVersion = property("java_version") as String
// The mod loader this node builds against (root gradle.properties, overridable per node). It becomes the
// Stonecutter constants `neoforge` / `fabric` below and picks the convention plugin; it is never derived from the
// node name.
val loader = property("loader") as String
if (loader != "neoforge" && loader != "fabric") {
    throw GradleException("loader must be 'neoforge' or 'fabric', not '$loader'")
}
// True only on MC lines below 1.21.11 (the 1.21.10 and 1.21.1 nodes). Gates the back-compat source
// replacements + the extra deps that the 26.1.2 / 1.21.11 nodes already get transitively.
val isPre1_21_11 = stonecutter.eval(stonecutter.current.version, "<1.21.11")
// True only on the 1.21.1 node (1.21.10/1.21.11/26.1 have the newer APIs). Gates cliffs that
// landed AT 1.21.10 — e.g. the model-data package move.
val isPre1_21_10 = stonecutter.eval(stonecutter.current.version, "<1.21.10")
// True on every node below 26.3: gates the loot/advancement data down-port (DataDownportPre263).
val isPre26_3 = stonecutter.eval(stonecutter.current.version, "<26.3")
// True on the 1.21.x nodes: Minecraft is obfuscated below 26.1, so a Fabric node there needs Loom's remap mode.
val isPre26_1 = stonecutter.eval(stonecutter.current.version, "<26.1")
// Handed to the convention plugin, which cannot reach Stonecutter's classes from buildSrc.
extensions.add(BcNode::class.java, "bcNode", BcNode(isPre1_21_11, isPre1_21_10, isPre26_3, isPre26_1))

// ─── Stonecutter source replacements ─────────────────────────────────────────
// The pre-1.21.11 nodes (1.21.10 and 1.21.1) reverse Mojang's 1.21.11 ResourceLocation->Identifier
// rename tree-wide, so the shared source — written in the `Identifier` form used by the 1.21.11+
// nodes — compiles against their older `ResourceLocation`. Word-boundary regex (\b) avoids
// clobbering substrings like Character.isJavaIdentifierStart, IdentifierException, or REI's
// CategoryIdentifier. Every rule is a PAIR, and `direction` picks which half runs: true on the
// pre-1.21.11 nodes (Identifier -> ResourceLocation), false everywhere else, where the reverse half
// runs (ResourceLocation -> Identifier) instead of nothing. Applied during each node's
// stonecutterGenerate, so it works under per-node `:1.21.10:compileJava` without switching the
// active node.
// The rewrite is plain text over the whole file, COMMENTS INCLUDED: a comment that contrasts the two
// names, or says which one a given line uses, reads wrong on one side. Prose may name the type only
// where it tracks the code on every node (a {@link} to the class in use); otherwise say "the id class" /
// "the pre-1.21.11 name". The same goes for the other rules below (render-type packages, the 1.21.1
// accessor renames, `helper.assertTrue(`).
stonecutter {
    // Loader constants: `//? if neoforge {` is live on a node whose `loader` is neoforge, `//? if fabric {` on a fabric
    // one. match() sets the key equal to `loader` true and the other false (verified against 0.9.8's
    // ConstantContainer). Directives in common main code stay about MC versions; a loader arm belongs only in a
    // buildcraft/platform/<loader>/ file, a binder holder, or one of the documented exceptions
    // (docs/platform-layer.md, Rules for directives). Shared test code is looser: game-test
    // bodies carry loader arms where a loader's own route differs (docs/platform-layer.md, Phase 2c).
    constants.match(loader, "neoforge", "fabric")
    replacements {
        regex {
            direction.set(isPre1_21_11)
            replace("\\bIdentifier\\b", "ResourceLocation", "\\bResourceLocation\\b", "Identifier")
        }
        regex {
            direction.set(isPre1_21_11)
            replace("\\bIdentifierArgument\\b", "ResourceLocationArgument", "\\bResourceLocationArgument\\b", "IdentifierArgument")
        }
        // RenderType moved out of the `rendertype` subpackage AT 1.21.11. This is a clean,
        // reversible package-move (same class name) so it rides as a replacement; `\b` stops
        // before RenderTypeS (which was a separate split-out class on 1.21.11 — handled via
        // directives, since its members merged back onto RenderType on 1.21.10).
        regex {
            direction.set(isPre1_21_11)
            replace(
                "net\\.minecraft\\.client\\.renderer\\.rendertype\\.RenderType\\b", "net.minecraft.client.renderer.RenderType",
                "net\\.minecraft\\.client\\.renderer\\.RenderType\\b", "net.minecraft.client.renderer.rendertype.RenderType"
            )
        }
        // RenderTypes (the stored render-type accessors) was split out as its own class AT 1.21.11;
        // on 1.21.10 those statics live back on RenderType. This handles the fully-qualified call
        // sites (rendertype.RenderTypes.foo() -> renderer.RenderType.foo()); the handful of files that
        // import + use it BARE are converted via //? directives (a bare RenderTypes->RenderType swap
        // is NOT safely reversible, since it would clobber legitimate RenderType usages on 26.1/1.21.11).
        regex {
            direction.set(isPre1_21_11)
            replace(
                "net\\.minecraft\\.client\\.renderer\\.rendertype\\.RenderTypes\\.", "net.minecraft.client.renderer.RenderType.",
                "net\\.minecraft\\.client\\.renderer\\.RenderType\\.", "net.minecraft.client.renderer.rendertype.RenderTypes."
            )
        }
        // Game-test assertions: 1.21.10's GameTestHelper.assertTrue/assertFalse take a Component message
        // (1.21.11+/26.1 and 1.21.1 take a String). Route helper.assert*(...) through GameTestUtil on the
        // pre-1.21.11 nodes: it wraps the String in a Component on 1.21.10 and passes it straight through on
        // 1.21.1. `\bhelper\.` matches the GameTestHelper param (never JUnit Assertions.*). Only test sources
        // contain `helper.assertTrue(`, so this is inert for main code.
        regex {
            direction.set(isPre1_21_11)
            replace(
                "\\bhelper\\.assertTrue\\(", "buildcraft.lib.test.GameTestUtil.assertTrue(helper, ",
                "\\bbuildcraft\\.lib\\.test\\.GameTestUtil\\.assertTrue\\(helper, ", "helper.assertTrue("
            )
        }
        regex {
            direction.set(isPre1_21_11)
            replace(
                "\\bhelper\\.assertFalse\\(", "buildcraft.lib.test.GameTestUtil.assertFalse(helper, ",
                "\\bbuildcraft\\.lib\\.test\\.GameTestUtil\\.assertFalse\\(helper, ", "helper.assertFalse("
            )
        }
        // 1.21.1 ONLY: the model-data API (ModelData/ModelProperty/ModelDataManager) lives under
        // `client.model.data` on 1.21.1; it was moved up to `model.data` at 1.21.10. Pure package move,
        // same API. Specific + reversible; canonical src only ever writes `model.data`, so on every other
        // node the reverse half matches nothing. No substring overlap (the moved form inserts `.client` mid-path).
        regex {
            direction.set(isPre1_21_10)
            replace(
                "net\\.neoforged\\.neoforge\\.model\\.data", "net.neoforged.neoforge.client.model.data",
                "net\\.neoforged\\.neoforge\\.client\\.model\\.data", "net.neoforged.neoforge.model.data"
            )
        }
        // 1.21.1 ONLY: LevelHeightAccessor.getMaxY()/getMinY() (the short accessors, present from 1.21.2)
        // were getMaxBuildHeight()/getMinBuildHeight() on 1.21.1. All call sites are `level.getMaxY()` /
        // `level.getMinY()` (no BuildCraft class defines either), so the literal `.getMaxY()`/`.getMinY()`
        // form is unambiguous. Specific + reversible; on every other node the reverse half matches nothing in canonical src.
        regex {
            direction.set(isPre1_21_10)
            replace(
                "\\.getMaxY\\(\\)", ".getMaxBuildHeight()",
                "\\.getMaxBuildHeight\\(\\)", ".getMaxY()"
            )
        }
        regex {
            direction.set(isPre1_21_10)
            replace(
                "\\.getMinY\\(\\)", ".getMinBuildHeight()",
                "\\.getMinBuildHeight\\(\\)", ".getMinY()"
            )
        }
        // 1.21.1 ONLY: Minecraft.getDeltaTracker() (added when getTimer() was renamed at 1.21.2) was
        // getTimer() on 1.21.1 — both return DeltaTracker (with getGameTimeDeltaPartialTick). Every BC
        // call site receives Minecraft (`Minecraft.getInstance().getDeltaTracker()` / `mc.getDeltaTracker()`)
        // and 1.21.1 defines no other getDeltaTracker(), so the literal `.getDeltaTracker()` is unambiguous.
        // Specific + reversible; on every other node the reverse half matches nothing in canonical src.
        regex {
            direction.set(isPre1_21_10)
            replace(
                "\\.getDeltaTracker\\(\\)", ".getTimer()",
                "\\.getTimer\\(\\)", ".getDeltaTracker()"
            )
        }

        // 1.21.1 ONLY: JEI's IRecipeCatalystRegistration.addCraftingStation(IRecipeType, ItemLike...)
        // (JEI 26.x / MC 1.21.10+) was addRecipeCatalysts(RecipeType, ItemLike...) on JEI 19.27 / 1.21.1.
        // Same arg shape (the recipe-type constants already resolve per-node via the recipe.types gate),
        // so the literal method-name swap suffices. JEI-specific name — no BC collision. Reversible;
        // on every other node the reverse half matches nothing in canonical src.
        regex {
            direction.set(isPre1_21_10)
            replace(
                "\\.addCraftingStation\\(", ".addRecipeCatalysts(",
                "\\.addRecipeCatalysts\\(", ".addCraftingStation("
            )
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    // Surface the full cross-cliff error set during the 1.21.11 port (javac caps at 100 by default).
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000", "-Xmaxwarns", "200"))
    // Opt-in lint audit: `-PbcLint=deprecation` (also =all, =unchecked, =removal, ...) surfaces the
    // javac warning detail that's otherwise just summarised ("recompile with -Xlint"). Default builds
    // are unchanged — no extra warnings/noise — so this is purely a diagnostic switch.
    if (project.hasProperty("bcLint")) {
        options.compilerArgs.addAll(listOf("-Xlint:${project.property("bcLint")}", "-Xmaxwarns", "5000"))
    }
}

// ─── Loader ──────────────────────────────────────────────────────────────────
// A Fabric node first picks Loom's mode: both ids apply the same `fabric-loom` plugin (which bc.fabric's own plugins
// block then names, a no-op that gives it Loom's typed DSL), but the no-remap id must come first on the unobfuscated
// 26.x lines and the remap id (Mojang mappings, mod* configurations, remapJar) on the obfuscated 1.21.x lines. It has
// to happen here: a convention plugin's plugins block runs before any of its code could choose.
if (loader == "fabric") {
    apply(plugin = if (isPre26_1) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
}
apply(plugin = "bc.$loader")

// ─── Full pre-release verification ───────────────────────────────────────────
// `fullTestSuite` is defined ONCE on the ROOT (stonecutter.gradle.kts), NOT per-node: it enumerates
// every node from the tree and depends on each one's `nodeVerification` (registered by the convention plugin:
// `test` + `runGameTestServer` + `buildAndCollect` on a NeoForge node; the compile, the deprecated-API guard
// (`checkDeprecations`), class tweaker, resources, jar, `buildAndCollect` and `runGameTest` on a Fabric one) so coverage
// can't silently narrow to a single line. Run it with `./gradlew fullTestSuite`. For a single node during
// development, run that node's tasks directly, e.g. `:1.21.1:test :1.21.1:runGameTestServer`.
