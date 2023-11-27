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

package com.volmit.iris.util.nbt.mca;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.scheduling.J;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;

@SuppressWarnings("ALL")
public class MCAFile {

    /**
     * The default chunk data version used when no custom version is supplied.
     */
    public static final int DEFAULT_DATA_VERSION = 1628;

    private final int regionX;
    private final int regionZ;
    private AtomicReferenceArray<Chunk> chunks;
    private ConcurrentLinkedQueue<Runnable> afterSave;

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
        this.regionX = regionX;
        this.regionZ = regionZ;
        afterSave = new ConcurrentLinkedQueue<>();
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
        return (chunkX & 0x1F) + (chunkZ & 0x1F) * 32;
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
        chunks = new AtomicReferenceArray<>(1024);
        for (int i = 0; i < 1024; i++) {
            raf.seek(i * 4);
            int offset = raf.read() << 16;
            offset |= (raf.read() & 0xFF) << 8;
            offset |= raf.read() & 0xFF;
            if (raf.readByte() == 0) {
                continue;
            }
            raf.seek(4096 + i * 4);
            int timestamp = raf.readInt();
            Chunk chunk = new Chunk(timestamp);
            raf.seek(4096L * offset + 4); //+4: skip data size
            chunk.deserialize(raf, loadFlags);
            chunks.set(i, chunk);
        }
    }

    public KList<Position2> samplePositions(RandomAccessFile raf) throws IOException {
        KList<Position2> p2 = new KList<>();
        chunks = new AtomicReferenceArray<>(1024);
        int x = 0;
        int z = 0;
        for (int i = 0; i < 1024; i++) {
            x++;
            z++;

            raf.seek(i * 4);
            int offset = raf.read() << 16;
            offset |= (raf.read() & 0xFF) << 8;
            offset |= raf.read() & 0xFF;
            if (raf.readByte() == 0) {
                continue;
            }
            p2.add(new Position2(x & 31, (z / 31) & 31));
        }
        return p2;
    }

    public AtomicReferenceArray<Chunk> getChunks() {
        return chunks;
    }

    /**
     * Calls {@link MCAFile#serialize(RandomAccessFile, boolean)} without updating any timestamps.
     *
     * @param raf The {@code RandomAccessFile} to writeNodeData to.
     * @return The amount of chunks written to the file.
     * @throws IOException If something went wrong during serialization.
     * @see MCAFile#serialize(RandomAccessFile, boolean)
     */
    public int serialize(RandomAccessFile raf) throws IOException {
        return serialize(raf, false);
    }

    /**
     * Serializes this object to an .mca file.
     * This method does not perform any cleanups on the data.
     *
     * @param raf              The {@code RandomAccessFile} to writeNodeData to.
     * @param changeLastUpdate Whether it should update all timestamps that show
     *                         when this file was last updated.
     * @return The amount of chunks written to the file.
     * @throws IOException If something went wrong during serialization.
     */
    public int serialize(RandomAccessFile raf, boolean changeLastUpdate) throws IOException {
        int globalOffset = 2;
        int lastWritten = 0;
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        int chunksWritten = 0;
        int chunkXOffset = MCAUtil.regionToChunk(regionX);
        int chunkZOffset = MCAUtil.regionToChunk(regionZ);

        if (chunks == null) {
            return 0;
        }

        for (int cx = 0; cx < 32; cx++) {
            for (int cz = 0; cz < 32; cz++) {
                int index = getChunkIndex(cx, cz);
                Chunk chunk = chunks.get(index);
                if (chunk == null) {
                    continue;
                }
                raf.seek(4096L * globalOffset);
                lastWritten = chunk.serialize(raf, chunkXOffset + cx, chunkZOffset + cz);

                if (lastWritten == 0) {
                    continue;
                }

                chunksWritten++;

                int sectors = (lastWritten >> 12) + (lastWritten % 4096 == 0 ? 0 : 1);

                raf.seek(index * 4L);
                raf.writeByte(globalOffset >>> 16);
                raf.writeByte(globalOffset >> 8 & 0xFF);
                raf.writeByte(globalOffset & 0xFF);
                raf.writeByte(sectors);

                // writeNodeData timestamp
                raf.seek(index * 4L + 4096);
                raf.writeInt(changeLastUpdate ? timestamp : chunk.getLastMCAUpdate());

                globalOffset += sectors;
            }
        }

        // padding
        if (lastWritten % 4096 != 0) {
            raf.seek(globalOffset * 4096L - 1);
            raf.write(0);
        }

        J.a(() -> {
            afterSave.forEach(i -> i.run());
        }, 20);

        return chunksWritten;
    }

    /**
     * Set a specific Chunk at a specific index. The index must be in range of 0 - 1023.
     *
     * @param index The index of the Chunk.
     * @param chunk The Chunk to be set.
     * @throws IndexOutOfBoundsException If index is not in the range.
     */
    public void setChunk(int index, Chunk chunk) {
        checkIndex(index);
        if (chunks == null) {
            chunks = new AtomicReferenceArray<>(1024);
        }
        chunks.set(index, chunk);
    }

    /**
     * Set a specific Chunk at a specific chunk location.
     * The x- and z-value can be absolute chunk coordinates or they can be relative to the region origin.
     *
     * @param chunkX The x-coordinate of the Chunk.
     * @param chunkZ The z-coordinate of the Chunk.
     * @param chunk  The chunk to be set.
     */
    public void setChunk(int chunkX, int chunkZ, Chunk chunk) {
        setChunk(getChunkIndex(chunkX, chunkZ), chunk);
    }

    /**
     * Returns the chunk data of a chunk at a specific index in this file.
     *
     * @param index The index of the chunk in this file.
     * @return The chunk data.
     */
    public Chunk getChunk(int index) {
        checkIndex(index);
        if (chunks == null) {
            return null;
        }
        return chunks.get(index);
    }

    /**
     * Returns the chunk data of a chunk in this file.
     *
     * @param chunkX The x-coordinate of the chunk.
     * @param chunkZ The z-coordinate of the chunk.
     * @return The chunk data.
     */
    public Chunk getChunk(int chunkX, int chunkZ) {
        return getChunk(getChunkIndex(chunkX, chunkZ));
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return getChunk(chunkX, chunkZ) != null;
    }

    private int checkIndex(int index) {
        if (index < 0 || index > 1023) {
            throw new IndexOutOfBoundsException();
        }
        return index;
    }

    private Chunk createChunkIfMissing(int blockX, int blockZ) {
        int chunkX = MCAUtil.blockToChunk(blockX), chunkZ = MCAUtil.blockToChunk(blockZ);
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            chunk = Chunk.newChunk();
            setChunk(getChunkIndex(chunkX, chunkZ), chunk);
        }
        return chunk;
    }

    public void setBiomeAt(int blockX, int blockY, int blockZ, int biomeID) {
        createChunkIfMissing(blockX, blockZ).setBiomeAt(blockX, blockY, blockZ, biomeID);
    }

    /**
     * Fetches the biome id at a specific block.
     *
     * @param blockX The x-coordinate of the block.
     * @param blockY The y-coordinate of the block.
     * @param blockZ The z-coordinate of the block.
     * @return The biome id if the chunk exists and the chunk has biomes, otherwise -1.
     */
    public int getBiomeAt(int blockX, int blockY, int blockZ) {
        int chunkX = MCAUtil.blockToChunk(blockX), chunkZ = MCAUtil.blockToChunk(blockZ);
        Chunk chunk = getChunk(getChunkIndex(chunkX, chunkZ));
        if (chunk == null) {
            return -1;
        }
        return chunk.getBiomeAt(blockX, blockY, blockZ);
    }

    /**
     * Set a block state at a specific block location.
     * The block coordinates can be absolute coordinates or they can be relative to the region.
     *
     * @param blockX  The x-coordinate of the block.
     * @param blockY  The y-coordinate of the block.
     * @param blockZ  The z-coordinate of the block.
     * @param state   The block state to be set.
     * @param cleanup Whether the Palette and the BLockStates should be recalculated after adding the block state.
     */
    public void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {
        createChunkIfMissing(blockX, blockZ).setBlockStateAt(blockX, blockY, blockZ, state, cleanup);
    }

    /**
     * Fetches a block state at a specific block location.
     * The block coordinates can be absolute coordinates or they can be relative to the region.
     *
     * @param blockX The x-coordinate of the block.
     * @param blockY The y-coordinate of the block.
     * @param blockZ The z-coordinate of the block.
     * @return The block state or <code>null</code> if the chunk or the section do not exist.
     */
    public CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ) {
        int chunkX = MCAUtil.blockToChunk(blockX), chunkZ = MCAUtil.blockToChunk(blockZ);
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockStateAt(blockX, blockY, blockZ);
    }

    public void afterSave(Runnable o) {
        afterSave.add(o);
    }
}
