## BuildCraft **UNOFFICIAL**
#### For Minecraft 26.1/26.1.1/26.1.2 on NeoForge

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
2. Clone the BuildCraft repository: `git clone https://github.com/legoj15/BuildCraft.git` or [download the latest zip](https://github.com/legoj15/BuildCraft/archive/refs/heads/26.1.x.zip)
3. Navigate to the cloned repository in a shell: `cd BuildCraft`
4. Run the Gradle build command:
    * On Linux/Unix/Mac: `./gradlew build`
    * On Windows: `.\gradlew.bat build`
    * With `Gradle` installed: use `gradle` instead of the wrapper.
5. Once the build finishes, the compiled mod jar will be in `build/libs/`.

### Running in a Development Environment
To test the mod locally, use the NeoForge run tasks:
* Run the Client: `./gradlew runClient` (or `.\gradlew.bat runClient` on Windows)
* Run the Server: `./gradlew runServer` (or `.\gradlew.bat runServer` on Windows)
On Windows 11, `runClientAndServer.ps1` will launch both at the same time to reduce the amount of steps.

Your directory structure will look like a standard monolithic Java project:
***

    BuildCraft
     |- build            (Generated after compiling)
     |- run              (Generated when running the client/server)
     \- src
      |- main            (Mod source code and resources)
      \- test            (Test source code)

***