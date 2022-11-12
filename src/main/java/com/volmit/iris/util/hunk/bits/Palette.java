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

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.function.Consumer2;
import com.volmit.iris.util.function.Consumer2IO;

import java.io.DataInputStream;
import java.io.IOException;

public interface Palette<T> {
    T get(int id);

    int add(T t);

    int id(T t);

    int size();

    default int bits() {
        return DataContainer.bits(size() + 1);
    }

    void iterate(Consumer2<T, Integer> c);

    default void iterateIO(Consumer2IO<T, Integer> c) {
        iterate((a, b) -> {
            try {
                c.accept(a, b);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    default Palette<T> from(int size, Writable<T> writable, DataInputStream in) throws IOException {
        for (int i = 0; i < size; i++) {
            add(writable.readNodeData(in));
        }

        return this;
    }

    default Palette<T> from(Palette<T> oldPalette) {
        oldPalette.iterate((k, v) -> add(k));
        return this;
    }

    default KList<T> list() {
        KList<T> t = new KList<>();
        iterate((tx, __) -> t.add(tx));
        return t;
    }
}
