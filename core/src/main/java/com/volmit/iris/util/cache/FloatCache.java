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

import lombok.Getter;

import java.io.*;

public class FloatCache implements ArrayCache<Float> {
    @Getter
    private final int width;
    @Getter
    private final int height;
    private final float[] cache;


    public FloatCache(File file) throws IOException {
        this(new DataInputStream(new FileInputStream(file)));
    }

    public FloatCache(DataInputStream din) throws IOException {
        this(din.readInt(), din.readInt());
        for (int i = 0; i < width * height; i++) {
            cache[i] = din.readFloat();
        }
        din.close();
    }

    public FloatCache(int width, int height) {
        this.width = width;
        this.height = height;
        cache = new float[width * height];
    }

    public void set(int i, Float v) {
        cache[i] = v;
    }

    public Float get(int i) {
        return cache[i];
    }

    @Override
    public void writeCache(DataOutputStream dos) throws IOException {
        dos.writeInt(width);
        dos.writeInt(height);

        for (int i = 0; i < width * height; i++) {
            dos.writeFloat(get(i));
        }
    }

    @Override
    public Float readNodeData(DataInputStream din) throws IOException {
        return din.readFloat();
    }

    @Override
    public void writeNodeData(DataOutputStream dos, Float integer) throws IOException {
        dos.writeFloat(integer);
    }

    @Override
    public void iset(int i, int v) {
        set(i, (float) v);
    }
}
