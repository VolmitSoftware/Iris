package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.KList;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Listener;

import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import org.bukkit.generator.BlockPopulator;

public interface EngineCompound extends Listener
{
    public IrisDimension getRootDimension();

    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    public World getWorld();

    public int getSize();

    public Engine getEngine(int index);

    public MultiBurst getBurster();

    public EngineData getEngineMetadata();

    public void saveEngineMetadata();

    public KList<BlockPopulator> getPopulators();

    default Engine getEngineForHeight(int height)
    {
        if(getSize() == 1)
        {
            return getEngine(0);
        }

        int buf = 0;

        for(int i = 0; i < getSize(); i++)
        {
            Engine e = getEngine(i);
            buf += e.getHeight();

            if(buf >= height)
            {
                return e;
            }
        }

        return getEngine(getSize() - 1);
    }

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
