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

package com.volmit.iris.util.nbt.mca.palette;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class MCAChunkBiomeContainer<T> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int WIDTH_BITS = MCAMth.ceillog2(16) - 2;
    private static final int HORIZONTAL_MASK = (1 << WIDTH_BITS) - 1;
    private static final int PACKED_X_LENGTH = 1 + MCAMth.log2(MCAMth.smallestEncompassingPowerOfTwo(30000000));
    private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
    public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
    public static final int MAX_SIZE = 1 << WIDTH_BITS + WIDTH_BITS + PACKED_Y_LENGTH - 2;

    public final MCAIdMap<T> biomeRegistry;

    private final T[] biomes;

    private final int quartMinY;

    private final int quartHeight;

    protected MCAChunkBiomeContainer(MCAIdMap<T> registry, int minHeight, int maxHeight, T[] abiomebase) {
        this.biomeRegistry = registry;
        this.biomes = abiomebase;
        this.quartMinY = MCAQuartPos.fromBlock(minHeight);
        this.quartHeight = MCAQuartPos.fromBlock(maxHeight) - 1;
    }

    public MCAChunkBiomeContainer(MCAIdMap<T> registry, int min, int max) {
        this(registry, min, max, new int[(1 << WIDTH_BITS + WIDTH_BITS) * ceilDiv(max - min, 4)]);
    }

    public MCAChunkBiomeContainer(MCAIdMap<T> registry, int minHeight, int maxHeight, int[] aint) {
        this(registry, minHeight, maxHeight, (T[]) new Object[aint.length]);
        int i = -1;
        for (int j = 0; j < this.biomes.length; j++) {
            int k = aint[j];
            T biomebase = registry.byId(k);
            if (biomebase == null) {
                if (i == -1)
                    i = j;
                this.biomes[j] = registry.byId(0);
            } else {
                this.biomes[j] = biomebase;
            }
        }
        if (i != -1)
            LOGGER.warn("Invalid biome data received, starting from {}: {}", Integer.valueOf(i), Arrays.toString(aint));
    }

    private static int ceilDiv(int i, int j) {
        return (i + j - 1) / j;
    }

    public int[] writeBiomes() {
        int[] aint = new int[this.biomes.length];
        for (int i = 0; i < this.biomes.length; i++)
            aint[i] = this.biomeRegistry.getId(this.biomes[i]);
        return aint;
    }

    public T getBiome(int i, int j, int k) {
        int l = i & HORIZONTAL_MASK;
        int i1 = MCAMth.clamp(j - this.quartMinY, 0, this.quartHeight);
        int j1 = k & HORIZONTAL_MASK;
        return this.biomes[i1 << WIDTH_BITS + WIDTH_BITS | j1 << WIDTH_BITS | l];
    }

    public void setBiome(int i, int j, int k, T biome) {
        int l = i & HORIZONTAL_MASK;
        int i1 = MCAMth.clamp(j - this.quartMinY, 0, this.quartHeight);
        int j1 = k & HORIZONTAL_MASK;
        this.biomes[i1 << WIDTH_BITS + WIDTH_BITS | j1 << WIDTH_BITS | l] = biome;
    }
}