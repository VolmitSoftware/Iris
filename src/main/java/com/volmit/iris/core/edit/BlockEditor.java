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

package com.volmit.iris.core.edit;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.Closeable;

public interface BlockEditor extends Closeable {
    long last();

    void set(int x, int y, int z, BlockData d);

    BlockData get(int x, int y, int z);

    void setBiome(int x, int z, Biome b);

    void setBiome(int x, int y, int z, Biome b);

    @Override
    void close();

    Biome getBiome(int x, int y, int z);

    Biome getBiome(int x, int z);
}
