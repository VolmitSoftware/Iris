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

package com.volmit.iris.scaffold.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.pregen.DirectWorldWriter;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@SuppressWarnings("EmptyMethod")
public interface IrisAccess extends Hotloadable, DataProvider {

    void directWriteMCA(World w, int x, int z, DirectWorldWriter writer, MultiBurst burst);

    void directWriteChunk(World w, int x, int z, DirectWorldWriter writer);

    int getGenerated();

    double getGeneratedPerSecond();

    void printMetrics(CommandSender sender);

    IrisBiome getBiome(int x, int y, int z);

    IrisBiome getCaveBiome(int x, int y, int z);

    IrisBiome getBiome(int x, int z);

    IrisBiome getCaveBiome(int x, int z);

    GeneratorAccess getEngineAccess(int y);

    IrisDataManager getData();

    int getHeight(int x, int y, int z);

    int getThreadCount();

    void changeThreadCount(int m);

    void regenerate(int x, int z);

    void close();

    boolean isClosed();

    EngineTarget getTarget();

    EngineCompound getCompound();

    boolean isFailing();

    boolean isStudio();

    default Location lookForBiome(IrisBiome biome, long timeout, Consumer<Integer> triesc) {
        ChronoLatch cl = new ChronoLatch(250, false);
        long s = M.ms();
        int cpus = 2 + (Runtime.getRuntime().availableProcessors() / 2);
        KList<Engine> engines = new KList<>();
        for (int i = 0; i < getCompound().getSize(); i++) {
            Engine e = getCompound().getEngine(i);
            if (e.getDimension().getAllBiomes(e).contains(biome)) {
                engines.add(e);
            }
        }

        if (engines.isEmpty()) {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<Location> location = new AtomicReference<>();

        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                try {
                    Engine e;
                    IrisBiome b;
                    int x, y, z;

                    while (!found.get()) {
                        try {
                            synchronized (engines) {
                                e = engines.getRandom();
                                x = RNG.r.i(-29999970, 29999970);
                                y = RNG.r.i(0, e.getHeight() - 1);
                                z = RNG.r.i(-29999970, 29999970);

                                b = e.getBiome(x, y, z);
                            }

                            if (b != null && b.getLoadKey() == null) {
                                continue;
                            }

                            if (b != null && b.getLoadKey().equals(biome.getLoadKey())) {
                                found.lazySet(true);
                                location.lazySet(new Location(e.getWorld(), x, y, z));
                            }

                            tries.getAndIncrement();
                        } catch (Throwable ex) {
                            Iris.reportError(ex);
                            ex.printStackTrace();
                            return;
                        }
                    }
                } catch (Throwable e) {Iris.reportError(e);
                    e.printStackTrace();
                }
            });
        }

        while (!found.get() || location.get() == null) {
            J.sleep(50);

            if (cl.flip()) {
                triesc.accept(tries.get());
            }

            if (M.ms() - s > timeout) {
                return null;
            }
        }

        return location.get();
    }

    default Location lookForRegion(IrisRegion reg, long timeout, Consumer<Integer> triesc) {
        ChronoLatch cl = new ChronoLatch(3000, false);
        long s = M.ms();
        int cpus = 2 + (Runtime.getRuntime().availableProcessors() / 2);
        KList<Engine> engines = new KList<>();
        for (int i = 0; i < getCompound().getSize(); i++) {
            Engine e = getCompound().getEngine(i);
            if (e.getDimension().getRegions().contains(reg.getLoadKey())) {
                engines.add(e);
            }
        }

        if (engines.isEmpty()) {
            return null;
        }

        AtomicInteger tries = new AtomicInteger(0);
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<Location> location = new AtomicReference<>();

        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                Engine e;
                IrisRegion b;
                int x, z;

                while (!found.get()) {
                    try {
                        e = engines.getRandom();
                        x = RNG.r.i(-29999970, 29999970);
                        z = RNG.r.i(-29999970, 29999970);
                        b = e.getRegion(x, z);

                        if (b != null && b.getLoadKey() != null && b.getLoadKey().equals(reg.getLoadKey())) {
                            found.lazySet(true);
                            location.lazySet(new Location(e.getWorld(), x, e.getHeight(x, z) + e.getMinHeight(), z));
                        }

                        tries.getAndIncrement();
                    } catch (Throwable xe) {Iris.reportError(xe);
                        xe.printStackTrace();
                        return;
                    }
                }
            });
        }

        while (!found.get() || location.get() != null) {
            J.sleep(50);

            if (cl.flip()) {
                triesc.accept(tries.get());
            }

            if (M.ms() - s > timeout) {
                triesc.accept(tries.get());
                return null;
            }
        }

        triesc.accept(tries.get());
        return location.get();
    }

    void clearRegeneratedLists(int x, int z);

    void precache(World world, int x, int z);

    int getPrecacheSize();

    Chunk generatePaper(World world, int cx, int cz);

    default int getParallaxChunkCount() {
        int v = 0;

        for (int i = 0; i < getCompound().getSize(); i++) {
            v += getCompound().getEngine(i).getParallax().getChunkCount();
        }

        return v;
    }
}
