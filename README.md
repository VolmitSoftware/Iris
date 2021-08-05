# Iris

For 1.16 and below, see the 1.14-1.16 branch. The master branch is for the latest version of minecraft.

# [Support](https://discord.gg/3xxPTpT) **|** [Documentation](https://docs.volmit.com/iris/) **

|** [Git](https://github.com/IrisDimensions)

## Iris Toolbelt

Everyone needs a toolbelt.

```java
package com.volmit.iris.core.tools

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
