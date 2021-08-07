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

import com.volmit.iris.engine.data.chunk.MCATerrainChunk;
import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.nbt.mca.Section;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MantleChunk {
    private final AtomicReferenceArray<MantleMatter> sections;

    @ChunkCoordinates
    public MantleChunk(int sectionHeight)
    {
        sections = new AtomicReferenceArray<>(sectionHeight);
    }

    public MantleChunk(int sectionHeight, DataInputStream din) throws IOException, ClassNotFoundException {
        this(sectionHeight);
        int s = Varint.readUnsignedVarInt(din);

        for(int i = 0; i < s; i++)
        {
            if(din.readBoolean())
            {
                sections.set(i, MantleMatter.read(din));
            }
        }
    }

    @ChunkCoordinates
    public boolean exists(int section)
    {
        return get(section) != null;
    }

    @ChunkCoordinates
    public MantleMatter get(int section)
    {
        return sections.get(section);
    }

    public void clear()
    {
        for(int i = 0; i < sections.length(); i++)
        {
            delete(i);
        }
    }

    @ChunkCoordinates
    public void delete(int section)
    {
        sections.set(section, null);
    }

    @ChunkCoordinates
    public MantleMatter getOrCreate(int section)
    {
        MantleMatter matter = get(section);

        if(matter == null)
        {
            matter = new MantleMatter(16, 16, 16);
            sections.set(section, matter);
        }

        return matter;
    }

    public void write(DataOutputStream dos) throws IOException {
        Varint.writeUnsignedVarInt(sections.length(), dos);

        for(int i = 0; i < sections.length(); i++)
        {
            if(exists(i))
            {
                dos.writeBoolean(true);
                MantleMatter matter = get(i);
                matter.writeDos(dos);
            }

            else
            {
                dos.writeBoolean(false);
            }
        }
    }
}
