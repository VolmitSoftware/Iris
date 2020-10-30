package com.volmit.iris.v2.scaffold.engine;

import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.v2.generator.IrisComplex;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.v2.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;

public interface EngineActuator<O>
{
    public Engine getEngine();

    public RollingSequence getMetrics();

    public String getName();

    public default IrisDataManager getData()
    {
        return getEngine().getData();
    }

    public default ParallaxAccess getParallax()
    {
        return getEngine().getParallax();
    }

    public default EngineTarget getTarget()
    {
        return getEngine().getTarget();
    }

    public default IrisDimension getDimension()
    {
        return getEngine().getDimension();
    }

    public default long getSeed()
    {
        return getTarget().getWorld().getSeed();
    }

    public default EngineFramework getFramework()
    {
        return getEngine().getFramework();
    }

    public default int getParallelism()
    {
        return getEngine().getParallelism();
    }

    public default IrisComplex getComplex()
    {
        return getFramework().getComplex();
    }

    public void actuate(int x, int z, Hunk<O> output);
}
