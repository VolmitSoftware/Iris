package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.scaffold.engine.Engine;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Listener;

public interface EngineCompound extends Listener
{
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    public World getWorld();

    public int getSize();

    public Engine getEngine(int index);

    public MultiBurst getBurster();

    public EngineData getEngineMetadata();

    public void saveEngineMetadata();

    public default void save()
    {
        saveEngineMetadata();
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).save();
        }
    }

    public default void saveNOW()
    {
        saveEngineMetadata();
        for(int i = 0; i < getSize(); i++)
        {
            getEngine(i).saveNow();
        }
    }
}
