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

package art.arcane.iris.util.nbt.mca;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.mca.MCAFileSupport;
import art.arcane.volmlib.util.nbt.mca.LoadFlags;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.iris.util.scheduling.J;

import java.io.IOException;
import java.io.RandomAccessFile;

@SuppressWarnings("ALL")
public class MCAFile extends MCAFileSupport<Chunk> {

    /**
     * The default chunk data version used when no custom version is supplied.
     */
    public static final int DEFAULT_DATA_VERSION = 1628;

    /**
     * MCAFile represents a world save file used by Minecraft to store world
     * data on the hard drive.
     * This constructor needs the x- and z-coordinates of the stored region,
     * which can usually be taken from the file name {@code r.x.z.mca}
     *
     * @param regionX The x-coordinate of this region.
     * @param regionZ The z-coordinate of this region.
     */
    public MCAFile(int regionX, int regionZ) {
        super(regionX, regionZ, Chunk::newChunk, task -> J.a(task, 20));
    }

    /**
     * Calculates the index of a chunk from its x- and z-coordinates in this region.
     * This works with absolute and relative coordinates.
     *
     * @param chunkX The x-coordinate of the chunk.
     * @param chunkZ The z-coordinate of the chunk.
     * @return The index of this chunk.
     */
    public static int getChunkIndex(int chunkX, int chunkZ) {
        return MCAFileSupport.getChunkIndex(chunkX, chunkZ);
    }

    /**
     * Reads an .mca file from a {@code RandomAccessFile} into this object.
     * This method does not perform any cleanups on the data.
     *
     * @param raf The {@code RandomAccessFile} to read from.
     * @throws IOException If something went wrong during deserialization.
     */
    public void deserialize(RandomAccessFile raf) throws IOException {
        deserialize(raf, LoadFlags.ALL_DATA);
    }

    /**
     * Reads an .mca file from a {@code RandomAccessFile} into this object.
     * This method does not perform any cleanups on the data.
     *
     * @param raf       The {@code RandomAccessFile} to read from.
     * @param loadFlags A logical or of {@link LoadFlags} constants indicating what data should be loaded
     * @throws IOException If something went wrong during deserialization.
     */
    public void deserialize(RandomAccessFile raf, long loadFlags) throws IOException {
        super.deserialize(raf, loadFlags, Chunk::new);
    }

    public KList<Position2> samplePositions(RandomAccessFile raf) throws IOException {
        return super.samplePositions(raf, Position2::new);
    }
}
