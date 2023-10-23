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
import com.volmit.iris.engine.object.IrisDepositGenerator;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.HeightMap;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

public class IrisDepositModifier extends EngineAssignedModifier<BlockData> {
    private final RNG rng;

    public IrisDepositModifier(Engine engine) {
        super(engine, "Deposit");
        rng = new RNG(getEngine().getSeedManager().getDeposit());
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateDeposits(rng, output, Math.floorDiv(x, 16), Math.floorDiv(z, 16), multicore, context);
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void generateDeposits(RNG rx, Hunk<BlockData> terrain, int x, int z, boolean multicore, ChunkContext context) {
        RNG ro = rx.nextParallelRNG(x * x).nextParallelRNG(z * z);
        IrisRegion region = context.getRegion().get(7, 7);
        IrisBiome biome = context.getBiome().get(7, 7);
        BurstExecutor burst = burst().burst(multicore);

        for (IrisDepositGenerator k : getDimension().getDeposits()) {
            burst.queue(() -> generate(k, terrain, ro, x, z, false, context));
        }

        for (IrisDepositGenerator k : region.getDeposits()) {
            for (int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++) {
                burst.queue(() -> generate(k, terrain, ro, x, z, false, context));
            }
        }

        for (IrisDepositGenerator k : biome.getDeposits()) {
            for (int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++) {
                burst.queue(() -> generate(k, terrain, ro, x, z, false, context));
            }
        }
        burst.complete();
    }

    public void generate(IrisDepositGenerator k, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, data, rng, cx, cz, safe, null, context);
    }

    public void generate(IrisDepositGenerator k, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        for (int l = 0; l < rng.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++) {
            IrisObject clump = k.getClump(rng, getData());

            int af = (int) Math.floor(clump.getW() / 2D);
            int bf = (int) Math.floor(16D - (clump.getW() / 2D));

            if (af > bf || af < 0 || bf > 15) {
                af = 6;
                bf = 9;
            }

            af = Math.max(af - 1, 0);
            int x = rng.i(af, bf);
            int z = rng.i(af, bf);
            int height = (he != null ? he.getHeight((cx << 4) + x, (cz << 4) + z) : (int) (Math.round(
                    context.getHeight().get(x, z)
            ))) - 7;

            if (height <= 0) {
                return;
            }

            int i = Math.max(0, k.getMinHeight());
            // TODO: WARNING HEIGHT
            int a = Math.min(height, Math.min(getEngine().getHeight(), k.getMaxHeight()));

            if (i >= a) {
                return;
            }

            int h = rng.i(i, a);

            if (h > k.getMaxHeight() || h < k.getMinHeight() || h > height - 2) {
                return;
            }

            for (BlockVector j : clump.getBlocks().keySet()) {
                int nx = j.getBlockX() + x;
                int ny = j.getBlockY() + h;
                int nz = j.getBlockZ() + z;

                if (ny > height || nx > 15 || nx < 0 || ny > getEngine().getHeight() || ny < 0 || nz < 0 || nz > 15) {
                    continue;
                }

                if (!getEngine().getMantle().isCarved((cx << 4) + nx, ny, (cz << 4) + nz)) {
                    data.set(nx, ny, nz, B.toDeepSlateOre(data.get(nx, ny, nz), clump.getBlocks().get(j)));
                }
            }
        }
    }
}
