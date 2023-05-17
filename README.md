# Iris

The master branch is for the latest version of minecraft.

# [Support](https://discord.gg/3xxPTpT) **|** [Documentation](https://docs.volmit.com/iris/) **|** [Git](https://github.com/IrisDimensions)

# Building

Building Iris is fairly simple, though you will need to setup a few things if your system has never been used for java
development.

Consider supporting our development by buying Iris on spigot! We work hard to make Iris the best it can be for everyone.

## Preface: if you need help compiling and you are a developer / intend to help out in the community or with development we would love to help you regardless in the discord! however do not come to the discord asking for free copies, or a tutorial on how to compile.

### Command Line Builds

1. Install [Java JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-17.0.1` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 17
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory
3. If this is your first time building Iris for MC 1.18+ run `gradlew setup` inside the root Iris project folder.
   Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
4. Once the project has setup, run `gradlew iris`
5. The Iris jar will be placed in `Iris/build/Iris-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!

### IDE Builds (for development)

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 17 (in settings, search for gradle)
* Add a build line in the build.gradle for your own build task to directly compile Iris into your plugins folder if you
  prefer.
* Resync the project & run your newly created task (under the development folder in gradle tasks!)

# Iris Toolbelt

Everyone needs a tool-belt.

```java
package com.volmit.iris.core.tools;

// Get IrisDataManager from a world
IrisToolbelt.access(anyWorld).getCompound().getData();

// Get Default Engine from world
IrisToolbelt.access(anyWorld).getCompound().getDefaultEngine();

// Get the engine at the given height
IrisToolbelt.access(anyWorld).getCompound().getEngineForHeight(68);

// IS THIS THING ON?
boolean yes=IrisToolbelt.isIrisWorld(world);

// GTFO for worlds (moves players to any other world, just not this one)
IrisToolbelt.evacuate(world);

IrisAccess access=IrisToolbelt.createWorld() // If you like builders...
  .name("myWorld") // The world name
  .dimension("terrifyinghands")
  .seed(69133742) // The world seed
  .headless(true)  // Headless make gen go fast
  .pregen(PregenTask // Define a pregen job to run
  .builder()
    .center(new Position2(0,0)) // REGION coords (1 region = 32x32 chunks)
    .radius(4)  // Radius in REGIONS. Rad of 4 means a 9x9 Region map.
    .build())
  .create();
```
