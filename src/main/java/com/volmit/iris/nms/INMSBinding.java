package com.volmit.iris.nms;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

public interface INMSBinding {
    Object getBiomeBaseFromId(int id);

    int getTrueBiomeBaseId(Object biomeBase);

    Object getTrueBiomeBase(Location location);

    String getTrueBiomeBaseKey(Location location);

    Object getCustomBiomeBaseFor(String mckey);

    String getKeyForBiomeBase(Object biomeBase);

    Object getBiomeBase(World world, Biome biome);

    Object getBiomeBase(Object registry, Biome biome);

    boolean isBukkit();

    int getBiomeId(Biome biome);

    default World createWorld(WorldCreator c) {
        return c.createWorld();
    }

	int countCustomBiomes();

    void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk);
}
