/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.event.Listener;

public interface EngineComponent {
    Engine getEngine();

    RollingSequence getMetrics();

    String getName();

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default void close() {
        try {
            if (this instanceof Listener) {
                Iris.instance.unregisterListener((Listener) this);
            }
        } catch (Throwable e) {
            Iris.reportError(e);

        }
    }

    default IrisData getData() {
        return getEngine().getData();
    }

    default EngineTarget getTarget() {
        return getEngine().getTarget();
    }

    default IrisDimension getDimension() {
        return getEngine().getDimension();
    }

    default long getSeed() {
        return getEngine().getSeedManager().getComponent();
    }

    default int getParallelism() {
        return getEngine().getParallelism();
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }
}
