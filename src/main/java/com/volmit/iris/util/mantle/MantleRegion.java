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

package com.volmit.iris.util.mantle;

import com.volmit.iris.util.documentation.ChunkCoordinates;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MantleRegion {
    private final int sectionHeight;
    private final AtomicReferenceArray<MantleChunk> chunks;

    public MantleRegion(int worldHeight)
    {
        this.sectionHeight = worldHeight >> 4;
        this.chunks = new AtomicReferenceArray<>(1024);
    }

    public MantleRegion(int worldHeight, DataInputStream din) throws IOException, ClassNotFoundException {
        this(worldHeight);

        for(int i = 0; i < chunks.length(); i++)
        {
            if(din.readBoolean())
            {
                chunks.set(i, new MantleChunk(sectionHeight, din));
            }
        }
    }

    @ChunkCoordinates
    public boolean exists(int x, int z)
    {
        return get(x, z) != null;
    }

    @ChunkCoordinates
    public MantleChunk get(int x, int z)
    {
        return chunks.get(index(x, z));
    }

    public void clear()
    {
        for(int i = 0; i < chunks.length(); i++)
        {
            chunks.set(i, null);
        }
    }

    @ChunkCoordinates
    public void delete(int x, int z)
    {
        chunks.set(index(x, z), null);
    }

    @ChunkCoordinates
    public MantleChunk getOrCreate(int x, int z)
    {
        MantleChunk chunk = get(x, z);

        if(chunk == null)
        {
            chunk = new MantleChunk(sectionHeight);
            chunks.set(index(x, z), chunk);
        }

        return chunk;
    }

    @ChunkCoordinates
    private int index(int x, int z) {
        return (x & 0x1F) + (z & 0x1F) * 32;
    }

    public void write(DataOutputStream dos) throws IOException {
        for(int i = 0; i < chunks.length(); i++)
        {
            MantleChunk chunk = chunks.get(i);
            dos.writeBoolean(chunk != null);

            if(chunk != null)
            {
                chunk.write(dos);
            }
        }
    }
}
