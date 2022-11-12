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

package com.volmit.iris.util.noise;

import com.volmit.iris.util.hunk.bits.Writable;
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.MatterSlice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class CachedNoiseMap implements Writable<Integer> {
    private final Matter noise;
    private final MatterSlice<Integer> slice;

    public CachedNoiseMap(int size, NoiseGenerator cng) {
        noise = new IrisMatter(size, size, 1);
        slice = noise.slice(Integer.class);

        for (int i = 0; i < slice.getWidth(); i++) {
            for (int j = 0; j < slice.getHeight(); j++) {
                set(i, j, cng.noise(i, j));
            }
        }
    }

    public CachedNoiseMap(File file) throws IOException, ClassNotFoundException {
        noise = Matter.read(file);
        slice = noise.slice(Integer.class);
    }

    void write(File file) throws IOException {
        noise.write(file);
    }

    void set(int x, int y, double value) {
        slice.set(x % slice.getWidth(), y % slice.getHeight(), 0, Float.floatToIntBits((float) value));
    }

    double get(int x, int y) {
        Integer i = slice.get(x % slice.getWidth(), y % slice.getHeight(), 0);

        if (i == null) {
            return 0;
        }

        return Float.intBitsToFloat(i);
    }

    @Override
    public Integer readNodeData(DataInputStream din) throws IOException {
        return din.readInt();
    }

    @Override
    public void writeNodeData(DataOutputStream dos, Integer integer) throws IOException {
        dos.writeInt(integer);
    }
}
