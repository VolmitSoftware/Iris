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

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;

import java.util.concurrent.atomic.AtomicInteger;

public class IrisPostModifier extends EngineAssignedModifier<BlockData> {
    private static final BlockData AIR = B.get("AIR");
    private static final BlockData WATER = B.get("WATER");
    private final RNG rng;

    public IrisPostModifier(Engine engine) {
        super(engine, "Post");
        rng = new RNG(getEngine().getSeedManager().getPost());
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        AtomicInteger i = new AtomicInteger();
        AtomicInteger j = new AtomicInteger();
        Hunk<BlockData> sync = output.synchronize();
        for (i.set(0); i.get() < output.getWidth(); i.getAndIncrement()) {
            for (j.set(0); j.get() < output.getDepth(); j.getAndIncrement()) {
                int ii = i.get();
                int jj = j.get();
                post(ii, jj, sync, ii + x, jj + z, context);
            }
        }

        getEngine().getMetrics().getPost().put(p.getMilliseconds());
    }

    private void post(int currentPostX, int currentPostZ, Hunk<BlockData> currentData, int x, int z, ChunkContext context) {
        int h = getEngine().getMantle().trueHeight(x, z);
        int ha = getEngine().getMantle().trueHeight(x + 1, z);
        int hb = getEngine().getMantle().trueHeight(x, z + 1);
        int hc = getEngine().getMantle().trueHeight(x - 1, z);
        int hd = getEngine().getMantle().trueHeight(x, z - 1);

        // Floating Nibs
        int g = 0;

        if (h < 1) {
            return;
        }

        g += ha < h - 1 ? 1 : 0;
        g += hb < h - 1 ? 1 : 0;
        g += hc < h - 1 ? 1 : 0;
        g += hd < h - 1 ? 1 : 0;

        if (g == 4 && isAir(x, h - 1, z, currentPostX, currentPostZ, currentData)) {
            setPostBlock(x, h, z, AIR, currentPostX, currentPostZ, currentData);

            for (int i = h - 1; i > 0; i--) {
                if (!isAir(x, i, z, currentPostX, currentPostZ, currentData)) {
                    h = i;
                    break;
                }
            }
        }

        // Nibs
        g = 0;
        g += ha == h - 1 ? 1 : 0;
        g += hb == h - 1 ? 1 : 0;
        g += hc == h - 1 ? 1 : 0;
        g += hd == h - 1 ? 1 : 0;

        if (g >= 4) {
            BlockData bc = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);
            BlockData b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);
            Material m = bc.getMaterial();

