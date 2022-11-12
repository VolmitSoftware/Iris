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

import com.volmit.iris.util.hunk.bits.Writable;

import java.io.DataOutputStream;
import java.io.IOException;

public interface ArrayCache<T> extends Writable<T> {
    static int zigZag(int coord, int size) {
        if (coord < 0) {
            coord = Math.abs(coord);
        }

        if (coord % (size * 2) >= size) {
            return (size) - (coord % size) - 1;
        } else {
            return coord % size;
        }
    }

    T get(int i);

    void set(int i, T t);

    void iset(int i, int v);

    int getWidth();

    int getHeight();

    void writeCache(DataOutputStream dos) throws IOException;

    default void set(int x, int y, T v) {
        set((zigZag(y, getHeight()) * getWidth()) + zigZag(x, getWidth()), v);
    }

    default T get(int x, int y) {
        try {
            return get((zigZag(y, getHeight()) * getWidth()) + zigZag(x, getWidth()));
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    default void iset(int x, int y, int v) {
        iset((zigZag(y, getHeight()) * getWidth()) + zigZag(x, getWidth()), v);
    }
}
