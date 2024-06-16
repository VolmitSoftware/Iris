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

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.tag.ByteArrayTag;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;


public class Section {
    private CompoundTag data;
    private MCAPaletteAccess palette;
    private byte[] blockLight;
    private byte[] skyLight;

    public Section(CompoundTag sectionRoot, int dataVersion) {
        this(sectionRoot, dataVersion, LoadFlags.ALL_DATA);
    }

    public Section(CompoundTag sectionRoot, int dataVersion, long loadFlags) {
        data = sectionRoot;
        ListTag<?> rawPalette = sectionRoot.getListTag("Palette");
        if (rawPalette == null) {
            return;
        }
        palette = INMS.get().createPalette();
        palette.readFromSection(sectionRoot);
        ByteArrayTag blockLight = sectionRoot.getByteArrayTag("BlockLight");
        ByteArrayTag skyLight = sectionRoot.getByteArrayTag("SkyLight");
        this.blockLight = blockLight != null ? blockLight.getValue() : null;
        this.skyLight = skyLight != null ? skyLight.getValue() : null;
    }

    Section() {
    }

    /**
     * Creates an empty Section with base values.
     *
     * @return An empty Section
     */
    public static Section newSection() {
        Section s = new Section();
        s.data = new CompoundTag();
        s.palette = INMS.get().createPalette();
        return s;
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
        synchronized (palette) {
            return palette.getBlock(blockX & 15, blockY & 15, blockZ & 15);
        }
    }

    /**
     * Attempts to add a block state for a specific block location in this Section.
     *
     * @param blockX The x-coordinate of the block in this Section
     * @param blockY The y-coordinate of the block in this Section
     * @param blockZ The z-coordinate of the block in this Section
     * @param state  The block state to be set
     */
    public synchronized void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {
        synchronized (palette) {
            palette.setBlock(blockX & 15, blockY & 15, blockZ & 15, state);
        }
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
    public synchronized byte[] getBlockLight() {
        return blockLight;
    }

    /**
     * Sets the block light array for this section.
     *
     * @param blockLight The block light array
     * @throws IllegalArgumentException When the length of the array is not 2048
     */
    public synchronized void setBlockLight(byte[] blockLight) {
        if (blockLight != null && blockLight.length != 2048) {
            throw new IllegalArgumentException("BlockLight array must have a length of 2048");
        }
        this.blockLight = blockLight;
    }

    /**
     * @return The sky light values of this Section
     */
    public synchronized byte[] getSkyLight() {
        return skyLight;
    }

    /**
     * Sets the sky light values of this section.
     *
     * @param skyLight The custom sky light values
     * @throws IllegalArgumentException If the length of the array is not 2048
     */
    public synchronized void setSkyLight(byte[] skyLight) {
        if (skyLight != null && skyLight.length != 2048) {
            throw new IllegalArgumentException("SkyLight array must have a length of 2048");
        }
        this.skyLight = skyLight;
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
            synchronized (palette) {
                palette.writeToSection(data);
            }
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
