// Central (per-node) build script. Runs once per Stonecutter version node.
// moddev's version is declared with `apply false` in stonecutter.gradle.kts (the controller).
plugins {
    id("net.neoforged.moddev")
}

// Per-node values, resolved from versions/<node>/gradle.properties (inheriting root gradle.properties).
val minecraftVersion = property("minecraft_version") as String
val neoVersion = property("neo_version") as String
val jeiVersion = property("jei_version") as String
val modVersion = property("mod_version") as String
val mcJarTag = property("mc_jar_tag") as String
// Per-node Java toolchain: 26.1+ needs Java 25; the 1.21.11 node (pre-CalVer) overrides to 21.
val javaVersion = property("java_version") as String
// Resolved at project scope (NOT inside tasks.processResources {}, where `property()` would
// resolve against the task, not the project). update_branch is root-only (inherited by nodes).
val updateBranch = property("update_branch") as String
val neoDepRange = property("neo_dep_range") as String
val mcDepRange = property("mc_dep_range") as String
// True only on MC lines below 1.21.11 (i.e. the 1.21.10 node). Gates the back-compat source
// replacements + the extra deps that the 26.1.2 / 1.21.11 nodes already get transitively.
val isPre1_21_11 = stonecutter.eval(stonecutter.current.version, "<1.21.11")
// True only on the 1.21.1 node (1.21.10/1.21.11/26.1 have the newer APIs). Gates cliffs that
// landed AT 1.21.10 — e.g. FMLEnvironment.dist became the getDist() accessor.
val isPre1_21_10 = stonecutter.eval(stonecutter.current.version, "<1.21.10")

// ─── Stonecutter source replacements ─────────────────────────────────────────
// The 1.21.10 node (ONLY) reverses Mojang's 1.21.11 ResourceLocation->Identifier rename
// tree-wide, so the shared source — written in the canonical `Identifier` form used by the
// 26.1.2 + 1.21.11 nodes — compiles against 1.21.10's older `ResourceLocation`. Word-boundary
// regex (\b) avoids clobbering substrings like Character.isJavaIdentifierStart, IdentifierException,
// or REI's CategoryIdentifier. `direction` gates it: false on 26.1.2/1.21.11 => no-op (those nodes
// are untouched); true only on 1.21.10. Applied during each node's stonecutterGenerate, so it works
// under per-node `:1.21.10:compileJava` without switching the active node.
stonecutter {
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
        // (1.21.11+/26.1 take a String). Route helper.assert*(...) through GameTestUtil (which wraps the
        // String) on the 1.21.10 node only. `\bhelper\.` matches the GameTestHelper param (never JUnit
        // Assertions.*). Only test sources contain `helper.assertTrue(`, so this is inert for main code.
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
        // 1.21.1 ONLY: FMLEnvironment.getDist() (the accessor, added at 1.21.10) -> the older public
        // static `dist` field. Specific + reversible; no collision since the canonical source only ever
        // writes getDist(). Inert on 1.21.10/1.21.11/26.1 (direction=false there).
        regex {
            direction.set(isPre1_21_10)
            replace(
                "FMLEnvironment\\.getDist\\(\\)", "FMLEnvironment.dist",
                "FMLEnvironment\\.dist\\b", "FMLEnvironment.getDist()"
            )
        }
        // 1.21.1 ONLY: the model-data API (ModelData/ModelProperty/ModelDataManager) lives under
        // `client.model.data` on 1.21.1; it was moved up to `model.data` at 1.21.10. Pure package move,
        // same API. Specific + reversible; released nodes only ever write `model.data` so the reverse is
        // inert there (direction=false). No substring overlap (the moved form inserts `.client` mid-path).
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
        // form is unambiguous. Specific + reversible; inert on 1.21.10/1.21.11/26.1 (direction=false).
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
        // Specific + reversible; inert on 1.21.10/1.21.11/26.1 (direction=false).
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
        // inert on 1.21.10/1.21.11/26.1 (direction=false).
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

repositories {
    mavenLocal()
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me/")
    mavenCentral()
}

sourceSets {
    val main by getting
    named("test") {
        compileClasspath += main.compileClasspath + main.output
        runtimeClasspath += main.runtimeClasspath + main.output
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
            // Enables BCLib.DEV-gated affordances (Power Tester block, /bcsoundtest command, ...).
            systemProperty("buildcraft.dev", "true")
            // Profiling diagnostics (drop spark NeoForge jar into run/mods/ for /spark profiler).
            // Remove these args once perf investigation is done — they write a continuous GC log
            // plus a JDK Flight Recorder dump (run/bc-profile.jfr) when the client exits.
            jvmArguments.addAll(
                listOf(
                    "-Xlog:gc*:file=gc.log:time,uptime,level,tags",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:FlightRecorderOptions=stackdepth=256",
                    "-XX:StartFlightRecording=name=BuildCraft,settings=profile,filename=bc-profile.jfr,dumponexit=true"
                )
            )
        }
        register("server") {
            server()
            systemProperty("buildcraft.dev", "true")
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enableGameTest", "true")
            // Power Tester block (BCLib.DEV-gated) is used as the sink in
            // PipeFlowPowerTester — it accepts unlimited MJ so the test isn't
            // race-sensitive to a bounded battery filling up between ticks.
            systemProperty("buildcraft.dev", "true")
            gameDirectory = project.file("run-gameTestServer")
        }
    }

    mods {
        register("buildcraftunofficial") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["test"])
        }
    }
}

