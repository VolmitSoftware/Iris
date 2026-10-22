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

package art.arcane.iris.probe;

import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.iris.generation.chunk.ColumnExtent;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockVolume;
import art.arcane.volmlib.util.hunk.storage.StorageHunk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

final class ProductionBlockHunk extends StorageHunk<NativeBlockState> implements NativeBlockVolume, ColumnExtent {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");
    private static final VarHandle COLUMN_TOPS = MethodHandles.arrayElementVarHandle(int[].class);

    private final NativeBlockState[] data;
    private final int[] columnTops;
    private final int zStride;

    ProductionBlockHunk(int height) {
        super(16, height, 16);
        this.zStride = 16 * height;
        this.data = new NativeBlockState[zStride * 16];
        this.columnTops = new int[256];
        Arrays.fill(columnTops, -1);
    }

    @Override
    public void setRaw(int x, int y, int z, NativeBlockState t) {
        if (t == null) {
            return;
        }
        data[index(x, y, z)] = t;
        int column = (z << 4) | x;
        int top = (int) COLUMN_TOPS.getOpaque(columnTops, column);
        while (y > top) {
            int witness = (int) COLUMN_TOPS.compareAndExchange(columnTops, column, top, y);
            if (witness == top) {
                break;
            }
            top = witness;
        }
    }

    @Override
    public NativeBlockState getRaw(int x, int y, int z) {
        if (y < 0 || y >= getHeight()) {
            return AIR.get();
        }
        NativeBlockState state = data[index(x, y, z)];
        return state != null ? state : AIR.get();
    }

    @Override
    public NativeBlockState getStoredRaw(int x, int y, int z) {
        return data[index(x, y, z)];
    }

    @Override
    public int highestStoredY(int x, int z) {
        return (int) COLUMN_TOPS.getOpaque(columnTops, (z << 4) | x);
    }

    private int index(int x, int y, int z) {
        return (z * zStride) + (y << 4) + x;
    }
}
