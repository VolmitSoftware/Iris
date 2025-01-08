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

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisBlockData;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterStructurePOI;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.noise.NoiseType;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Getter;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class MantleObjectComponent extends IrisMantleComponent {
    private final int radius = computeRadius();

    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.OBJECT, 1);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        placeObjects(writer, rng, x, z, biome, region);
    }

    private RNG applyNoise(int x, int z, long seed) {
        CNG noise = CNG.signatureFast(new RNG(seed), NoiseType.WHITE, NoiseType.GLOB);
        return new RNG((long) (seed * noise.noise(x, z)));
    }

    @ChunkCoordinates
    private void placeObjects(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region) {
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                try {
                    placeObject(writer, rng, x << 4, z << 4, i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following biome: " + biome.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                try {
                    placeObject(writer, rng, x << 4, z << 4, i);
                } catch (Throwable e) {
                    Iris.reportError(e);
                    Iris.error("Failed to place objects in the following region: " + region.getName());
                    Iris.error("Object(s) " + i.getPlace().toString(", ") + " (" + e.getClass().getSimpleName() + ").");
                    Iris.error("Are these objects missing?");
                    e.printStackTrace();
                }
            }
        }
    }

    @BlockCoordinates
    private void placeObject(MantleWriter writer, RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        for (int i = 0; i < objectPlacement.getDensity(rng, x, z, getData()); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                return;
            }
            int xx = rng.i(x, x + 15);
            int zz = rng.i(z, z + 15);
            int id = rng.i(0, Integer.MAX_VALUE);
            v.place(xx, -1, zz, writer, objectPlacement, rng, (b, data) -> {
                writer.setData(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id);
                if (objectPlacement.isDolphinTarget() && objectPlacement.isUnderwater() && B.isStorageChest(data)) {
                    writer.setData(b.getX(), b.getY(), b.getZ(), MatterStructurePOI.BURIED_TREASURE);
                }
                if (data instanceof IrisBlockData d) {
                    writer.setData(b.getX(), b.getY(), b.getZ(), d.getCustom());
                }
            }, null, getData());
        }
    }

    @BlockCoordinates
    private Set<String> guessPlacedKeys(RNG rng, int x, int z, IrisObjectPlacement objectPlacement) {
        Set<String> f = new KSet<>();
        for (int i = 0; i < objectPlacement.getDensity(rng, x, z, getData()); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                continue;
            }

            f.add(v.getLoadKey());
        }

        return f;
    }

    public Set<String> guess(int x, int z) {
        // todo The guess doesnt bring into account that the placer may return -1
        RNG rng = applyNoise(x, z, Cache.key(x, z) + seed());
        IrisBiome biome = getEngineMantle().getEngine().getSurfaceBiome((x << 4) + 8, (z << 4) + 8);
        IrisRegion region = getEngineMantle().getEngine().getRegion((x << 4) + 8, (z << 4) + 8);
        Set<String> v = new KSet<>();
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rng, x, z, i));
            }
        }

        return v;
    }

    private int computeRadius() {
        var dimension = getDimension();

        KSet<String> objects = new KSet<>();
        KMap<IrisObjectScale, KList<String>> scalars = new KMap<>();
        for (var region : dimension.getAllRegions(this::getData)) {
            for (var placement : region.getObjects()) {
                if (placement.getScale().canScaleBeyond()) {
                    scalars.put(placement.getScale(), placement.getPlace());
                } else {
                    objects.addAll(placement.getPlace());
                }
            }

            for (var biome : region.getAllBiomes(this::getData)) {
                for (var placement : biome.getObjects()) {
                    if (placement.getScale().canScaleBeyond()) {
                        scalars.put(placement.getScale(), placement.getPlace());
                    } else {
                        objects.addAll(placement.getPlace());
                    }
                }
            }
        }

        return computeObjectRadius(objects, scalars, getEngineMantle().getTarget().getBurster(), getData());
    }

    static int computeObjectRadius(KSet<String> objects, KMap<IrisObjectScale, KList<String>> scalars, MultiBurst burst, IrisData data) {
        AtomicInteger x = new AtomicInteger();
        AtomicInteger z = new AtomicInteger();

        BurstExecutor e = burst.burst(objects.size());
        KMap<String, BlockVector> sizeCache = new KMap<>();
        for (String loadKey : objects) {
            e.queue(() -> {
                try {
                    BlockVector bv = sampleSize(sizeCache, data, loadKey);

                    if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                        Iris.warn("Object " + loadKey + " has a large size (" + bv + ") and may increase memory usage!");
                    }

                    x.getAndUpdate(i -> Math.max(bv.getBlockX(), i));
                    z.getAndUpdate(i -> Math.max(bv.getBlockZ(), i));
                } catch (Throwable ed) {
                    Iris.reportError(ed);
                }
            });
        }

        for (Map.Entry<IrisObjectScale, KList<String>> entry : scalars.entrySet()) {
            double ms = entry.getKey().getMaximumScale();
            for (String loadKey : entry.getValue()) {
                e.queue(() -> {
                    try {
                        BlockVector bv = sampleSize(sizeCache, data, loadKey);

                        if (Math.max(bv.getBlockX(), bv.getBlockZ()) > 128) {
                            Iris.warn("Object " + loadKey + " has a large size (" + bv + ") and may increase memory usage! (Object scaled up to " + Form.pc(ms, 2) + ")");
                        }

                        x.getAndUpdate(i -> (int) Math.max(Math.ceil(bv.getBlockX() * ms), i));
                        x.getAndUpdate(i -> (int) Math.max(Math.ceil(bv.getBlockZ() * ms), i));
                    } catch (Throwable ee) {
                        Iris.reportError(ee);

                    }
                });
            }
        }

        e.complete();
        return Math.max(x.get(), z.get());
    }

    private static BlockVector sampleSize(KMap<String, BlockVector> sizeCache, IrisData data, String loadKey) {
        BlockVector bv = sizeCache.computeIfAbsent(loadKey, (k) -> {
            try {
                return IrisObject.sampleSize(data.getObjectLoader().findFile(loadKey));
            } catch (IOException ioException) {
                Iris.reportError(ioException);
                ioException.printStackTrace();
            }

            return null;
        });
        return Objects.requireNonNull(bv, "sampleSize returned a null block vector");
    }
}
