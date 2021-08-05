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

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.carve.IrisCaveLayer;
import com.volmit.iris.engine.object.common.CaveResult;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.FastNoiseDouble;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.function.Function;

public class IrisCaveModifier extends EngineAssignedModifier<BlockData> {
    public static final BlockData CAVE_AIR = B.get("CAVE_AIR");
    public static final BlockData AIR = B.get("AIR");
    private static final KList<CaveResult> EMPTY = new KList<>();
    private final FastNoiseDouble gg;
    private final RNG rng;

    public IrisCaveModifier(Engine engine) {
        super(engine, "Cave");
        rng = new RNG(engine.getWorld().seed() + 28934555);
        gg = new FastNoiseDouble(324895L * rng.nextParallelRNG(49678).imax());
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> a, boolean multicore) {
        if (!getDimension().isCaves()) {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (multicore) {
            BurstExecutor e = getEngine().burst().burst(a.getWidth());
            for (int i = 0; i < a.getWidth(); i++) {
                int finalI = i;
                e.queue(() -> modifySliver(x, z, finalI, a));
            }

            e.complete();
        } else {
            for (int i = 0; i < a.getWidth(); i++) {
                modifySliver(x, z, i, a);
            }
        }

        getEngine().getMetrics().getCave().put(p.getMilliseconds());
    }

    public void modifySliver(int x, int z, int finalI, Hunk<BlockData> a) {
        for (int j = 0; j < a.getDepth(); j++) {
            KList<CaveResult> caves = genCaves(x + finalI, z + j, finalI, j, a);
            int he = (int) Math.round(getComplex().getHeightStream().get(x + finalI, z + j));
            if (caves != null && caves.isNotEmpty()) {
                IrisBiome cave = getComplex().getCaveBiomeStream().get(x + finalI, z + j);

                if (cave == null) {
                    continue;
                }

                for (CaveResult cl : caves) {
                    if (cl.getFloor() < 0 || cl.getFloor() > getEngine().getHeight() || cl.getCeiling() > getEngine().getHeight() || cl.getCeiling() < 0) {
                        continue;
                    }

                    KList<BlockData> floor = cave.generateLayers(x + finalI, z + j, rng, cl.getFloor(), cl.getFloor(), getData(), getComplex());
                    KList<BlockData> ceiling = cave.generateLayers(x + finalI + 656, z + j - 656, rng,
                            he - cl.getCeiling(),
                            he - cl.getCeiling(), getData(), getComplex());

                    for (int g = 0; g < floor.size(); g++) {
                        a.set(finalI, cl.getFloor() - g, j, floor.get(g));
                    }

                    for (int g = ceiling.size() - 1; g > 0; g--) {
                        a.set(finalI, cl.getCeiling() + g, j, ceiling.get(g));
                    }
                }
            }
        }
    }

    public KList<CaveResult> genCaves(double wxx, double wzz) {
        return genCaves(wxx, wzz, 0, 0, null);
    }

    public KList<CaveResult> genCaves(double wxx, double wzz, int x, int z, Hunk<BlockData> data) {
        if (!getDimension().isCaves()) {
            return EMPTY;
        }

        KList<CaveResult> result = new KList<>();
        gg.setNoiseType(FastNoiseDouble.NoiseType.Cellular);
        gg.setCellularReturnType(FastNoiseDouble.CellularReturnType.Distance2Sub);
        gg.setCellularDistanceFunction(FastNoiseDouble.CellularDistanceFunction.Natural);

        for (int i = 0; i < getDimension().getCaveLayers().size(); i++) {
            IrisCaveLayer layer = getDimension().getCaveLayers().get(i);
            generateCave(result, wxx, wzz, x, z, data, layer, i);
        }

        return result;
    }

    public void generateCave(KList<CaveResult> result, double wxx, double wzz, int x, int z, Hunk<BlockData> data, IrisCaveLayer layer, int seed) {
        double scale = layer.getCaveZoom();
        Function<Integer, BlockData> fluid = (height) ->
        {
            if (!layer.getFluid().hasFluid(getData())) {
                return CAVE_AIR;
            }

            if (layer.getFluid().isInverseHeight() && height >= layer.getFluid().getFluidHeight()) {
                return layer.getFluid().getFluid(getData());
            } else if (!layer.getFluid().isInverseHeight() && height <= layer.getFluid().getFluidHeight()) {
                return layer.getFluid().getFluid(getData());
            }

            return CAVE_AIR;
        };

        int surface = (int) Math.round(getComplex().getHeightStream().get(wxx, wzz));
        double wx = wxx + layer.getHorizontalSlope().get(rng, getData(), wxx, wzz);
        double wz = wzz + layer.getHorizontalSlope().get(rng, getData(), -wzz, -wxx);
        double baseWidth = (14 * scale);
        double distanceCheck = 0.0132 * baseWidth;
        double distanceTake = 0.0022 * baseWidth;
        double caveHeightNoise = layer.getVerticalSlope().get(rng, getData(), wxx, wzz);

        if (caveHeightNoise > 259 || caveHeightNoise < -1) {
            return;
        }

        // TODO: WARNING HEIGHT
        int ceiling = -256;
        int floor = 512;

        for (double tunnelHeight = 1; tunnelHeight <= baseWidth; tunnelHeight++) {
            double distance = (gg.GetCellular(((wx + (10000 * seed)) / layer.getCaveZoom()), ((wz - (10000 * seed)) / layer.getCaveZoom())) + 1D) / 2D;
            if (distance < distanceCheck - (tunnelHeight * distanceTake)) {
                int caveHeight = (int) Math.round(caveHeightNoise);
                int pu = (int) (caveHeight + tunnelHeight);
                int pd = (int) (caveHeight - tunnelHeight);

                if (pd > surface + 1) {
                    continue;
                }

                if (!layer.isCanBreakSurface() && pu > surface - 3) {
                    continue;
                }

                if ((pu > 255 && pd > 255) || (pu < 0 && pd < 0)) {
                    continue;
                }

                if (data == null) {
                    ceiling = Math.max(pu, ceiling);
                    floor = Math.min(pu, floor);
                    ceiling = Math.max(pd, ceiling);
                    floor = Math.min(pd, floor);

                    if (tunnelHeight == 1) {
                        ceiling = Math.max(caveHeight, ceiling);
                        floor = Math.min(caveHeight, floor);
                    }
                } else {
                    if (dig(x, pu, z, data, fluid)) {
                        ceiling = Math.max(pu, ceiling);
                        floor = Math.min(pu, floor);
                    }

                    if (dig(x, pd, z, data, fluid)) {
                        ceiling = Math.max(pd, ceiling);
                        floor = Math.min(pd, floor);
                    }

                    if (tunnelHeight == 1) {
                        if (dig(x, caveHeight, z, data, fluid)) {
                            ceiling = Math.max(caveHeight, ceiling);
                            floor = Math.min(caveHeight, floor);
                        }
                    }
                }
            }
        }

        if (floor >= 0 && ceiling <= 255) {
            result.add(new CaveResult(floor, ceiling));
        }
    }

    private Material mat(int x, int y, int z, Hunk<BlockData> data) {
        BlockData d = data.get(Math.max(x, 0), Math.max(y, 0), Math.max(z, 0));

        if (d != null) {
            return d.getMaterial();
        }

        return Material.CAVE_AIR;
    }

    public boolean dig(int x, int y, int z, Hunk<BlockData> data, Function<Integer, BlockData> caveFluid) {
        Material a = mat(x, y, z, data);
        Material c = mat(x, y + 1, z, data);
        Material d = mat(x, y + 2, z, data);
        Material e = mat(x, y + 3, z, data);
        Material f = mat(x, y - 1, z, data);
        BlockData b = caveFluid.apply(y);
        BlockData b2 = caveFluid.apply(y + 1);

        if (can(a) && canAir(c, b) && canAir(f, b) && canWater(d) && canWater(e)) {
            data.set(x, y, z, b);
            data.set(x, y + 1, z, b2);
            return true;
        }

        return false;
    }

    public boolean canAir(Material m, BlockData caveFluid) {
        return (B.isSolid(m) ||
                (B.isDecorant(m.createBlockData())) || m.equals(Material.AIR)
                || m.equals(caveFluid.getMaterial()) ||
                m.equals(B.getMaterial("CAVE_AIR")))
                && !m.equals(Material.BEDROCK);
    }

    public boolean canWater(Material m) {
        return !m.equals(Material.WATER);
    }

    public boolean can(Material m) {
        return B.isSolid(m) && !m.equals(Material.BEDROCK);
    }
}
