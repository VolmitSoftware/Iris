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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ByteCache implements ArrayCache<Integer> {
    @Getter
    private final int width;
    @Getter
    private final int height;
    private final byte[] cache;

    public ByteCache(int width, int height) {
        this.width = width;
        this.height = height;
        cache = new byte[width * height];
    }

    public void set(int i, Integer v) {
        cache[i] = v.byteValue();
    }

    public Integer get(int i) {
        return (int) cache[i];
    }

    @Override
    public void writeCache(DataOutputStream dos) throws IOException {
        dos.writeInt(width);
        dos.writeInt(height);

        for (int i = 0; i < width * height; i++) {
            dos.writeByte(get(i));
        }
    }

    @Override
    public Integer readNodeData(DataInputStream din) throws IOException {
        return (int) din.readByte();
    }

    @Override
    public void writeNodeData(DataOutputStream dos, Integer integer) throws IOException {
        dos.writeByte(integer);
    }

    @Override
    public void iset(int i, int v) {
        set(i, v);
    }
}
