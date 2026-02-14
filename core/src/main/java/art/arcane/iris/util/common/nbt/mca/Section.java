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

import art.arcane.iris.core.nms.INMS;
import art.arcane.volmlib.util.nbt.mca.LoadFlags;
import art.arcane.volmlib.util.nbt.mca.MCASectionLike;
import art.arcane.volmlib.util.nbt.mca.MCASectionSupport;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
public class Section implements MCASectionLike {
    private final MCASectionSupport support;

    public Section(CompoundTag sectionRoot, int dataVersion) {
        this(sectionRoot, dataVersion, LoadFlags.ALL_DATA);
    }

    public Section(CompoundTag sectionRoot, int dataVersion, long loadFlags) {
        support = new MCASectionSupport(sectionRoot, loadFlags, () -> INMS.get().createPalette());
    }

    private Section(MCASectionSupport support) {
        this.support = support;
    }

    /**
     * Creates an empty Section with base values.
     *
     * @return An empty Section
     */
    public static Section newSection() {
        return new Section(MCASectionSupport.createNew(() -> INMS.get().createPalette()));
    }

    /**
     * Checks whether the data of this Section is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return support.isEmpty();
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
        return support.getBlockStateAt(blockX, blockY, blockZ);
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
        support.setBlockStateAt(blockX, blockY, blockZ, state, cleanup);
    }

    /**
     * This method recalculates the palette and its indices.
     * This should only be used moderately to avoid unnecessary recalculation of the palette indices.
     * Recalculating the Palette should only be executed once right before saving the Section to file.
     */
    public void cleanupPaletteAndBlockStates() {
        support.cleanupPaletteAndBlockStates();
    }

    /**
     * @return The block light array of this Section
     */
    public synchronized byte[] getBlockLight() {
        return support.getBlockLight();
    }

    /**
     * Sets the block light array for this section.
     *
     * @param blockLight The block light array
     * @throws IllegalArgumentException When the length of the array is not 2048
     */
    public synchronized void setBlockLight(byte[] blockLight) {
        support.setBlockLight(blockLight);
    }

    /**
     * @return The sky light values of this Section
     */
    public synchronized byte[] getSkyLight() {
        return support.getSkyLight();
    }

    /**
     * Sets the sky light values of this section.
     *
     * @param skyLight The custom sky light values
     * @throws IllegalArgumentException If the length of the array is not 2048
     */
    public synchronized void setSkyLight(byte[] skyLight) {
        support.setSkyLight(skyLight);
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
        return support.updateHandle(y);
    }
}
