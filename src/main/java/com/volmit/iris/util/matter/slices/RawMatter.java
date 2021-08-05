/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.matter.MatterHunk;
import com.volmit.iris.util.matter.MatterSlice;
import lombok.Getter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class RawMatter<T> extends MatterHunk<T> implements MatterSlice<T> {
    @Getter
    private final Class<T> type;

    public RawMatter(int width, int height, int depth, Class<T> type) {
        super(width, height, depth);
        this.type = type;
    }

    @Override
    public void setRaw(int x, int y, int z, T t) {

    }

    @Override
    public T getRaw(int x, int y, int z) {
        return null;
    }

    @Override
    public abstract void writeNode(T b, DataOutputStream dos) throws IOException;

    @Override
    public abstract T readNode(DataInputStream din) throws IOException;
}
