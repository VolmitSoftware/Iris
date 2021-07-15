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

package com.volmit.iris.util;

import com.volmit.iris.Iris;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.jetbrains.annotations.NotNull;

public class InvertedBiomeGrid implements BiomeGrid {
    private final BiomeGrid grid;

    public InvertedBiomeGrid(BiomeGrid real) {
        this.grid = real;
    }

    @NotNull
    @SuppressWarnings("deprecation")
    @Override
    public Biome getBiome(int arg0, int arg1) {
        return grid.getBiome(arg0, arg1);
    }

    @NotNull
    @Override
    public Biome getBiome(int arg0, int arg1, int arg2) {
        if (!Iris.biome3d) {
            return getBiome(arg0, arg2);
        }

        return grid.getBiome(arg0, 255 - arg1, arg2);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBiome(int arg0, int arg1, @NotNull Biome arg2) {
        grid.setBiome(arg0, arg1, arg2);
    }

    @Override
    public void setBiome(int arg0, int arg1, int arg2, @NotNull Biome arg3) {
        if (!Iris.biome3d) {
            setBiome(arg0, arg2, arg3);
            return;
        }

        grid.setBiome(arg0, 255 - arg1, arg2, arg3);
    }
}
