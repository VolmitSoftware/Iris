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

import com.volmit.iris.util.function.Consumer2;
import lombok.Synchronized;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class LinearPalette<T> implements Palette<T> {
    private volatile AtomicReferenceArray<T> palette;
    private final AtomicInteger size;

    public LinearPalette(int initialSize) {
        this.size = new AtomicInteger(1);
        this.palette = new AtomicReferenceArray<>(initialSize);
        palette.set(0, null);
    }

    @Override
    public T get(int id) {
        if (id < 0 || id >= size.get()) {
            return null;
        }

        return palette.get(id);
    }

    @Override
    public int add(T t) {
        int index = size.getAndIncrement();
        if (palette.length() <= index)
            grow(index);
        palette.set(index, t);
        return index;
    }

    private synchronized void grow(int newLength) {
        if (palette.length() <= newLength)
            return;

        AtomicReferenceArray<T> a = new AtomicReferenceArray<>(newLength + 1);
        for (int i = 0; i < palette.length(); i++) {
            a.set(i, palette.get(i));
        }

        palette = a;
    }

    @Override
    public int id(T t) {
        if (t == null) {
            return 0;
        }

        for (int i = 1; i < size.get(); i++) {
            if (t.equals(palette.get(i))) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int size() {
        return size.get() - 1;
    }

    @Override
    public void iterate(Consumer2<T, Integer> c) {
        for (int i = 1; i <= size(); i++) {
            c.accept(palette.get(i), i);
        }
    }
}
