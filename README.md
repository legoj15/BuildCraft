## BuildCraft **UNOFFICIAL**
#### For Minecraft 26.1/26.1.1 on NeoForge

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

BuildCraft, being an open-source project, gives you the right to submit a pull request if a particular fix or feature is important to you. However, if the change in question is major, please contact the team beforehand - we wish to prevent wasted effort.

### Contributing

If you wish to submit a pull request to fix bugs or broken behaviour feel free to do so. 

### Compiling and packaging BuildCraft
1. Ensure that `Java 25` (found [here](https://jdk.java.net/archive/)) and optionally `Git` (found [here](http://git-scm.com/)) are installed correctly on your system.
 * Optional: Install `Gradle` (found [here](http://www.gradle.org/downloads)).
2. Clone the BuildCraft repository: `git clone https://github.com/legoj15/BuildCraft.git` or [download the latest zip](https://github.com/legoj15/BuildCraft/archive/refs/heads/8.0.x-26.1.1.zip)
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

### Localizations

Localizations can be submitted [here](https://github.com/BuildCraft/BuildCraft-Localization).

### Depending on BuildCraft

As this is an unofficial port, it is not currently hosted on the official BuildCraft Maven repository.

To depend on BuildCraft in your own mod, you will first need to compile it locally and publish it to your local Maven repository:
1. Clone this repository locally.
2. Run `./gradlew publishToMavenLocal` (or `.\gradlew.bat publishToMavenLocal` on Windows).

Then, add `mavenLocal()` to your repositories block in your `build.gradle`:
```gradle
repositories {
    mavenLocal()
}
```

Since the 26.1.1 port uses a monolithic project structure, you will depend on the entire BuildCraft artifact rather than separate API or lib modules:
```gradle
dependencies {
    implementation "com.mod-buildcraft:buildcraft:8.0.+"
}
```
Where `8.0.+` is the desired version of BuildCraft.
