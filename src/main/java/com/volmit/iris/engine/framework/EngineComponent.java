/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.util.math.RollingSequence;
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
        } catch (Throwable e) {
            Iris.reportError(e);

        }
    }

    default double modX(double x) {
        return getEngine().modifyX(x);
    }

    default double modZ(double z) {
        return getEngine().modifyZ(z);
    }

    default IrisData getData() {
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
        return getTarget().getWorld().seed();
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
