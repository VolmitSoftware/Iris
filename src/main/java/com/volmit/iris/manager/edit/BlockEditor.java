package com.volmit.iris.manager.edit;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.Closeable;

public interface BlockEditor extends Closeable {
    long last();

    void set(int x, int y, int z, BlockData d);

    BlockData get(int x, int y, int z);

    void setBiome(int x, int z, Biome b);

    void setBiome(int x, int y, int z, Biome b);

    @Override
    void close();

    Biome getBiome(int x, int y, int z);

    Biome getBiome(int x, int z);
}
