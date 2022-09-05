package com.volmit.iris.platform;

import com.volmit.iris.platform.block.PlatformBlock;
import com.volmit.iris.util.WorldHeight;

import java.io.File;

public interface PlatformWorld {
    WorldHeight getHeight();

    String getName();

    File getFolder();

    Iterable<PlatformPlayer> getPlayers();

    Iterable<PlatformChunk> getLoadedChunks();

    PlatformChunk getOrLoadChunk(int x, int z);

    PlatformBlock getBlock(int x, int y, int z);

    PlatformBiome getBiome(int x, int y, int z);

    long getSeed();

    boolean isChunkLoaded(int x, int z);

    void setBlock(int x, int y, int z, PlatformBlock block);

    void setBiome(int x, int y, int z, PlatformBiome biome);

    default File getWorldFolder(String subfolder) {
        return new File(getFolder(), subfolder);
    }

    default File getRegionFolder() {
        return getWorldFolder("region");
    }

    default File getIrisFolder() {
        return getWorldFolder("iris");
    }

    default File getIrisDataFolder() {
        File f = new File(getWorldFolder("iris"), "data");
        f.mkdirs();
        return f;
    }

    default boolean isRegionLoaded(int x, int z) {
        for(PlatformChunk i : getLoadedChunks()) {
            if(i.getX() >> 5 == x && i.getZ() >> 5 == z) {
                return true;
            }
        }

        return false;
    }

    default void unloadChunks(boolean save, boolean force) {
        for(PlatformChunk i : getLoadedChunks()) {
            i.unload(save, force);
        }
    }
}
