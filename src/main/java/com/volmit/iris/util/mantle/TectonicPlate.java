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

import com.volmit.iris.Iris;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;

import java.io.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Tectonic Plates are essentially representations of regions in minecraft.
 * Tectonic Plates are fully atomic & thread safe
 */
public class TectonicPlate {
    private final int sectionHeight;
    private final AtomicReferenceArray<MantleChunk> chunks;

    /**
     * Create a new tectonic plate
     *
     * @param worldHeight the height of the world
     */
    public TectonicPlate(int worldHeight) {
        this.sectionHeight = worldHeight >> 4;
        this.chunks = new AtomicReferenceArray<>(1024);
    }

    /**
     * Load a tectonic plate from a data stream
     *
     * @param worldHeight the height of the world
     * @param din         the data input
     * @throws IOException            shit happens yo
     * @throws ClassNotFoundException real shit bro
     */
    public TectonicPlate(int worldHeight, DataInputStream din) throws IOException, ClassNotFoundException {
        this(worldHeight);

        for (int i = 0; i < chunks.length(); i++) {
            if (din.readBoolean()) {
                chunks.set(i, new MantleChunk(sectionHeight, din));
            }
        }
    }

    /**
     * Check if a chunk exists in this plate or not (same as get(x, z) != null)
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return true if the chunk exists
     */
    @ChunkCoordinates
    public boolean exists(int x, int z) {
        return get(x, z) != null;
    }

    /**
     * Get a chunk at the given coordinates or null if it doesnt exist
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return the chunk or null if it doesnt exist
     */
    @ChunkCoordinates
    public MantleChunk get(int x, int z) {
        return chunks.get(index(x, z));
    }

    /**
     * Clear all chunks from this tectonic plate
     */
    public void clear() {
        for (int i = 0; i < chunks.length(); i++) {
            chunks.set(i, null);
        }
    }

    /**
     * Delete a chunk from this tectonic plate
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     */
    @ChunkCoordinates
    public void delete(int x, int z) {
        chunks.set(index(x, z), null);
    }

    /**
     * Get a tectonic plate, or create one and insert it & return it if it diddnt exist
     *
     * @param x the chunk relative x (0-31)
     * @param z the chunk relative z (0-31)
     * @return the chunk (read or created & inserted)
     */
    @ChunkCoordinates
    public MantleChunk getOrCreate(int x, int z) {
        MantleChunk chunk = get(x, z);

        if (chunk == null) {
            chunk = new MantleChunk(sectionHeight);
            chunks.set(index(x, z), chunk);
        }

        return chunk;
    }

    @ChunkCoordinates
    private int index(int x, int z) {
        return (x & 0x1F) + (z & 0x1F) * 32;
    }

    /**
     * Write this tectonic plate to file
     *
     * @param file the file to write it to
     * @throws IOException shit happens
     */
    public void write(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        DataOutputStream dos = new DataOutputStream(fos);
        write(dos);
        dos.close();
        Iris.debug("Saved Tectonic Plate " + C.DARK_GREEN + file.getName().split("\\Q.\\E")[0]);
    }

    /**
     * Write this tectonic plate to a data stream
     *
     * @param dos the data output
     * @throws IOException shit happens
     */
    public void write(DataOutputStream dos) throws IOException {
        for (int i = 0; i < chunks.length(); i++) {
            MantleChunk chunk = chunks.get(i);
            dos.writeBoolean(chunk != null);

            if (chunk != null) {
                chunk.write(dos);
            }
        }
    }
}
