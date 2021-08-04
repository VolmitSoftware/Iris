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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.util.data.Varint;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface MatterSlice<T> extends Hunk<T>
{
    Class<T> getType();

    void writeNode(T b, DataOutputStream dos) throws IOException;

    T readNode(DataInputStream din) throws IOException;

    default void write(DataOutputStream dos) throws IOException
    {
        int w = getWidth();
        int h = getHeight();
        MatterHunk<?> mapped = (MatterHunk<?>) this;
        dos.writeUTF(getType().getCanonicalName());
        Varint.writeUnsignedVarInt(mapped.getCount(), dos);
        iterateSyncIO((x,y,z,b) -> {
            Varint.writeUnsignedVarInt((z * w * h) + (y * w) + x, dos);
            writeNode(b, dos);
        });
    }

    default void read(DataInputStream din) throws IOException
    {
        int nodes = Varint.readUnsignedVarInt(din);
        int w = getWidth();
        int h = getHeight();
        int d = getDepth();
        int[] pos;

        while(nodes-- > 0)
        {
            pos = Cache.to3D(Varint.readUnsignedVarInt(din), w, h);
            setRaw(pos[0], pos[1], pos[2], readNode(din));
        }
    }
}
