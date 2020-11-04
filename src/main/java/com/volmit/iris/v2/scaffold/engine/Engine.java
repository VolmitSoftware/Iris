package com.volmit.iris.v2.scaffold.engine;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;

public interface Engine
{
    public void setParallelism(int parallelism);

    public int getParallelism();

    public EngineTarget getTarget();

    public EngineFramework getFramework();

    public void setMinHeight(int min);

    public int getMinHeight();

    public double modifyX(double x);

    public double modifyZ(double z);

    default void updateBlock(Block block)
    {
        //TODO: UPDATE IT
    }

    default void updateChunk(Chunk chunk)
    {
        //TODO: UPDATE IT
    }

    default void save()
    {
        getParallax().saveAll();
    }

    default void saveNow()
    {
        getParallax().saveAllNOW();
    }

    default String getName()
    {
        return getDimension().getName();
    }

    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes);

    public default int getHeight()
    {
        return getTarget().getHeight();
    }

    public default IrisDataManager getData()
    {
        return getTarget().getData();
    }

    public default World getWorld()
    {
        return getTarget().getWorld();
    }

    public default IrisDimension getDimension()
    {
        return getTarget().getDimension();
    }

    public default ParallaxAccess getParallax()
    {
        return getTarget().getParallaxWorld();
    }
}
