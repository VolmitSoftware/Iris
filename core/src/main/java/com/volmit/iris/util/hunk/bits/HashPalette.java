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

package com.volmit.iris.util.hunk.bits;

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.function.Consumer2;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HashPalette<T> implements Palette<T> {
    private final LinkedHashMap<T, Integer> palette;
    private final KMap<Integer, T> lookup;
    private final AtomicInteger size;

    public HashPalette() {
        this.size = new AtomicInteger(1);
        this.palette = new LinkedHashMap<>();
        this.lookup = new KMap<>();
    }

    @Override
    public T get(int id) {
        if (id <= 0 || id >= size.get()) {
            return null;
        }

        return lookup.get(id);
    }

    @Override
    public int add(T t) {
        if (t == null) {
            return 0;
        }

        synchronized (palette) {
            return palette.computeIfAbsent(t, $ -> {
                int index = size.getAndIncrement();
                lookup.put(index, t);
                return index;
            });
        }
    }

    @Override
    public int id(T t) {
        if (t == null) {
            return 0;
        }

        Integer v = palette.get(t);
        return v != null ? v : -1;
    }

    @Override
    public int size() {
        return size.get() - 1;
    }

    @Override
    public void iterate(Consumer2<T, Integer> c) {
        synchronized (palette) {
            for (int i = 1; i < size.get(); i++) {
                c.accept(lookup.get(i), i);
            }
        }
    }

    @Override
    public Palette<T> from(Palette<T> oldPalette) {
        oldPalette.iterate((t, i) -> {
            if (t == null) throw new NullPointerException("Null palette entries are not allowed!");
            lookup.put(i, t);
            palette.put(t, i);
        });
        size.set(oldPalette.size() + 1);
        return this;
    }

    @Override
    public Palette<T> from(int size, Writable<T> writable, DataInputStream in) throws IOException {
        for (int i = 1; i <= size; i++) {
            T t = writable.readNodeData(in);
            if (t == null) throw new NullPointerException("Null palette entries are not allowed!");
            lookup.put(i, t);
            palette.put(t, i);
        }
        this.size.set(size + 1);
        return this;
    }
}
