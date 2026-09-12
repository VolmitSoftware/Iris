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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.math.RollingSequence;
import art.arcane.iris.generation.concurrent.MultiBurst;

public interface EngineComponent {
    Engine getEngine();

    RollingSequence getMetrics();

    String getName();

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default void close() {
        try {
            EngineComponentCleanup cleanup = IrisServices.getOrNull(EngineComponentCleanup.class);
            if (cleanup != null) {
                cleanup.release(this);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
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