dependencies {
    // JEI
    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$minecraftVersion-neoforge-api:$jeiVersion")
    runtimeOnly("mezz.jei:jei-$minecraftVersion-neoforge:$jeiVersion")

    // jspecify @Nullable/@NonNull: NeoForge exposes it transitively on 1.21.11+ / 26.1, but the
    // 21.10.x line does not put it on the compile classpath — add it explicitly for that node.
    if (isPre1_21_11) compileOnly("org.jspecify:jspecify:1.0.0")

    // REI — No compatible versions for MC 26.1 yet
    // compileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:16.0.799")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Exclude REI compat modules from compilation — no MC 26.1 versions available
tasks.withType<JavaCompile>().configureEach {
    exclude("**/compat/rei/**")
    // Surface the full cross-cliff error set during the 1.21.11 port (javac caps at 100 by default).
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000", "-Xmaxwarns", "200"))
}

tasks.test {
    useJUnitPlatform()
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
    archiveBaseName = "BCunofficial"
    // Axis A (mod_version, CalVer) + Axis B (minecraft_version) as a SemVer build-metadata tag.
    // The +mc<mc> coordinate lives ONLY in the jar filename — never in the mods.toml version —
    // because NeoForge orders versions Maven-style, where '+...' would corrupt update ordering.
    archiveVersion = "$modVersion+mc$mcJarTag"
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

// `./gradlew buildAndCollect` at the Tree root cascades to every version node and
// gathers each node's jar into build/libs/<mod_version>/ (the +mc<mc> names keep them distinct).
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.jar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    dependsOn("assemble")
}

// ─── Full pre-release verification ───────────────────────────────────────────
// One command for the lot: JUnit unit tests + headless NeoForge game tests (this node) + the production
// boot+connect matrix. The matrix is the single root task `runReleaseMatrix`, so it runs ONCE even when
// `gradlew fullTestSuite` cascades across nodes — and it auto-skips when the (gitignored, machine-specific)
// testing/ harness isn't present, so third-party clones just run test + game tests.
tasks.register("fullTestSuite") {
    group = "verification"
    description = "Unit tests + headless game tests + production boot/connect matrix (matrix auto-skips if the testing/ harness is absent)."
    // buildAndCollect produces the fresh +mc jars the matrix stages onto the server/client. It's listed
    // here (not left to the script) so the OUTER Gradle builds them — the matrix runs with -SkipBuild so it
    // never starts a nested `gradlew buildAndCollect`, which would deadlock on this build's project lock.
    dependsOn("test", "runGameTestServer", "buildAndCollect", rootProject.tasks.named("runReleaseMatrix"))
    // Build fresh jars + run cheap in-dev checks first; the long production matrix runs last.
    rootProject.tasks.named("runReleaseMatrix").configure {
        mustRunAfter(tasks.named("test"), tasks.named("runGameTestServer"), tasks.named("buildAndCollect"))
    }
}
