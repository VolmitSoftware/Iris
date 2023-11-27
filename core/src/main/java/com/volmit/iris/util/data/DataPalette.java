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

package com.volmit.iris.util.data;

import com.volmit.iris.util.collection.KList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DataPalette<T> {
    private final KList<T> palette;

    public DataPalette() {
        this(new KList<>(16));
    }

    public DataPalette(KList<T> palette) {
        this.palette = palette;
    }

    public static <T> DataPalette<T> getPalette(IOAdapter<T> adapter, DataInputStream din) throws IOException {
        KList<T> palette = new KList<>();
        int s = din.readShort() - Short.MIN_VALUE;

        for (int i = 0; i < s; i++) {
            palette.add(adapter.read(din));
        }

        return new DataPalette<>(palette);
    }

    public KList<T> getPalette() {
        return palette;
    }

    public T get(int index) {
        synchronized (palette) {
            if (!palette.hasIndex(index)) {
                return null;
            }

            return palette.get(index);
        }
    }

    public int getIndex(T t) {
        int v = 0;

        synchronized (palette) {
            v = palette.indexOf(t);

            if (v == -1) {
                v = palette.size();
                palette.add(t);
            }
        }

        return v;
    }

    public void write(IOAdapter<T> adapter, DataOutputStream dos) throws IOException {
        synchronized (palette) {
            dos.writeShort(getPalette().size() + Short.MIN_VALUE);

            for (T t : palette) {
                adapter.write(t, dos);
            }
        }
    }
}
