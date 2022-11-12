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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.Sliced;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class BlockMatter extends RawMatter<BlockData> {
    public static final BlockData AIR = Material.AIR.createBlockData();

    public BlockMatter() {
        this(1, 1, 1);
    }

    public BlockMatter(int width, int height, int depth) {
        super(width, height, depth, BlockData.class);
        registerWriter(World.class, ((w, d, x, y, z) -> w.getBlockAt(x, y, z).setBlockData(d)));
        registerReader(World.class, (w, x, y, z) -> {
            BlockData d = w.getBlockAt(x, y, z).getBlockData();
            return d.getMaterial().isAir() ? null : d;
        });
    }

    @Override
    public Palette<BlockData> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(BlockData b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getAsString(true));
    }

    @Override
    public BlockData readNode(DataInputStream din) throws IOException {
        return Bukkit.createBlockData(din.readUTF());
    }
}
