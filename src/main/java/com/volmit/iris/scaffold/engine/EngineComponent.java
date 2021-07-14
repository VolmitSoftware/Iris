package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.IrisComplex;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.util.RollingSequence;
import org.bukkit.event.Listener;

public interface EngineComponent {
    Engine getEngine();

    RollingSequence getMetrics();

    String getName();

    default void close() {
        try {
            if (this instanceof Listener) {
                Iris.instance.unregisterListener((Listener) this);
            }
        } catch (Throwable ignored) {

        }
    }

    default double modX(double x) {
        return getEngine().modifyX(x);
    }

    default double modZ(double z) {
        return getEngine().modifyZ(z);
    }

    default IrisDataManager getData() {
        return getEngine().getData();
    }

    default ParallaxAccess getParallax() {
        return getEngine().getParallax();
    }

    default EngineTarget getTarget() {
        return getEngine().getTarget();
    }

    default IrisDimension getDimension() {
        return getEngine().getDimension();
    }

    default long getSeed() {
        return getTarget().getWorld().getSeed();
    }

    default EngineFramework getFramework() {
        return getEngine().getFramework();
    }

    default int getParallelism() {
        return getEngine().getParallelism();
    }

    default IrisComplex getComplex() {
        return getFramework().getComplex();
    }
}
