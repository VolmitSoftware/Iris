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

package com.volmit.iris.util.hunk.view;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.EditSVC;
import com.volmit.iris.util.hunk.Hunk;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkBiomeHunkView implements Hunk<Biome> {
    private final Chunk chunk;

    public ChunkBiomeHunkView(Chunk chunk) {
        this.chunk = chunk;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getDepth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return chunk.getWorld().getMaxHeight();
    }

    @Override
    public void setRaw(int x, int y, int z, Biome t) {
        if (t == null) {
            return;
        }

        Iris.service(EditSVC.class).setBiome(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16), t);
    }

    @Override
    public Biome getRaw(int x, int y, int z) {
        return Iris.service(EditSVC.class)
                .getBiome(chunk.getWorld(), x + (chunk.getX() * 16), y, z + (chunk.getZ() * 16));
    }
}
