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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.object.IrisBiome;
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
    private static final BlockData GLASS = Material.GLASS.createBlockData();
    private static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
    @Getter
    private final RNG rng;
    @Getter
    private int lastBedrock = -1;

    public IrisTerrainNormalActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getSeedManager().getTerrain());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> h, boolean multicore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();

        BurstExecutor e = burst().burst(multicore);
        for (int xf = 0; xf < h.getWidth(); xf++) {
            int finalXf = xf;
            e.queue(() -> terrainSliver(x, z, finalXf, h));
        }

        e.complete();

        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }

    private int fluidOrHeight(int height) {
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
        int zf, realX, realZ, hf, he;
        IrisBiome biome;

        for (zf = 0; zf < h.getDepth(); zf++) {
            realX = xf + x;
            realZ = zf + z;
            biome = getComplex().getTrueBiomeStream().get(realX, realZ);
            he = (int) Math.round(Math.min(h.getHeight(), getComplex().getHeightStream().get(realX, realZ)));
            hf = Math.round(Math.max(Math.min(h.getHeight(), getDimension().getFluidHeight()), he));

            if (hf < 0) {
                continue;
            }

            KList<BlockData> blocks = null;
            KList<BlockData> fblocks = null;
            int depth, fdepth;

            for (int i = hf; i >= 0; i--) {
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

                if (i > he && i <= hf) {
                    fdepth = hf - i;

                    if (fblocks == null) {
                        fblocks = biome.generateSeaLayers(realX, realZ, rng, hf - he, getData());
                    }

                    if (fblocks.hasIndex(fdepth)) {
                        h.set(xf, i, zf, fblocks.get(fdepth));
                        continue;
                    }

                    h.set(xf, i, zf, getComplex().getFluidStream().get(realX, +realZ));
                    continue;
                }

                if (i <= he) {
                    depth = he - i;
                    if (blocks == null) {
                        blocks = biome.generateLayers(getDimension(), realX, realZ, rng,
                                he,
                                he,
                                getData(),
                                getComplex());
                    }

                    if (blocks.hasIndex(depth)) {
                        h.set(xf, i, zf, blocks.get(depth));
                        continue;
                    }

                    h.set(xf, i, zf, getComplex().getRockStream().get(realX, realZ));
                }
            }
        }
    }
}
