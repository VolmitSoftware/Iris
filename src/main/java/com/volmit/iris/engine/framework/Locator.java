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

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiral;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@FunctionalInterface
public interface Locator<T> {
    static void cancelSearch()
    {
        if(LocatorCanceller.cancel != null)
        {
            LocatorCanceller.cancel.run();
        }
    }

    default Future<Position2> find(Engine engine, Position2 pos, long timeout)
    {
        return MultiBurst.burst.completeValue(() -> {
            int tc = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 4;
            MultiBurst burst = new MultiBurst("Iris Locator", Thread.MIN_PRIORITY);
            AtomicBoolean found = new AtomicBoolean(false);
            Position2 cursor = pos;
            AtomicBoolean stop = new AtomicBoolean(false);
            AtomicReference<Position2> foundPos = new AtomicReference<>();
            PrecisionStopwatch px = PrecisionStopwatch.start();
            LocatorCanceller.cancel = () -> stop.set(true);

            while(!found.get() || stop.get() || px.getMilliseconds() > timeout)
            {
                BurstExecutor e = burst.burst(tc);

                for(int i = 0; i < tc; i++)
                {
                    Position2 p = cursor;
                    cursor = Spiral.next(cursor);

                    e.queue(() -> {
                        if(matches(engine, p))
                        {
                            if(foundPos.get() == null)
                            {
                                foundPos.set(p);
                            }

                            found.set(true);
                        }
                    });
                }

                e.complete();
            }

            burst.close();

            if(found.get() && foundPos.get() != null)
            {
                return foundPos.get();
            }

            return null;
        });
    }

    boolean matches(Engine engine, Position2 chunk);

    static Locator<IrisBiome> region(String loadKey)
    {
        return (e, c) -> e.getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<IrisBiome> jigsawStructure(String loadKey)
    {
        return (e, c) -> {
            IrisJigsawStructure s = e.getStructureAt(c.getX(), c.getZ());
            return s != null && s.getLoadKey().equals(loadKey);
        };
    }

    static Locator<IrisBiome> object(String loadKey)
    {
        return (e, c) -> e.getObjectsAt(c.getX(), c.getZ()).contains(loadKey);
    }

    static Locator<IrisBiome> surfaceBiome(String loadKey)
    {
        return (e, c) -> e.getSurfaceBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<IrisBiome> caveBiome(String loadKey)
    {
        return (e, c) -> e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<IrisBiome> caveOrMantleBiome(String loadKey)
    {
        return (e, c) -> {
            AtomicBoolean found = new AtomicBoolean(false);
            e.generateMatter(c.getX(), c.getZ(), true);
            e.getMantle().getMantle().iterateChunk(c.getX(), c.getZ(), MatterCavern.class, (x,y,z,t) ->{
                if(found.get())
                {
                    return;
                }

                if(t != null && t.getCustomBiome().equals(loadKey))
                {
                    found.set(true);
                }
            });

            return found.get();
        };
    }
}
