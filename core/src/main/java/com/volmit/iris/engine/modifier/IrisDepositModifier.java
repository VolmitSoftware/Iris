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
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.HeightMap;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Material;
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
        generateDeposits(output, Math.floorDiv(x, 16), Math.floorDiv(z, 16), multicore, context);
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void generateDeposits(Hunk<BlockData> terrain, int x, int z, boolean multicore, ChunkContext context) {
        IrisRegion region = context.getRegion().get(7, 7);
        IrisBiome biome = context.getBiome().get(7, 7);
        BurstExecutor burst = burst().burst(multicore);

        long seed = x * 341873128712L + z * 132897987541L;
        long mask = 0;
        MantleChunk chunk = getEngine().getMantle().getMantle().getChunk(x, z).use();
        for (IrisDepositGenerator k : getDimension().getDeposits()) {
            long finalSeed = seed * ++mask;
            burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
        }

        for (IrisDepositGenerator k : region.getDeposits()) {
            long finalSeed = seed * ++mask;
            burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
        }

        for (IrisDepositGenerator k : biome.getDeposits()) {
            long finalSeed = seed * ++mask;
            burst.queue(() -> generate(k, chunk, terrain, rng.nextParallelRNG(finalSeed), x, z, false, context));
        }
        burst.complete();
        chunk.release();
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe, ChunkContext context) {
        generate(k, chunk, data, rng, cx, cz, safe, null, context);
    }

    public void generate(IrisDepositGenerator k, MantleChunk chunk, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe, HeightMap he, ChunkContext context) {
        if (k.getSpawnChance() < rng.d())
            return;

        for (int l = 0; l < rng.i(k.getMinPerChunk(), k.getMaxPerChunk() + 1); l++) {
            if (k.getPerClumpSpawnChance() < rng.d())
                continue;

            IrisObject clump = k.getClump(getEngine(), rng, getData());

            int dim = clump.getW();
            int min = dim / 2;
            int max = (int) (16D - dim / 2D);

            if (min > max || min < 0 || max > 15) {
                min = 6;
                max = 9;
            }

            int x = rng.i(min, max + 1);
            int z = rng.i(min, max + 1);
            int height = (he != null ? he.getHeight((cx << 4) + x, (cz << 4) + z) : (int) (Math.round(
                    context.getHeight().get(x, z)
            ))) - 7;

            if (height <= 0)
                continue;

            int minY = Math.max(0, k.getMinHeight());
            // TODO: WARNING HEIGHT
            int maxY = Math.min(height, Math.min(getEngine().getHeight(), k.getMaxHeight()));

            if (minY >= maxY)
                continue;

            int y = rng.i(minY, maxY + 1);

            if (y > k.getMaxHeight() || y < k.getMinHeight() || y > height - 2)
                continue;

            for (BlockVector j : clump.getBlocks().keySet()) {
                int nx = j.getBlockX() + x;
                int ny = j.getBlockY() + y;
                int nz = j.getBlockZ() + z;

                if (ny > height || nx > 15 || nx < 0 || ny > getEngine().getHeight() || ny < 0 || nz < 0 || nz > 15) {
                    continue;
                }
                if (!k.isReplaceBedrock() && data.get(nx, ny, nz).getMaterial() == Material.BEDROCK) {
                    continue;
                }

                if (chunk.get(nx, ny, nz, MatterCavern.class) == null) {
                    data.set(nx, ny, nz, B.toDeepSlateOre(data.get(nx, ny, nz), clump.getBlocks().get(j)));
                }
            }
        }
    }
}
