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

import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Represents a mantle chunk. Mantle chunks contain sections of matter (see matter api)
 * Mantle Chunks are fully atomic & thread safe
 */
public class MantleChunk {
    private final KSet<String> flags;
    private final AtomicReferenceArray<Matter> sections;

    /**
     * Create a mantle chunk
     *
     * @param sectionHeight the height of the world in sections (blocks >> 4)
     */
    @ChunkCoordinates
    public MantleChunk(int sectionHeight) {
        sections = new AtomicReferenceArray<>(sectionHeight);
        flags = new KSet<>();
    }

    /**
     * Load a mantle chunk from a data stream
     *
     * @param sectionHeight the height of the world in sections (blocks >> 4)
     * @param din           the data input
     * @throws IOException            shit happens
     * @throws ClassNotFoundException shit happens
     */
    public MantleChunk(int sectionHeight, DataInputStream din) throws IOException, ClassNotFoundException {
        this(sectionHeight);
        int s = din.readByte();
        int f = din.readByte();

        for (int i = 0; i < f; i++) {
            flags.add(din.readUTF());
        }

        for (int i = 0; i < s; i++) {
            if (din.readBoolean()) {
                sections.set(i, Matter.read(din));
            }
        }
    }

    public void flag(String s, boolean f) {
        if (f) {
            flags.add(s);
        } else {
            flags.remove(s);
        }
    }

    public boolean isFlagged(String s) {
        return flags.contains(s);
    }

    /**
     * Check if a section exists (same as get(section) != null)
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return true if it exists
     */
    @ChunkCoordinates
    public boolean exists(int section) {
        return get(section) != null;
    }

    /**
     * Get thje matter at the given section or null if it doesnt exist
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return the matter or null if it doesnt exist
     */
    @ChunkCoordinates
    public Matter get(int section) {
        return sections.get(section);
    }

    /**
     * Clear all matter from this chunk
     */
    public void clear() {
        for (int i = 0; i < sections.length(); i++) {
            delete(i);
        }
    }

    /**
     * Delete the matter from the given section
     *
     * @param section the section (0 - (worldHeight >> 4))
     */
    @ChunkCoordinates
    public void delete(int section) {
        sections.set(section, null);
    }

    /**
     * Get or create a new matter section at the given section
     *
     * @param section the section (0 - (worldHeight >> 4))
     * @return the matter
     */
    @ChunkCoordinates
    public Matter getOrCreate(int section) {
        Matter matter = get(section);

        if (matter == null) {
            matter = new IrisMatter(16, 16, 16);
            sections.set(section, matter);
        }

        return matter;
    }

    /**
     * Write this chunk to a data stream
     *
     * @param dos the stream
     * @throws IOException shit happens
     */
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(sections.length());
        dos.writeByte(flags.size());

        for (String i : flags) {
            dos.writeUTF(i);
        }

        for (int i = 0; i < sections.length(); i++) {
            if (exists(i)) {
                dos.writeBoolean(true);
                Matter matter = get(i);
                matter.writeDos(dos);
            } else {
                dos.writeBoolean(false);
            }
        }
    }
}
