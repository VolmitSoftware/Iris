package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import org.bukkit.event.Listener;

public interface EngineComponent {
    public Engine getEngine();

    public RollingSequence getMetrics();

    public String getName();

    default void close()
    {
        try
        {
            if(this instanceof Listener)
            {
                Iris.instance.unregisterListener((Listener) this);
            }
        }

        catch(Throwable ignored)
        {

        }
    }

    default double modX(double x)
    {
        return getEngine().modifyX(x);
    }

    default double modZ(double z)
    {
        return getEngine().modifyZ(z);
    }

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
}
