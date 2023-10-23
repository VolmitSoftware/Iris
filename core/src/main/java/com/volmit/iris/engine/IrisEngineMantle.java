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

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.MantleComponent;
import com.volmit.iris.engine.mantle.components.MantleCarvingComponent;
import com.volmit.iris.engine.mantle.components.MantleFluidBodyComponent;
import com.volmit.iris.engine.mantle.components.MantleJigsawComponent;
import com.volmit.iris.engine.mantle.components.MantleObjectComponent;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.BurstExecutor;
import lombok.Data;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class IrisEngineMantle implements EngineMantle {
    private final Engine engine;
    private final Mantle mantle;
    private final KList<MantleComponent> components;
    private final int radius;
    private final AtomicCache<Integer> radCache = new AtomicCache<>();
    private final MantleObjectComponent object;
    private final MantleJigsawComponent jigsaw;

    public IrisEngineMantle(Engine engine) {
        this.engine = engine;
        this.mantle = new Mantle(new File(engine.getWorld().worldFolder(), "mantle"), engine.getTarget().getHeight());
        radius = radCache.aquire(this::computeParallaxSize);
        components = new KList<>();
        registerComponent(new MantleCarvingComponent(this));
        registerComponent(new MantleFluidBodyComponent(this));
        jigsaw = new MantleJigsawComponent(this);
        registerComponent(jigsaw);
        object = new MantleObjectComponent(this);
        registerComponent(object);
    }

    @Override
    public void registerComponent(MantleComponent c) {
        components.add(c);
    }

    @Override
    public MantleJigsawComponent getJigsawComponent() {
        return jigsaw;
    }

    @Override
    public MantleObjectComponent getObjectComponent() {
        return object;
    }

    private KList<IrisRegion> getAllRegions() {
        KList<IrisRegion> r = new KList<>();

        for (String i : getEngine().getDimension().getRegions()) {
            r.add(getEngine().getData().getRegionLoader().load(i));
        }

        return r;
    }

    private KList<IrisBiome> getAllBiomes() {
        KList<IrisBiome> r = new KList<>();

        for (IrisRegion i : getAllRegions()) {
            r.addAll(i.getAllBiomes(getEngine()));
        }

        return r;
    }

    private void warn(String ob, BlockVector bv) {
        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
            Iris.warn("Object " + ob + " has a large size (" + bv + ") and may increase memory usage!");
        }
    }

    private void warnScaled(String ob, BlockVector bv, double ms) {
        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
            Iris.warn("Object " + ob + " has a large size (" + bv + ") and may increase memory usage! (Object scaled up to " + Form.pc(ms, 2) + ")");
        }
    }

    private int computeParallaxSize() {
        Iris.verbose("Calculating the Parallax Size in Parallel");
        AtomicInteger xg = new AtomicInteger(0);
        AtomicInteger zg = new AtomicInteger();
        xg.set(0);
        zg.set(0);
        int jig = 0;
        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        int x = xg.get();
        int z = zg.get();

        if (getEngine().getDimension().isUseMantle()) {
            KList<IrisRegion> r = getAllRegions();
            KList<IrisBiome> b = getAllBiomes();

            for (IrisBiome i : b) {
                for (IrisObjectPlacement j : i.getObjects()) {
                    if (j.getScale().canScaleBeyond()) {
                        scalars.put(j.getScale(), j.getPlace());
                    } else {
                        objects.addAll(j.getPlace());
                    }
                }

                for (IrisJigsawStructurePlacement j : i.getJigsawStructures()) {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
                }
            }

            for (IrisRegion i : r) {
                for (IrisObjectPlacement j : i.getObjects()) {
                    if (j.getScale().canScaleBeyond()) {
                        scalars.put(j.getScale(), j.getPlace());
                    } else {
                        objects.addAll(j.getPlace());
                    }
                }

                for (IrisJigsawStructurePlacement j : i.getJigsawStructures()) {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
                }
            }

            for (IrisJigsawStructurePlacement j : getEngine().getDimension().getJigsawStructures()) {
                jig = Math.max(jig, getData().getJigsawStructureLoader().load(j.getStructure()).getMaxDimension());
            }

            if (getEngine().getDimension().getStronghold() != null) {
                try {
                    jig = Math.max(jig, getData().getJigsawStructureLoader().load(getEngine().getDimension().getStronghold()).getMaxDimension());
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }

            Iris.verbose("Checking sizes for " + Form.f(objects.size()) + " referenced objects.");
            BurstExecutor e = getEngine().getTarget().getBurster().burst(objects.size());
            KMap<String, BlockVector> sizeCache = new KMap<>();
            for (String i : objects) {
                e.queue(() -> {
                    try {
                        BlockVector bv = sizeCache.computeIfAbsent(i, (k) -> {
                            try {
                                return IrisObject.sampleSize(getData().getObjectLoader().findFile(i));
                            } catch (IOException ex) {
                                Iris.reportError(ex);
                                ex.printStackTrace();
                            }

                            return null;
                        });

                        if (bv == null) {
                            throw new RuntimeException();
                        }

                        warn(i, bv);

                        synchronized (xg) {
                            xg.getAndSet(Math.max(bv.getBlockX(), xg.get()));
                        }

                        synchronized (zg) {
                            zg.getAndSet(Math.max(bv.getBlockZ(), zg.get()));
                        }
                    } catch (Throwable ed) {
                        Iris.reportError(ed);

                    }
                });
            }

            for (Map.Entry<IrisObjectScale, KList<String>> entry : scalars.entrySet()) {
                double ms = entry.getKey().getMaximumScale();
                for (String j : entry.getValue()) {
                    e.queue(() -> {
                        try {
                            BlockVector bv = sizeCache.computeIfAbsent(j, (k) -> {
                                try {
                                    return IrisObject.sampleSize(getData().getObjectLoader().findFile(j));
                                } catch (IOException ioException) {
                                    Iris.reportError(ioException);
                                    ioException.printStackTrace();
                                }

                                return null;
                            });

                            if (bv == null) {
                                throw new RuntimeException();
                            }

                            warnScaled(j, bv, ms);

                            synchronized (xg) {
                                xg.getAndSet((int) Math.max(Math.ceil(bv.getBlockX() * ms), xg.get()));
                            }

                            synchronized (zg) {
                                zg.getAndSet((int) Math.max(Math.ceil(bv.getBlockZ() * ms), zg.get()));
                            }
                        } catch (Throwable ee) {
                            Iris.reportError(ee);

                        }
                    });
                }
            }

            e.complete();

            x = xg.get();
            z = zg.get();

            for (IrisDepositGenerator i : getEngine().getDimension().getDeposits()) {
                int max = i.getMaxDimension();
                x = Math.max(max, x);
                z = Math.max(max, z);
            }

            for (IrisRegion v : r) {
                for (IrisDepositGenerator i : v.getDeposits()) {
                    int max = i.getMaxDimension();
                    x = Math.max(max, x);
                    z = Math.max(max, z);
                }
            }

            for (IrisBiome v : b) {
                for (IrisDepositGenerator i : v.getDeposits()) {
                    int max = i.getMaxDimension();
                    x = Math.max(max, x);
                    z = Math.max(max, z);
                }
            }
        } else {
            return 0;
        }

        x = Math.max(z, x);
        int u = x;
        int c = Math.max(computeCarvingRange(), computeBodyRange());
        x = Math.max(jig, x);
        x = Math.max(x, c);
        x = (Math.max(x, 16) + 16) >> 4;
        x = x % 2 == 0 ? x + 1 : x;
        Iris.info("Mantle Size: " + x + " Chunks");
        Iris.info("  Object Mantle Size: " + u + " (" + ((Math.max(u, 16) + 16) >> 4) + ")");
        Iris.info("  Jigsaw Mantle Size: " + jig + " (" + ((Math.max(jig, 16) + 16) >> 4) + ")");
        Iris.info("  Carving Mantle Size: " + c + " (" + ((Math.max(c, 16) + 16) >> 4) + ")");

        return x;
    }

    private int computeBodyRange() {
        int m = 0;

        m = Math.max(m, getDimension().getFluidBodies().getMaxRange(getData()));

        for (IrisRegion i : getDimension().getAllRegions(getEngine())) {
            m = Math.max(m, i.getFluidBodies().getMaxRange(getData()));
        }

        for (IrisBiome i : getDimension().getAllBiomes(getEngine())) {
            m = Math.max(m, i.getFluidBodies().getMaxRange(getData()));
        }

        return m;
    }

    private int computeCarvingRange() {
        int m = 0;

        m = Math.max(m, getDimension().getCarving().getMaxRange(getData()));

        for (IrisRegion i : getDimension().getAllRegions(getEngine())) {
            m = Math.max(m, i.getCarving().getMaxRange(getData()));
        }

        for (IrisBiome i : getDimension().getAllBiomes(getEngine())) {
            m = Math.max(m, i.getCarving().getMaxRange(getData()));
        }

        return m;
    }
}