            if ((b.getMaterial().isOccluding() && b.getMaterial().isSolid())) {
                if (m.isSolid()) {
                    setPostBlock(x, h, z, b, currentPostX, currentPostZ, currentData);
                    h--;
                }
            }
        } else {
            // Potholes
            g = 0;
            g += ha == h + 1 ? 1 : 0;
            g += hb == h + 1 ? 1 : 0;
            g += hc == h + 1 ? 1 : 0;
            g += hd == h + 1 ? 1 : 0;

            if (g >= 4) {
                BlockData ba = getPostBlock(x, ha, z, currentPostX, currentPostZ, currentData);
                BlockData bb = getPostBlock(x, hb, z, currentPostX, currentPostZ, currentData);
                BlockData bc = getPostBlock(x, hc, z, currentPostX, currentPostZ, currentData);
                BlockData bd = getPostBlock(x, hd, z, currentPostX, currentPostZ, currentData);
                g = 0;
                g = B.isSolid(ba) ? g + 1 : g;
                g = B.isSolid(bb) ? g + 1 : g;
                g = B.isSolid(bc) ? g + 1 : g;
                g = B.isSolid(bd) ? g + 1 : g;

                if (g >= 3) {
                    setPostBlock(x, h + 1, z, getPostBlock(x, h, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
                    h++;
                }
            }
        }

        // Wall Patcher
        IrisBiome biome = context.getBiome().get(currentPostX, currentPostZ);

        if (getDimension().isPostProcessingWalls()) {
            if (!biome.getWall().getPalette().isEmpty()) {
                if (ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2) {
                    boolean brokeGround = false;
                    int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

                    for (int i = h; i > h - max; i--) {
                        BlockData d = biome.getWall().get(rng, x + i, i + h, z + i, getData());

                        if (d != null) {
                            if (isAirOrWater(x, i, z, currentPostX, currentPostZ, currentData)) {
                                if (brokeGround) {
                                    break;
                                }

                                continue;
                            }

                            setPostBlock(x, i, z, d, currentPostX, currentPostZ, currentData);
                            brokeGround = true;
                        }
                    }
                }
            }
        }

        // Slab
        if (getDimension().isPostProcessingSlabs()) {
            //@builder
            if ((ha == h + 1 && isSolidNonSlab(x + 1, ha, z, currentPostX, currentPostZ, currentData))
                    || (hb == h + 1 && isSolidNonSlab(x, hb, z + 1, currentPostX, currentPostZ, currentData))
                    || (hc == h + 1 && isSolidNonSlab(x - 1, hc, z, currentPostX, currentPostZ, currentData))
                    || (hd == h + 1 && isSolidNonSlab(x, hd, z - 1, currentPostX, currentPostZ, currentData)))
            //@done
            {
                BlockData d = biome.getSlab().get(rng, x, h, z, getData());

                if (d != null) {
                    boolean cancel = B.isAir(d);

                    if (d.getMaterial().equals(Material.SNOW) && h + 1 <= getDimension().getFluidHeight()) {
                        cancel = true;
                    }

                    if (isSnowLayer(x, h, z, currentPostX, currentPostZ, currentData)) {
                        cancel = true;
                    }

                    if (!cancel && isAirOrWater(x, h + 1, z, currentPostX, currentPostZ, currentData)) {
                        setPostBlock(x, h + 1, z, d, currentPostX, currentPostZ, currentData);
                        h++;
                    }
                }
            }
        }

        // Waterlogging
        BlockData b = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

        if (b instanceof Waterlogged) {
            Waterlogged ww = (Waterlogged) b.clone();
            boolean w = false;

            if (h <= getDimension().getFluidHeight() + 1) {
                if (isWaterOrWaterlogged(x, h + 1, z, currentPostX, currentPostZ, currentData)) {
                    w = true;
                } else if ((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData))) {
                    w = true;
                }
            }

            if (w != ww.isWaterlogged()) {
                ww.setWaterlogged(w);
                setPostBlock(x, h, z, ww, currentPostX, currentPostZ, currentData);
            }
        } else if (b.getMaterial().equals(Material.AIR) && h <= getDimension().getFluidHeight()) {
            if ((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData))) {
                setPostBlock(x, h, z, WATER, currentPostX, currentPostZ, currentData);
            }
        }

        // Foliage
        b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

        if (B.isVineBlock(b) && b instanceof MultipleFacing f) {
            int finalH = h + 1;

            f.getAllowedFaces().forEach(face -> {
                BlockData d = getPostBlock(x + face.getModX(), finalH + face.getModY(), z + face.getModZ(), currentPostX, currentPostZ, currentData);
                f.setFace(face, !B.isAir(d) && !B.isVineBlock(d));
            });
            setPostBlock(x, h + 1, z, b, currentPostX, currentPostZ, currentData);
        }

        if (B.isFoliage(b) || b.getMaterial().equals(Material.DEAD_BUSH)) {
            Material onto = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData).getMaterial();

            if (!B.canPlaceOnto(b.getMaterial(), onto) && !B.isDecorant(b)) {
                setPostBlock(x, h + 1, z, AIR, currentPostX, currentPostZ, currentData);
            }
        }
    }

    public boolean isAir(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
    }

    public boolean hasGravity(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.SAND) || d.getMaterial().equals(Material.RED_SAND) || d.getMaterial().equals(Material.BLACK_CONCRETE_POWDER) || d.getMaterial().equals(Material.BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.BROWN_CONCRETE_POWDER) || d.getMaterial().equals(Material.CYAN_CONCRETE_POWDER) || d.getMaterial().equals(Material.GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.GREEN_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIME_CONCRETE_POWDER) || d.getMaterial().equals(Material.MAGENTA_CONCRETE_POWDER) || d.getMaterial().equals(Material.ORANGE_CONCRETE_POWDER) || d.getMaterial().equals(Material.PINK_CONCRETE_POWDER) || d.getMaterial().equals(Material.PURPLE_CONCRETE_POWDER) || d.getMaterial().equals(Material.RED_CONCRETE_POWDER) || d.getMaterial().equals(Material.WHITE_CONCRETE_POWDER) || d.getMaterial().equals(Material.YELLOW_CONCRETE_POWDER);
    }

    public boolean isSolid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().isSolid() && !B.isVineBlock(d);
    }

    public boolean isSolidNonSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().isSolid() && !(d instanceof Slab);
    }

    public boolean isAirOrWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
    }

    public boolean isSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d instanceof Slab;
    }

    public boolean isSnowLayer(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.SNOW);
    }

    public boolean isWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER);
    }

    public boolean isWaterOrWaterlogged(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d.getMaterial().equals(Material.WATER) || (d instanceof Waterlogged && ((Waterlogged) d).isWaterlogged());
    }

    public boolean isLiquid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return d instanceof Levelled;
    }

    public void setPostBlock(int x, int y, int z, BlockData d, int currentPostX, int currentPostZ, Hunk<BlockData> currentData) {
        if (y < currentData.getHeight()) {
            currentData.set(x & 15, y, z & 15, d);
        }
    }

    public BlockData getPostBlock(int x, int y, int z, int cpx, int cpz, Hunk<BlockData> h) {
        BlockData b = h.getClosest(x & 15, y, z & 15);

        return b == null ? AIR : b;
    }
}
