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

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.hunk.Hunk;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Arrays;

@SuppressWarnings("Lombok")
@Data
@EqualsAndHashCode(callSuper = false)
public class ArrayHunk<T> extends StorageHunk<T> implements Hunk<T> {
    private final T[] data;

    @SuppressWarnings("unchecked")
    public ArrayHunk(int w, int h, int d) {
        super(w, h, d);
        data = (T[]) new Object[w * h * d];
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {
        data[index(x, y, z)] = t;
    }

    @Override
    public T getRaw(int x, int y, int z) {
        return data[index(x, y, z)];
    }

    private int index(int x, int y, int z) {
        return Cache.to1D(x, y, z, getWidth(), getHeight());
    }

    @Override
    public void fill(T t) {
        Arrays.fill(data, t);
    }
}
