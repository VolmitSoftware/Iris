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

package com.volmit.iris.util.data;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

public class InvertedBiomeGrid implements BiomeGrid {
    private final BiomeGrid grid;

    public InvertedBiomeGrid(BiomeGrid real) {
        this.grid = real;
    }


    @SuppressWarnings("deprecation")
    @Override
    public Biome getBiome(int arg0, int arg1) {
        return grid.getBiome(arg0, arg1);
    }


    @Override
    public Biome getBiome(int arg0, int arg1, int arg2) {
        return grid.getBiome(arg0, 255 - arg1, arg2);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBiome(int arg0, int arg1, Biome arg2) {
        grid.setBiome(arg0, arg1, arg2);
    }

    @Override
    public void setBiome(int arg0, int arg1, int arg2, Biome arg3) {
        grid.setBiome(arg0, 255 - arg1, arg2, arg3);
    }
}
