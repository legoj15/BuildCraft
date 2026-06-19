## BuildCraft **UNOFFICIAL**
#### For Minecraft 1.21.1, 1.21.10, 1.21.11, and 26.1.2 on NeoForge

### Reporting an issue

| MC Version | Support | Notes |
|---|---|---|
| 26.1.2 | ✔️ | Latest version (primary support) |
| 26.1.1 | ⚠️ | Not directly supported (may not run) |
| 26.1 | ⚠️ | Not directly supported (may not run) |
| 1.21.11 | ✔️ | Currently supported (low priority) |
| 1.21.10 | ✔️ | Currently supported (low priority) |
| 1.21.2-9 | ❌ | Not planned |
| 1.21.1 | ✔️ | Currently supported |
| ≤1.21 | ❌ | Not planned |

If you're on a supported version, please open an issue if you encounter anything unexpected! For crashes or bugs/glitches, please ensure:

* you are positive the bug is caused by BuildCraft and not by any other mod (bugged interactions with other mods are a valid issue),
* you have at least one of the following:
  * a crash report or log file/entry, 
  * a step-by-step means of reproducing the bug in question, i.e. "What were you doing at the time of the issue?",
  * and/or screenshots/videos/etc. to demonstrate the bug.

**Only bugfixes are planned for this port.**

Please check if the bug has been reported beforehand. Also, provide the version of BuildCraft used - if it's a version compiled from source, link to the commit/tree you complied from.

Please mention if you are using any other mods, especially mods which optimize or otherwise severely modify the functioning of the Minecraft engine like MCPC+, Cauldron, OptiFine, FastCraft, Sodium, etc. That is very helpful when trying to reproduce a bug.

