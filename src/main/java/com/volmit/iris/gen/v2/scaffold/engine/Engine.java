package com.volmit.iris.gen.v2.scaffold.engine;

import com.volmit.iris.gen.v2.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import org.bukkit.World;

public interface Engine
{
    public void setParallelism(int parallelism);

    public int getParallelism();

    public EngineTarget getTarget();

    public EngineFramework getFramework();

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
