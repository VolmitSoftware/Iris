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

package com.volmit.iris.util.cache;

import com.volmit.iris.util.hunk.bits.DataContainer;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

public abstract class DataBitCache<T> implements ArrayCache<T> {
    @Getter
    private final int width;
    @Getter
    private final int height;
    private final DataContainer<T> cache;

    public DataBitCache(int width, int height) {
        this.width = width;
        this.height = height;
        cache = new DataContainer<>(this, width * height);
    }

    public void set(int i, T v) {
        cache.set(i, v);
    }

    public T get(int i) {
        return cache.get(i);
    }

    @Override
    public void writeCache(DataOutputStream dos) throws IOException {
        dos.writeInt(width);
        dos.writeInt(height);
        cache.writeDos(dos);
    }
}
