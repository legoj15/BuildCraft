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
