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

package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

/** Writes the carved blocks of one chunk into the cavern slices of its mantle sections, keeping the slice of each section it has used. */
final class MantleCaveCellSink implements CaveCellSink {
    private static final int SECTION_HEIGHT_SHIFT = 4;
    private static final int SECTION_HEIGHT_MASK = (1 << SECTION_HEIGHT_SHIFT) - 1;

    private final MantleChunk<Matter> chunk;
    private final MatterSlice<MatterCavern>[] slices;

    @SuppressWarnings("unchecked")
    MantleCaveCellSink(MantleChunk<Matter> chunk, int worldHeight) {
        this.chunk = chunk;
        this.slices = new MatterSlice[PowerOfTwoCoordinates.ceilDivPow2(worldHeight, SECTION_HEIGHT_SHIFT)];
    }

    @Override
    public void set(int localX, int y, int localZ, MatterCavern cell) {
        sliceOf(y >> SECTION_HEIGHT_SHIFT).set(localX, y & SECTION_HEIGHT_MASK, localZ, cell);
    }

    private MatterSlice<MatterCavern> sliceOf(int section) {
        MatterSlice<MatterCavern> slice = slices[section];
        if (slice == null) {
            slice = chunk.getOrCreate(section).slice(MatterCavern.class);
            slices[section] = slice;
        }
        return slice;
    }
}
