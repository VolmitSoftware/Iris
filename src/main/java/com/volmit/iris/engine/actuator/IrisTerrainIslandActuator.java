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
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class IrisTerrainIslandActuator extends EngineAssignedActuator<BlockData> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    private static final BlockData WEB = Material.COBWEB.createBlockData();
    private static final BlockData BLACK_GLASS = Material.BLACK_STAINED_GLASS.createBlockData();
    private static final BlockData WHITE_GLASS = Material.WHITE_STAINED_GLASS.createBlockData();
    private static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
    @Getter
    private final RNG rng;
    private final boolean carving;
    private final boolean hasUnder;
    @Getter
    private final int lastBedrock = -1;

    public IrisTerrainIslandActuator(Engine engine) {
        super(engine, "TerrainIsland");
        rng = new RNG(engine.getWorld().seed());
        carving = getDimension().isCarving() && getDimension().getCarveLayers().isNotEmpty();
        hasUnder = getDimension().getUndercarriage() != null && !getDimension().getUndercarriage().getGenerator().isFlat();
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> h, boolean multicore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int i, zf, depth, surface, realX, realZ;
        IrisBiome biome;
        KList<BlockData> blocks, fblocks;
        int hi, lo;
        double hh;

        for (int xf = 0; xf < h.getWidth(); xf++) {
            for (zf = 0; zf < h.getDepth(); zf++) {
                realX = (int) modX(xf + x);
                realZ = (int) modZ(zf + z);

                if (getComplex().getIslandStream().get(realX, realZ)) {
                    biome = getComplex().getTrueBiomeStream().get(realX, realZ);
                    hh = getComplex().getTrueHeightStream().get(realX, realZ) - getComplex().getFluidHeight();
                    depth = (int) (getComplex().getIslandDepthStream().get(realX, realZ).intValue() + hh);
                    blocks = biome.generateLayers(realX, realZ, rng, depth, depth, getData(), getComplex());
                    hi = getComplex().getIslandTopStream().get(realX, realZ);
                    lo = getComplex().getIslandBottomStream().get(realX, realZ);

                    // 10
                    // 6

                    // hf = 4

                    for (i = hi; i >= lo; i--) {
                        int hf = (i - hi);
                        if (blocks.hasIndex(hf)) {
                            h.set(xf, i, zf, blocks.get(hf));
                            continue;
                        }

                        h.set(xf, i, zf, getComplex().getRockStream().get(realX, realZ));
                    }
                }
            }
        }

        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }
}
