/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.chunk;

import art.arcane.iris.platform.bukkit.BukkitBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.util.hunk.HunkMutationSupport;
import art.arcane.volmlib.util.hunk.storage.StorageHunk;
import org.bukkit.block.Biome;

/**
 * Biome output for one Bukkit chunk, read back only by the generation stages. The biome actuator writes whole
 * columns, so storage stays at one entry per column until a partial column write arrives; that write switches
 * the hunk to full resolution. Reads, clamping and the plains fallback match {@link LinkedTerrainChunk}.
 */
public final class ColumnBiomeHunk extends StorageHunk<NativeBiome> {
    private static final int CHUNK_SIZE = 16;

    private final NativeBiome[] columns = new NativeBiome[CHUNK_SIZE * CHUNK_SIZE];
    private NativeBiome[] cells;
    private volatile NativeBiome defaultBiome;

    public ColumnBiomeHunk(int height) {
        super(CHUNK_SIZE, height, CHUNK_SIZE);
    }

    @Override
    public void set(int x1, int y1, int z1, int x2, int y2, int z2, NativeBiome biome) {
        if (x1 == x2 && z1 == z2 && y1 == 0 && y2 == getHeight() - 1) {
            fillColumn(x1, z1, LinkedTerrainChunk.canonicalBiome(biome));
            return;
        }

        HunkMutationSupport.setRangeInclusive(this, x1, y1, z1, x2, y2, z2, biome);
    }

    @Override
    public void setRaw(int x, int y, int z, NativeBiome biome) {
        NativeBiome[] storage = cells == null ? expand() : cells;
        storage[cellIndex(x, y, z)] = LinkedTerrainChunk.canonicalBiome(biome);
    }

    @Override
    public NativeBiome getRaw(int x, int y, int z) {
        NativeBiome[] storage = cells;
        NativeBiome biome = storage == null ? columns[columnIndex(x, z)] : storage[cellIndex(x, y, z)];
        if (biome != null) {
            return biome;
        }
        NativeBiome fallback = defaultBiome;
        if (fallback == null) {
            fallback = BukkitBiome.of(Biome.PLAINS);
            defaultBiome = fallback;
        }
        return fallback;
    }

    private void fillColumn(int x, int z, NativeBiome biome) {
        NativeBiome[] storage = cells;
        if (storage == null) {
            columns[columnIndex(x, z)] = biome;
            return;
        }
        int stride = CHUNK_SIZE * CHUNK_SIZE;
        int index = columnIndex(x, z);
        for (int y = 0; y < getHeight(); y++) {
            storage[index] = biome;
            index += stride;
        }
    }

    private NativeBiome[] expand() {
        NativeBiome[] storage = new NativeBiome[CHUNK_SIZE * getHeight() * CHUNK_SIZE];
        int stride = CHUNK_SIZE * CHUNK_SIZE;
        for (int column = 0; column < stride; column++) {
            NativeBiome biome = columns[column];
            if (biome == null) {
                continue;
            }
            for (int index = column; index < storage.length; index += stride) {
                storage[index] = biome;
            }
        }
        cells = storage;
        return storage;
    }

    private static int columnIndex(int x, int z) {
        return ((z & (CHUNK_SIZE - 1)) * CHUNK_SIZE) + (x & (CHUNK_SIZE - 1));
    }

    private int cellIndex(int x, int y, int z) {
        int clampedY = Math.max(0, Math.min(getHeight() - 1, y));
        return (clampedY * CHUNK_SIZE * CHUNK_SIZE) + columnIndex(x, z);
    }
}
