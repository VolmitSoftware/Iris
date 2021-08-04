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
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.DataProvider;
import com.volmit.iris.engine.data.mca.NBTWorld;
import com.volmit.iris.engine.headless.HeadlessGenerator;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@SuppressWarnings("EmptyMethod")
public interface IrisAccess extends Hotloadable, DataProvider {

    HeadlessGenerator getHeadlessGenerator();

    default boolean isHeadless() {
        return getHeadlessGenerator() != null;
    }

    NBTWorld getHeadlessNBTWriter();

    void directWriteMCA(IrisWorld w, int x, int z, NBTWorld writer, MultiBurst burst);

    void directWriteMCA(IrisWorld w, int x, int z, NBTWorld writer, MultiBurst burst, PregenListener listener);

    void directWriteChunk(IrisWorld w, int x, int z, NBTWorld writer);

    int getGenerated();

    double getGeneratedPerSecond();

    void printMetrics(CommandSender sender);

    /**
     * Ignores the world, just uses the position
     *
     * @param l the location
     * @return the biome
     */
    default IrisBiome getBiome(Location l) {
        return getBiome(l.toVector());
    }

    default IrisRegion getRegion(int x, int y, int z) {
        return getEngineAccess(y).getRegion(x, z);
    }

    default IrisRegion getRegion(Location l) {
        return getRegion(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    default IrisBiome getBiome(Vector l) {
        return getBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    IrisBiome getBiome(int x, int y, int z);

    IrisBiome getCaveBiome(int x, int y, int z);

    IrisBiome getBiome(int x, int z);

    IrisBiome getCaveBiome(int x, int z);

    GeneratorAccess getEngineAccess(int y);

    IrisData getData();

    int getHeight(int x, int y, int z);

    int getThreadCount();

    void changeThreadCount(int m);

    void close();

    boolean isClosed();

    EngineTarget getTarget();

    EngineCompound getCompound();

    boolean isFailing();

    boolean isStudio();

    default Location lookForBiome(IrisBiome biome, long timeout, Consumer<Integer> triesc) {
        if (!getCompound().getWorld().hasRealWorld()) {
            Iris.error("Cannot GOTO without a bound world (headless mode)");
            return null;
        }

        ChronoLatch cl = new ChronoLatch(250, false);
        long s = M.ms();
        int cpus = (Runtime.getRuntime().availableProcessors());
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
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Location> location = new AtomicReference<>();
        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                try {
                    Engine e;
                    IrisBiome b;
                    int x, z;

                    while (!found.get() && running.get()) {
                        try {
                            synchronized (engines) {
                                e = engines.getRandom();
                                x = RNG.r.i(-29999970, 29999970);
                                z = RNG.r.i(-29999970, 29999970);
                                b = e.getSurfaceBiome(x, z);
                            }

                            if (b != null && b.getLoadKey() == null) {
                                continue;
                            }

                            if (b != null && b.getLoadKey().equals(biome.getLoadKey())) {
                                found.lazySet(true);
                                location.lazySet(new Location(e.getWorld().realWorld(), x, e.getHeight(x, z), z));
                            }

                            tries.getAndIncrement();
                        } catch (Throwable ex) {
                            Iris.reportError(ex);
                            ex.printStackTrace();
                            return;
                        }
                    }
                } catch (Throwable e) {
                    Iris.reportError(e);
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
                running.set(false);
                return null;
            }
        }

        running.set(false);
        return location.get();
    }

    default Location lookForRegion(IrisRegion reg, long timeout, Consumer<Integer> triesc) {
        if (!getCompound().getWorld().hasRealWorld()) {
            Iris.error("Cannot GOTO without a bound world (headless mode)");
            return null;
        }

        ChronoLatch cl = new ChronoLatch(3000, false);
        long s = M.ms();
        int cpus = (Runtime.getRuntime().availableProcessors());
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
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Location> location = new AtomicReference<>();

        for (int i = 0; i < cpus; i++) {
            J.a(() -> {
                Engine e;
                IrisRegion b;
                int x, z;

                while (!found.get() && running.get()) {
                    try {
                        e = engines.getRandom();
                        x = RNG.r.i(-29999970, 29999970);
                        z = RNG.r.i(-29999970, 29999970);
                        b = e.getRegion(x, z);

                        if (b != null && b.getLoadKey() != null && b.getLoadKey().equals(reg.getLoadKey())) {
                            found.lazySet(true);
                            location.lazySet(new Location(e.getWorld().realWorld(), x, e.getHeight(x, z) + e.getMinHeight(), z));
                        }

                        tries.getAndIncrement();
                    } catch (Throwable xe) {
                        Iris.reportError(xe);
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
                running.set(false);
                return null;
            }
        }

        triesc.accept(tries.get());
        running.set(false);
        return location.get();
    }

    default int getParallaxChunkCount() {
        int v = 0;

        for (int i = 0; i < getCompound().getSize(); i++) {
            v += getCompound().getEngine(i).getParallax().getChunkCount();
        }

        return v;
    }

    default double getHeight(Location l) {
        return getHeight(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
}
