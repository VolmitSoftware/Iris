/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.modded;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

public final class ModdedBlockBuffer implements Hunk<PlatformBlockState> {
    private final PlatformBlockState[] data;
    private final PlatformBlockState air;
    private final int height;

    public ModdedBlockBuffer(int height, PlatformBlockState air) {
        this.data = new PlatformBlockState[16 * height * 16];
        this.air = air;
        this.height = height;
    }

    private int index(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }

    public boolean isAir(int x, int y, int z) {
        return data[index(x, y, z)] == null;
    }

    /**
     * Direct slot read, null when unset — lets writeBlocks pay one index + one array load per
     * block instead of the isAir + getRaw pair.
     */
    public PlatformBlockState rawOrNull(int x, int y, int z) {
        return data[index(x, y, z)];
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
        return height;
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState t) {
        if (t == null) {
            return;
        }
        if (x < 0 || y < 0 || z < 0 || x >= 16 || y >= height || z >= 16) {
            return;
        }
        data[index(x, y, z)] = t;
    }

    @Override
    public void setRaw(int x, int y, int z, PlatformBlockState t) {
        if (t == null) {
            return;
        }
        data[index(x, y, z)] = t;
    }

    @Override
    public PlatformBlockState getRaw(int x, int y, int z) {
        PlatformBlockState state = data[index(x, y, z)];
        return state == null ? air : state;
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= 16 || y >= height || z >= 16) {
            return air;
        }
        return getRaw(x, y, z);
    }
}
