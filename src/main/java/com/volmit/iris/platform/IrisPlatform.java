package com.volmit.iris.platform;

import java.util.stream.Stream;

public interface IrisPlatform {
    String getPlatformName();

    Stream<PlatformBlock> getBlocks();

    Stream<PlatformBiome> getBiomes();

    boolean isWorldLoaded(String name);

    PlatformWorld getWorld(String name);
}
