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

import com.volmit.iris.util.math.IrisMathHelper;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.jetbrains.annotations.NotNull;

public class IrisBiomeStorage implements BiomeGrid {
    public static final int a;
    public static final int b;
    public static final int c;
    private static final int e;
    private static final int f;

    static {
        e = (int) Math.round(Math.log(16.0) / Math.log(2.0)) - 2;
        f = (int) Math.round(Math.log(256.0) / Math.log(2.0)) - 2; // TODO: WARNING HEIGHT
        a = 1 << IrisBiomeStorage.e + IrisBiomeStorage.e + IrisBiomeStorage.f;
        b = (1 << IrisBiomeStorage.e) - 1;
        c = (1 << IrisBiomeStorage.f) - 1;
    }

    private final Biome[] g;

    public IrisBiomeStorage(final Biome[] aBiome) {
        this.g = aBiome;
    }

    public IrisBiomeStorage() {
        this(new Biome[IrisBiomeStorage.a]);
    }

    public IrisBiomeStorage b() {
        return new IrisBiomeStorage(this.g.clone());
    }

    public void inject(BiomeGrid grid) {
        // TODO: WARNING HEIGHT
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    Biome b = getBiome(j, i, k);

                    if (b == null || b.equals(Biome.THE_VOID)) {
                        continue;
                    }

                    grid.setBiome(j, i, k, b);
                }
            }
        }
    }

    @NotNull
    @Override
    public Biome getBiome(int x, int z) {
        return null;
    }

    public Biome getBiome(final int x, final int y, final int z) {
        final int l = x & IrisBiomeStorage.b;
        final int i2 = IrisMathHelper.clamp(y, 0, IrisBiomeStorage.c);
        final int j2 = z & IrisBiomeStorage.b;
        return this.g[i2 << IrisBiomeStorage.e + IrisBiomeStorage.e | j2 << IrisBiomeStorage.e | l];
    }

    @Override
    public void setBiome(int x, int z, @NotNull Biome bio) {

    }

    public void setBiome(final int x, final int y, final int z, final Biome biome) {
        final int l = x & IrisBiomeStorage.b;
        final int i2 = IrisMathHelper.clamp(y, 0, IrisBiomeStorage.c);
        final int j2 = z & IrisBiomeStorage.b;
        this.g[i2 << IrisBiomeStorage.e + IrisBiomeStorage.e | j2 << IrisBiomeStorage.e | l] = biome;
    }
}