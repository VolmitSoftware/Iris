package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.hunk.io.BlockDataHunkIOAdapter;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.v2.scaffold.parallel.MultiBurst;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface Engine
{
    public void setParallelism(int parallelism);

    public int getParallelism();

    public EngineTarget getTarget();

    public EngineFramework getFramework();

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
