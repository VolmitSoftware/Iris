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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Process-wide registry of live {@link IrisEngine} instances driven by one shared repeating task.
 * The task is created on the first registration and cancelled once the last engine unregisters, so
 * no ticking work runs when no engine is loaded.
 */
final class EngineTickRegistry {
    private static final Object TICK_LOCK = new Object();
    private static final Set<IrisEngine> TICK_ENGINES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int sharedTickTask = -1;

    private EngineTickRegistry() {
    }

    static void registerTicking(IrisEngine engine) {
        synchronized (TICK_LOCK) {
            TICK_ENGINES.add(engine);
            if (sharedTickTask == -1) {
                sharedTickTask = J.ar(EngineTickRegistry::tickEngines, 1);
            }
        }
    }

    static void unregisterTicking(IrisEngine engine) {
        synchronized (TICK_LOCK) {
            TICK_ENGINES.remove(engine);
            if (TICK_ENGINES.isEmpty() && sharedTickTask != -1) {
                J.car(sharedTickTask);
                sharedTickTask = -1;
            }
        }
    }

    private static void tickEngines() {
        List<IrisEngine> engines;
        synchronized (TICK_LOCK) {
            engines = List.copyOf(TICK_ENGINES);
        }
        for (IrisEngine engine : engines) {
            try {
                engine.tickRandomPlayer();
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }
}
