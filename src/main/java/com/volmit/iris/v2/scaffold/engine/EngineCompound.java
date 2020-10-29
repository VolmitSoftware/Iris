package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineCompound
{
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    public World getWorld();

    public int getSize();

    public Engine getEngine(int index);

    public MultiBurst getBurster();

    public EngineData getEngineMetadata();

    public void saveEngineMetadata();
}
