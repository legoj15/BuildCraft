## BuildCraft **UNOFFICIAL**
#### For Minecraft 1.21.11, 26.1, 26.1.1, and 26.1.2 on NeoForge

### Reporting an issue

Please open an issue if you encounter anything unexpected! For crashes or bugs/glitches, please ensure:

* you are positive the bug is caused by BuildCraft and not by any other mod (bugged interactions with other mods are a valid issue),
* you have at least one of the following:
  * a crash report or log file/entry, 
  * a step-by-step means of reproducing the bug in question, i.e. "What were you doing at the time of the issue?",
  * and/or screenshots/videos/etc. to demonstrate the bug.

**Only bugfixes are planned for this port.**

Please check if the bug has been reported beforehand. Also, provide the version of BuildCraft used - if it's a version compiled from source, link to the commit/tree you complied from.

Please mention if you are using any other mods, especially mods which optimize or otherwise severely modify the functioning of the Minecraft engine like MCPC+, Cauldron, OptiFine, FastCraft, Sodium, etc. That is very helpful when trying to reproduce a bug.

### Compiling and packaging BuildCraft
1. Install `Java 25` (Microsoft OpenJDK, recommended by the NeoForge team, available [here](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25)) and optionally `Git` (found [here](http://git-scm.com/)). Note: NeoForge's moddev tooling also uses Java 21 internally for its NFRT tasks (asset download, run preparation), but you don't need to install it yourself since the build is configured with the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains), so Gradle auto-downloads JDK 21 into its user home on first build. Java 25 is the only JDK you need to install manually.
 * Optional: Install `Gradle` (found [here](http://www.gradle.org/downloads)).
2. Clone the BuildCraft repository: `git clone https://github.com/legoj15/BuildCraft.git` or [download the latest zip](https://github.com/legoj15/BuildCraft/archive/refs/heads/main.zip)
3. Navigate to the cloned repository in a shell: `cd BuildCraft`
4. Build the mod. BuildCraft uses [Stonecutter](https://stonecutter.kikugie.dev) to target Minecraft from one source tree, one *node* per MC line. The 26.1.x line is a single node (compiled against 26.1.2) that ships as **one jar covering 26.1, 26.1.1 and 26.1.2**. Build with the `buildAndCollect` task:
    * On Linux/Unix/Mac: `./gradlew buildAndCollect`
    * On Windows: `.\gradlew.bat buildAndCollect`
    * The Gradle wrapper pins the required Gradle version (9.x); you do **not** need Gradle installed.
5. Once the build finishes, the jar — `BCunofficial-<version>+mc26.1.2.jar`, compatible with all of 26.1.x — is in `build/libs/<version>/`.

### Running in a Development Environment
To test the mod locally, use the NeoForge run tasks. These are **per-node**, so prefix them with the Minecraft version you want (e.g. `:26.1.2:`):
* Run the Client: `./gradlew :26.1.2:runClient` (or `.\gradlew.bat :26.1.2:runClient` on Windows)
* Run the Server: `./gradlew :26.1.2:runServer` (or `.\gradlew.bat :26.1.2:runServer` on Windows)
Valid versions are `:1.21.11:` and `:26.1.2:`.

On Windows 11, `runClientAndServer.ps1` will launch both at the same time (against the `:26.1.2` node) to reduce the amount of steps.

Your directory structure will look like a standard monolithic Java project:
***

    BuildCraft
     |- build            (Generated after compiling)
     |- run              (Generated when running the client/server)
     |- versions         (Stonecutter nodes, one per MC line; today just 26.1.2)
     |   \- 26.1.2
     \- src              (Shared source; one jar covers all of 26.1.x)
      |- main            (Mod source code and resources)
      \- test            (Test source code)

***
