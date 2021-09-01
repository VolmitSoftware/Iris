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

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;

import java.util.function.Consumer;

public class MantleObjectComponent extends IrisMantleComponent {
    public MantleObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.OBJECT);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, Consumer<Runnable> post) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xxx, zzz);
        placeObjects(writer, rng, x, z, biome, region, post);
    }

    @ChunkCoordinates
    private void placeObjects(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region, Consumer<Runnable> post) {
        for (IrisObjectPlacement i : biome.getSurfaceObjects()) {
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && rng.chance(getComplex().getObjectChanceStream().get(x << 4, z << 4))) {
                try {
                    placeObject(writer, rng, x << 4, z << 4, i, post);
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
            if (rng.chance(i.getChance() + rng.d(-0.005, 0.005)) && rng.chance(getComplex().getObjectChanceStream().get(x << 4, z << 4))) {
                try {
                    placeObject(writer, rng, x << 4, z << 4, i, post);
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
    private void placeObject(MantleWriter writer, RNG rng, int x, int z, IrisObjectPlacement objectPlacement, Consumer<Runnable> post) {
        for (int i = 0; i < objectPlacement.getDensity(); i++) {
            IrisObject v = objectPlacement.getScale().get(rng, objectPlacement.getObject(getComplex(), rng));
            if (v == null) {
                return;
            }
            int xx = rng.i(x, x + 15);
            int zz = rng.i(z, z + 15);
            int id = rng.i(0, Integer.MAX_VALUE);

            int h = v.place(xx, -1, zz, writer, objectPlacement, rng,
                    (b) -> writer.setData(b.getX(), b.getY(), b.getZ(),
                            v.getLoadKey() + "@" + id), null, getData());
            if (objectPlacement.usesFeatures() && h >= 0) {
                if (objectPlacement.isVacuum()) {
                    double a = Math.max(v.getW(), v.getD());
                    IrisFeature f = new IrisFeature();
                    f.setConvergeToHeight(h);
                    f.setBlockRadius(a);
                    f.setInterpolationRadius(objectPlacement.getVacuumInterpolationRadius());
                    f.setInterpolator(objectPlacement.getVacuumInterpolationMethod());
                    f.setStrength(1D);
                    writer.setData(xx, 0, zz, new IrisFeaturePositional(xx, zz, f));
                }

                for (IrisFeaturePotential j : objectPlacement.getAddFeatures()) {
                    if (j.hasZone(rng, xx >> 4, zz >> 4)) {
                        writer.setData(xx, 0, zz, new IrisFeaturePositional(xx, zz, j.getZone()));
                    }
                }
            }
        }
    }
}
