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
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisObjectPlacement;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterStructurePOI;

import java.util.Set;

public class MantleObjectComponent extends IrisMantleComponent {
    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.OBJECT);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        placeObjects(writer, rng, x, z, biome, region);
    }

    @ChunkCoordinates
    private void placeObjects(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region) {
        long s = Cache.key(x, z) + seed();
        RNG rnp = new RNG(s);
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                try {
                    placeObject(writer, rnp, x << 4, z << 4, i);
                    rnp.setSeed(s);
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
                    placeObject(writer, rnp, x << 4, z << 4, i);
                    rnp.setSeed(s);
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
        RNG rng = new RNG(Cache.key(x, z) + seed());
        long s = Cache.key(x, z) + seed();
        RNG rngd = new RNG(s);
        IrisBiome biome = getEngineMantle().getEngine().getSurfaceBiome((x << 4) + 8, (z << 4) + 8);
        IrisRegion region = getEngineMantle().getEngine().getRegion((x << 4) + 8, (z << 4) + 8);
        Set<String> v = new KSet<>();
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rngd, x, z, i));
                rngd.setSeed(s);
            }
        }

        for (IrisObjectPlacement i : region.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005))) {
                v.addAll(guessPlacedKeys(rngd, x, z, i));
                rngd.setSeed(s);
            }
        }

        return v;
    }
}
