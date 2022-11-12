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

package com.volmit.iris.util.hunk.storage;

import com.volmit.iris.util.hunk.Hunk;
import lombok.Data;

@Data
public abstract class StorageHunk<T> implements Hunk<T> {
    private final int width;
    private final int height;
    private final int depth;

    public StorageHunk(int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new RuntimeException("Unsupported size " + width + " " + height + " " + depth);
        }

        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    @Override
    public abstract void setRaw(int x, int y, int z, T t);

    @Override
    public abstract T getRaw(int x, int y, int z);
}
