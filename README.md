## **UNOFFICIAL** BuildCraft for Minecraft 1.21.11

### Reporting an issue

Please open an issue for a bug report only if:

* you are sure the bug is caused by BuildCraft and not by any other mod,
* you have at least one of the following:
  * a crash report, 
  * means of reproducing the bug in question,
  * screenshots/videos/etc. to demonstrate the bug.

**Only bugfixes are planned for this port.**

Please check if the bug has been reported beforehand. Also, provide the version of BuildCraft used - if it's a version compiled from source, link to the commit/tree you complied from.

Please mention if you are using MCPC+, Cauldron, OptiFine, FastCraft or any other mods which optimize or otherwise severely modify the functioning of the Minecraft engine. That is very helpful when trying to reproduce a bug.

BuildCraft, being an open-source project, gives you the right to submit a pull request if a particular fix or feature is important to you. However, if the change in question is major, please contact the team beforehand - we wish to prevent wasted effort.

### Contributing

If you wish to submit a pull request to fix bugs or broken behaviour feel free to do so. 

### Compiling and packaging BuildCraft
1. Ensure that `Java 21` (found [here](https://jdk.java.net/archive/)), `Git` (found [here](http://git-scm.com/)) are installed correctly on your system.
 * Optional: Install `Gradle` (found [here](http://www.gradle.org/downloads)).
2. Create a base directory for the build
3. Clone the BuildCraft repository into 'baseDir/BuildCraft/'
4. Clone (and update) the submodules into 'baseDir/BuildCraft with 'git submodule init' and 'git submodule update'
5. Navigate to basedir/BuildCraft in a shell and run one of two commands:
    * `./gradlew setupCIWorkspace build` to just build a current jar (this may take a while).
    * `./gradlew setupDecompWorkspace` to setup a complete development environment.
    * With `Gradle` installed: use `gradle` instead of `./gradlew`
    * On Windows: use `gradlew.bat` instead of `./gradlew`
6. The compiles and obfuscated module jars will be in 'baseDir/BuildCraft/build/libs/&lt;build number&gt;/modules'

Your directory structure should look like this before running gradle:
***

    baseDir
    \- BuildCraft
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

And like this after running gradle:
***

    basedir
    \- BuildCraft
     |- .gradle
     |- build
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

### Localizations

Localizations can be submitted [here](https://github.com/BuildCraft/BuildCraft-Localization). Localization PRs against
this repository will have to be rejected.

### Depending on BuildCraft

Instructions for depending on BC 7.1.x can be found [here](https://github.com/BuildCraft/BuildCraft/blob/7.1.x/README.md) (for 1.7.10).

8.0.x hasn't been finished yet, so there are no instructions for depending on it :(

The following instructions are for BC 8.0.x (1.21.11):

Add the following to your build.gradle file:
```
repositories {
    maven {
        name "BuildCraft"
        url = "https://mod-buildcraft.com/maven"
    }
}
````

If you want to depend on JUST the API then do this:
````
dependencies {
    implementation "com.mod-buildcraft:buildcraft-api:8.0.+"
}
````

If you want to depend on JUST the lib then do this:
````
dependencies {
    implementation "com.mod-buildcraft:buildcraft-lib:8.0.+"
}
````

If you want to depend on the whole of buildcraft do this:
```
dependencies {
    implementation "com.mod-buildcraft:buildcraft:8.0.+"
}
```
Where `8.0.+` is the desired version of BuildCraft.
