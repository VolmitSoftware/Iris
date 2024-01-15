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

package com.volmit.iris.engine.framework;

import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import lombok.Data;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class PregeneratedData {
    private final Hunk<BlockData> blocks;
    private final Hunk<BlockData> post;
    private final Hunk<Biome> biomes;
    private final AtomicBoolean postMod;

    public PregeneratedData(int height) {
        postMod = new AtomicBoolean(false);
        blocks = Hunk.newAtomicHunk(16, height, 16);
        biomes = Hunk.newAtomicHunk(16, height, 16);
        Hunk<BlockData> p = Hunk.newMappedHunkSynced(16, height, 16);
        post = p.trackWrite(postMod);
    }

    public Runnable inject(TerrainChunk tc) {
        blocks.iterateSync((x, y, z, b) -> {
            if (b != null) {
                tc.setBlock(x, y, z, b);
            }

            Biome bf = biomes.get(x, y, z);
            if (bf != null) {
                tc.setBiome(x, y, z, bf);
            }
        });

        if (postMod.get()) {
            return () -> Hunk.view(tc).insertSoftly(0, 0, 0, post, (b) -> b == null || B.isAirOrFluid(b));
        }

        return () -> {
        };
    }
}
