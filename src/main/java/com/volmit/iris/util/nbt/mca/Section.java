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

package com.volmit.iris.util.nbt.mca;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.nbt.mca.palettes.DataPaletteBlock;
import com.volmit.iris.util.nbt.tag.ByteArrayTag;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import com.volmit.iris.util.nbt.tag.LongArrayTag;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.world.level.chunk.Chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;

public class Section {
    private CompoundTag data;
    private DataPaletteBlock<CompoundTag> palette;
    private byte[] blockLight;
    private byte[] skyLight;
    private int dataVersion;

    public Section(CompoundTag sectionRoot, int dataVersion) {
        this(sectionRoot, dataVersion, LoadFlags.ALL_DATA);
    }

    public Section(CompoundTag sectionRoot, int dataVersion, long loadFlags) {
        data = sectionRoot;
        this.dataVersion = dataVersion;
        ListTag<?> rawPalette = sectionRoot.getListTag("Palette");
        if (rawPalette == null) {
            return;
        }
        palette = new DataPaletteBlock<>();
        LongArrayTag blockStates = sectionRoot.getLongArrayTag("BlockStates");
        palette.a((ListTag<CompoundTag>) rawPalette, blockStates.getValue());
        ByteArrayTag blockLight = sectionRoot.getByteArrayTag("BlockLight");
        ByteArrayTag skyLight = sectionRoot.getByteArrayTag("SkyLight");
        this.blockLight = blockLight != null ? blockLight.getValue() : null;
        this.skyLight = skyLight != null ? skyLight.getValue() : null;
    }

    Section() {
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static class PaletteIndex {

        final CompoundTag data;
        final int index;

        PaletteIndex(CompoundTag data, int index) {
            this.data = data;
            this.index = index;
        }
    }

    /**
     * Checks whether the data of this Section is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return data == null;
    }

    /**
     * Fetches a block state based on a block location from this section.
     * The coordinates represent the location of the block inside of this Section.
     *
     * @param blockX The x-coordinate of the block in this Section
     * @param blockY The y-coordinate of the block in this Section
     * @param blockZ The z-coordinate of the block in this Section
     * @return The block state data of this block.
     */
    public synchronized CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ) {
        return palette.a(blockX, blockY, blockZ);
    }

    /**
     * Attempts to add a block state for a specific block location in this Section.
     *
     * @param blockX  The x-coordinate of the block in this Section
     * @param blockY  The y-coordinate of the block in this Section
     * @param blockZ  The z-coordinate of the block in this Section
     * @param state   The block state to be set
     */
    public synchronized void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {

        if(cleanup)
        {
            palette.setBlock(blockX, blockY, blockZ, state);
        }

        else
        {
            palette.b(blockX, blockY, blockZ, state);
        }
    }

    /**
     * Sets the index of the block data in the BlockStates. Does not adjust the size of the BlockStates array.
     *
     * @param blockIndex   The index of the block in this section, ranging from 0-4095.
     * @param paletteIndex The block state to be set (index of block data in the palette).
     * @param blockStates  The block states to be updated.
     */
    public synchronized void setPaletteIndex(int blockIndex, int paletteIndex, AtomicLongArray blockStates) {
        int bits = blockStates.length() >> 6;

        if (dataVersion < 2527) {
            double blockStatesIndex = blockIndex / (4096D / blockStates.length());
            int longIndex = (int) blockStatesIndex;
            int startBit = (int) ((blockStatesIndex - Math.floor(longIndex)) * 64D);
            if (startBit + bits > 64) {
                blockStates.set(longIndex, updateBits(blockStates.get(longIndex), paletteIndex, startBit, 64));
                blockStates.set(longIndex + 1, updateBits(blockStates.get(longIndex + 1), paletteIndex, startBit - 64, startBit + bits - 64));
            } else {
                blockStates.set(longIndex, updateBits(blockStates.get(longIndex), paletteIndex, startBit, startBit + bits));
            }
        } else {
            int indicesPerLong = (int) (64D / bits);
            int blockStatesIndex = blockIndex / indicesPerLong;
            int startBit = (blockIndex % indicesPerLong) * bits;
            blockStates.set(blockStatesIndex, updateBits(blockStates.get(blockStatesIndex), paletteIndex, startBit, startBit + bits));
        }
    }

    int getBlockIndex(int blockX, int blockY, int blockZ) {
        return (blockY & 0xF) * 256 + (blockZ & 0xF) * 16 + (blockX & 0xF);
    }

    static long updateBits(long n, long m, int i, int j) {
        //replace i to j in n with j - i bits of m
        long mShifted = i > 0 ? (m & ((1L << j - i) - 1)) << i : (m & ((1L << j - i) - 1)) >>> -i;
        return ((n & ((j > 63 ? 0 : (~0L << j)) | (i < 0 ? 0 : ((1L << i) - 1L)))) | mShifted);
    }

    static long bitRange(long value, int from, int to) {
        int waste = 64 - to;
        return (value << waste) >>> (waste + from);
    }

    /**
     * This method recalculates the palette and its indices.
     * This should only be used moderately to avoid unnecessary recalculation of the palette indices.
     * Recalculating the Palette should only be executed once right before saving the Section to file.
     */
    public void cleanupPaletteAndBlockStates() {

    }

    /**
     * @return The block light array of this Section
     */
    public byte[] getBlockLight() {
        return blockLight;
    }

    /**
     * Sets the block light array for this section.
     *
     * @param blockLight The block light array
     * @throws IllegalArgumentException When the length of the array is not 2048
     */
    public void setBlockLight(byte[] blockLight) {
        if (blockLight != null && blockLight.length != 2048) {
            throw new IllegalArgumentException("BlockLight array must have a length of 2048");
        }
        this.blockLight = blockLight;
    }

    /**
     * @return The sky light values of this Section
     */
    public byte[] getSkyLight() {
        return skyLight;
    }

    /**
     * Sets the sky light values of this section.
     *
     * @param skyLight The custom sky light values
     * @throws IllegalArgumentException If the length of the array is not 2048
     */
    public void setSkyLight(byte[] skyLight) {
        if (skyLight != null && skyLight.length != 2048) {
            throw new IllegalArgumentException("SkyLight array must have a length of 2048");
        }
        this.skyLight = skyLight;
    }

    /**
     * Creates an empty Section with base values.
     *
     * @return An empty Section
     */
    public static Section newSection() {
        Section s = new Section();
        s.palette = new DataPaletteBlock<>();
        s.data = new CompoundTag();
        return s;
    }

    /**
     * Updates the raw CompoundTag that this Section is based on.
     * This must be called before saving a Section to disk if the Section was manually created
     * to set the Y of this Section.
     *
     * @param y The Y-value of this Section
     * @return A reference to the raw CompoundTag this Section is based on
     */
    public synchronized CompoundTag updateHandle(int y) {
        data.putByte("Y", (byte) y);
        if (palette != null) {
            data.put("Palette", palette.getK().getPalette());
            data.putLongArray("BlockStates", palette.getC().a());
        }
        if (blockLight != null) {
            data.putByteArray("BlockLight", blockLight);
        }
        if (skyLight != null) {
            data.putByteArray("SkyLight", skyLight);
        }
        return data;
    }
}