### Compiling and packaging BuildCraft
1. Install `Java 25` for 26.1.2 and newer, and `Java 21` for 1.21.x (Microsoft OpenJDK, recommended by the NeoForge team, available [here](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25)) and optionally `Git` (found [here](http://git-scm.com/)). Note: NeoForge's moddev tooling also uses Java 21 internally for its NFRT tasks (asset download, run preparation), but you don't need to install it yourself since the build is configured with the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains), so Gradle auto-downloads JDK 21 into its user home on first build. Java 25 is the only JDK you need to install manually.
 * Optional: Install `Gradle` (found [here](http://www.gradle.org/downloads)).
2. Clone the BuildCraft repository: `git clone https://github.com/legoj15/BuildCraft.git` or [download the latest zip](https://github.com/legoj15/BuildCraft/archive/refs/heads/main.zip)
3. Navigate to the cloned repository in a shell: `cd BuildCraft`
4. Build the mod. BuildCraft uses [Stonecutter](https://stonecutter.kikugie.dev) to target Minecraft from one source tree, one *node* per MC line. Build with the `buildAndCollect` task:
    * On Linux/Unix/Mac: `./gradlew buildAndCollect`
    * On Windows: `.\gradlew.bat buildAndCollect`
    * The Gradle wrapper pins the required Gradle version (9.x); you do **not** need Gradle installed.
5. Once the build finishes, the jar `BCunofficial-<version>+mc26.1.2.jar` is in `build/libs/<version>/`.

### Running in a Development Environment
To test the mod locally, use the NeoForge run tasks. These are **per-node**, so prefix them with the Minecraft version you want (e.g. `:26.1.2:`):
* Run the Client: `./gradlew :26.1.2:runClient` (or `.\gradlew.bat :26.1.2:runClient` on Windows)
* Run the Server: `./gradlew :26.1.2:runServer` (or `.\gradlew.bat :26.1.2:runServer` on Windows)

Valid versions are `:1.21.1:`, `:1.21.10:`, `:1.21.11:` and `:26.1.2:`.

On Windows 11, `runClientAndServer.ps1` will launch both at the same time (against the `:26.1.2` node) to reduce the amount of steps.

Your directory structure will look like a standard monolithic Java project:
***

    BuildCraft
     |- build            (Generated after compiling)
     |- run              (Generated when running the client/server)
     |- versions         (Stonecutter nodes, one per MC line)
     |   \- 26.1.2
     \- src              (Shared source between all versions)
      |- main            (Mod source code and resources)
      \- test            (Test source code)

***

### For mod / addon developers (integrating with BuildCraft)

This fork of BuildCraft's public API is the **`buildcraft.api`** package. It exposes the systems other mods are most
likely to build on: pipes & transport, MJ power (`buildcraft.api.mj`), gates/statements
(triggers & actions), facades, recipes (assembly / refinery / integration), fuels & coolants, robots, and
the schematic/blueprint types. I don't recommend trying to brute-force anything outside the scope of the API.

With that said, there is currently **no separate `-api` jar**. Compile against the full mod jar, and the
installed mod provides the classes at runtime. The jars are published on
[GitHub Releases](https://github.com/legoj15/BuildCraft/releases) (and on CurseForge / Modrinth), so the
lowest-friction way to depend on one without hosting a Maven repo is the Modrinth Maven or CurseMaven proxy:

```gradle
// build.gradle(.kts) of YOUR mod (NeoForge / ModDevGradle or NeoGradle)
repositories {
    maven { url = "https://api.modrinth.com/maven" }   // Modrinth Maven
    // or: maven { url = "https://cursemaven.com" }     // CurseMaven
}
dependencies {
    // Compile against BuildCraft's API; the installed mod provides the classes at runtime.
    // <version> is the Modrinth version id for the build matching your MC line (see below).
    compileOnly "maven.modrinth:buildcraft-unofficial:<version>"
    // For in-dev testing (runClient/runServer), put the real mod on the dev runtime classpath:
    runtimeOnly "maven.modrinth:buildcraft-unofficial:<version>"
    // CurseMaven coordinate form instead (1499367 is the CurseForge project id):
    //   "curse.maven:buildcraft-unofficial-1499367:<fileId>"
}
```

The `<version>` / `<fileId>` segment is is important because not only are there multiple versions, there are 
different builds of those versions targeting other Minecraft versions:
[Modrinth versions](https://modrinth.com/mod/buildcraft-unofficial/versions) (the version id is the last
segment of a version's page URL) or
[CurseForge files](https://www.curseforge.com/minecraft/mc-mods/buildcraft-unofficial/files) (the file id is
the number in a file's URL).

Some really good to know information:

1. **Match the Minecraft line.** Each BuildCraft jar is tagged `+mc<version>` and pins that line's APIs (for
   example, fluid handling differs across the 1.21.1 cliff). Depend on the jar built for the MC version you
   target.
2. **Compile-only; never shade/bundle.** The full mod already contains `buildcraft.api.*` at runtime under the
   single `buildcraftunofficial` mod id. Bundling the jar into your own (`implementation`, `jarJar`, shadow)
   duplicates those classes and causes load errors. Use `compileOnly` + `runtimeOnly` only.
3. **Same version at compile and runtime.** You compile against signatures the installed mod must still
   provide, so build against the same `mod_version` you expect players to run, and treat a major-version bump
   as a potential API break.
4. **Use bare Gradle configurations.** BuildCraft (NeoForge/ModDevGradle) jars are plain — `compileOnly` /
   `runtimeOnly` are correct. Do **not** use `modImplementation` or `fg.deobf(...)`; those are Fabric Loom /
   old ForgeGradle idioms and will not resolve.
5. **Registries are empty without the mod.** API singletons (the MJ effect manager, fuel/coolant, statement,
   and pipe registries) are populated by the live mod at init. If you write tests that *exercise* them, put
   the full jar on the test runtime classpath (`testRuntimeOnly`) — against a bare API they are empty by
   design. (The MJ ⇄ RF conversion config falls back to BuildCraft's own defaults.)
6. **Mind the toolchain floor.** The 26.1.2 / 1.21.11 jars are Java 25 bytecode; the 1.21.10 / 1.21.1 jars are
   Java 21. Compile with a JDK at least matching the jar you target.
