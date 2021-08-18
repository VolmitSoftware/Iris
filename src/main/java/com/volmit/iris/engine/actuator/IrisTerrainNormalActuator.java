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

package com.volmit.iris.engine.actuator;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.noise.IrisShapedGeneratorStyle;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class IrisTerrainNormalActuator extends EngineAssignedActuator<BlockData> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    private static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
    @Getter
    private final RNG rng;
    private final boolean carving;
    @Getter
    private int lastBedrock = -1;
    private IrisShapedGeneratorStyle domain;

    public IrisTerrainNormalActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getWorld().seed());
        carving = getDimension().isCarving() && getDimension().getCarveLayers().isNotEmpty();
        domain = getDimension().getVerticalDomain();
        domain = domain.isFlat() ? null : domain;
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> h, boolean multicore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();

        if (multicore) {
            BurstExecutor e = getEngine().burst().burst(h.getWidth());
            for (int xf = 0; xf < h.getWidth(); xf++) {
                int finalXf = xf;
                e.queue(() -> terrainSliver(x, z, finalXf, h));
            }

            e.complete();
        } else {
            for (int xf = 0; xf < h.getWidth(); xf++) {
                terrainSliver(x, z, xf, h);
            }
        }

        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }

    public void generateGround(int realX, int realZ, int xf, int zf, Hunk<BlockData> h, int surface, int bottom, int height, int fluidOrHeight, IrisBiome biome)
    {
        if(surface == bottom || surface-1 == bottom)
        {
            return;
        }

        KList<BlockData> blocks = null;
        KList<BlockData> fblocks = null;
        int depth,fdepth;

        for (int i = surface; i >= bottom; i--) {
            if (i >= h.getHeight()) {
                continue;
            }

            if (i == 0) {
                if (getDimension().isBedrock()) {
                    h.set(xf, i, zf, BEDROCK);
                    lastBedrock = i;
                    continue;
                }
            }

            if (carving && getDimension().isCarved(getData(), realX, i, realZ, rng, height)) {
                continue;
            }

            if (getDimension().getCaverns() != null && getDimension().getCaverns().isCavern(rng, realX, i, realZ, height, getData())) {
                continue;
            }

            if (i > height && i <= fluidOrHeight) {
                fdepth = fluidOrHeight - i;

                if (fblocks == null) {
                    fblocks = biome.generateSeaLayers(realX, realZ, rng, fluidOrHeight - height, getData());
                }

                if (fblocks.hasIndex(fdepth)) {
                    h.set(xf, i, zf, fblocks.get(fdepth));
                    continue;
                }

                h.set(xf, i, zf, getComplex().getFluidStream().get(realX, +realZ));
                continue;
            }

            if (i <= height) {
                depth = surface - i;
                if (blocks == null) {
                    blocks = biome.generateLayers(realX, realZ, rng, surface - bottom, surface, getData(), getComplex());
                }

                if (blocks.hasIndex(depth)) {
                    h.set(xf, i, zf, blocks.get(depth));
                    continue;
                }

                h.set(xf, i, zf, getComplex().getRockStream().get(realX, realZ));
            }
        }
    }

    private int fluidOrHeight(int height)
    {
        return Math.max(getDimension().getFluidHeight(), height);
    }

    /**
     * This is calling 1/16th of a chunk x/z slice. It is a plane from sky to bedrock 1 thick in the x direction.
     *
     * @param x  the chunk x in blocks
     * @param z  the chunk z in blocks
     * @param xf the current x slice
     * @param h  the blockdata
     */
    @BlockCoordinates
    public void terrainSliver(int x, int z, int xf, Hunk<BlockData> h) {
        int i, j, k, realX, realZ, hf, he;
        IrisBiome biome;

        for (i = 0; i < h.getDepth(); i++) {
            realX = (int) modX(xf + x);
            realZ = (int) modZ(i + z);

            if(domain != null)
            {
                int[] heights = new int[h.getHeight()];
                IrisBiome[] biomes = new IrisBiome[h.getHeight()];
                int maximum = 0;

                for(j = 0; j < h.getHeight(); j++)
                {
                    double ox = domain.get(rng, getData(), j - 12345);
                    double oz = domain.get(rng, getData(), j + 54321);
                    biomes[j] = getComplex().getTrueBiomeStream().get(realX+ox, realZ+oz);
                    heights[j] = (int) Math.round(Math.min(h.getHeight(), getComplex().getHeightStream().get(realX+ox, realZ+oz)));
                    maximum = Math.max(maximum, heights[j]);
                }

                for(j = maximum; j >= 0; j--) {
                    if(fluidOrHeight(heights[j]) < j)
                    {
                        continue;
                    }

                    int hi = j;
                    int lo = 0;

                    for(k = j; k >= 0; k--)
                    {
                        if(fluidOrHeight(heights[k]) < k)
                        {
                            break;
                        }

                        lo = k;
                        j = k-1;
                    }

                    generateGround(realX, realZ, xf, i, h, hi, lo, heights[hi], fluidOrHeight(heights[hi]), biomes[hi]);
                }
            }

            else
            {
                biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                he = (int) Math.round(Math.min(h.getHeight(), getComplex().getHeightStream().get(realX, realZ)));
                hf = Math.round(Math.max(Math.min(h.getHeight(), getDimension().getFluidHeight()), he));

                if (hf < 0) {
                    continue;
                }

                generateGround(realX, realZ, xf, i, h, hf, 0, he, hf, biome);
            }
        }
    }
}
