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

package com.volmit.iris.engine.actuator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IMemoryWorld;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedActuator;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.misc.E;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

public class IrisTerrainNormalActuator extends EngineAssignedActuator<BlockData> {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    private static final BlockData DEEPSLATE = Material.DEEPSLATE.createBlockData();
    private static final BlockData LAVA = Material.LAVA.createBlockData();
    private static final BlockData GLASS = Material.GLASS.createBlockData();
    private static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
    private IMemoryWorld memoryWorld;
    @Getter
    private final RNG rng;
    @Getter
    private int lastBedrock = -1;

    public IrisTerrainNormalActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getSeedManager().getTerrain());
        // todo: for v4
//        boolean debug = getDimension().getMerger().isDatapackMode();
//        if (!getDimension().getMerger().getGenerator().isBlank()) {
//            try {
//                if (!getDimension().getMerger().isDatapackMode()) {
//                    this.memoryWorld = INMS.get().createMemoryWorld(new WorldCreator("terrain").generator(getEngine().getDimension().getMerger().getGenerator()));
//                } else {
//                    String test = getDimension().getMerger().getGenerator().toLowerCase();
//                    this.memoryWorld = INMS.get().createMemoryWorld(NamespacedKey.minecraft(test), new WorldCreator("terrain"));
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<BlockData> h, boolean multicore, ChunkContext context) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            AtomicReference<Hunk<BlockData>> hm = new AtomicReference<>();
            if (memoryWorld != null) {
                PaperLib.getChunkAtAsync(memoryWorld.getBukkit(), x, z, true).thenAccept((i) -> {
                    hm.set(toHunk(memoryWorld.getChunkData(x, z)));
                }).get();

            }

            for (int xf = 0; xf < h.getWidth(); xf++) {
                terrainSliver(x, z, xf, h, hm.get(), context);
            }

            getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
        } catch (Exception e) {
            Iris.error("Fatal Error!", e);
        }
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
    public void terrainSliver(int x, int z, int xf, Hunk<BlockData> h, @Nullable Hunk<BlockData> hm, ChunkContext context) {
        int zf, realX, realZ, hf, he;
        IrisBiome biome;
        IrisRegion region;

        for (zf = 0; zf < h.getDepth(); zf++) {
            realX = xf + x;
            realZ = zf + z;
            biome = context.getBiome().get(xf, zf);
            region = context.getRegion().get(xf, zf);
            he = (int) Math.round(Math.min(h.getHeight(), context.getHeight().get(xf, zf)));
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

                    h.set(xf, i, zf, context.getFluid().get(xf, zf));
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

                    BlockData ore = biome.generateOres(realX, i, realZ, rng, getData());
                    ore = ore == null ? region.generateOres(realX, i, realZ, rng, getData()) : ore;
                    ore = ore == null ? getDimension().generateOres(realX, i, realZ, rng, getData()) : ore;

                    if (ore != null) {
                        h.set(xf, i, zf, ore);
                    } else if (hm == null) {
                        // todo remove this ( TEMP )
                        if (getDimension().isDeepslateLayer() && i < 64) {
                            h.set(xf, i, zf, DEEPSLATE);
                        } else {
                            h.set(xf, i, zf, context.getRock().get(xf, zf));
                        }
                    }
                }
            }
        }
    }

    private Hunk<BlockData> toHunk(ChunkGenerator.ChunkData data) {
        Hunk<BlockData> h = Hunk.newArrayHunk(16, memoryWorld.getBukkit().getMaxHeight(), 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < memoryWorld.getBukkit().getMaxHeight(); y++) {
                    BlockData block = data.getBlockData(x, y, z);
                    h.set(x, y, z, block);
                }
            }
        }
        return h;
    }
}
