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

package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

final class CaveFluidSupportPlan {
    private final IdentityHashMap<MatterCavern, FluidCandidateGroup> groups = new IdentityHashMap<>();

    void add(int localX, int y, int localZ, MatterCavern fluid, MatterCavern air) {
        FluidCandidateGroup group = groups.computeIfAbsent(fluid, key -> new FluidCandidateGroup(fluid, air));
        int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
        group.positions.set((y << 8) | columnIndex);
    }

    void resolve(MantleChunk<Matter> chunk) {
        if (chunk == null) {
            groups.clear();
            return;
        }

        for (Map.Entry<MatterCavern, FluidCandidateGroup> entry : groups.entrySet()) {
            FluidCandidateGroup group = entry.getValue();
            for (int position = group.positions.nextSetBit(0); position >= 0; position = group.positions.nextSetBit(position + 1)) {
                int y = position >>> 8;
                int columnIndex = position & 255;
                int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                int localZ = columnIndex & 15;
                MatterCavern current = getCavern(chunk, localX, y, localZ);
                if (current != group.fluid || hasCupSupport(chunk, localX, y, localZ)) {
                    continue;
                }

                Matter section = chunk.get(y >> 4);
                MatterSlice<MatterCavern> cavernSlice = section.getSlice(MatterCavern.class);
                cavernSlice.set(localX, y & 15, localZ, group.air);
            }
        }
        groups.clear();
    }

    private static boolean hasCupSupport(MantleChunk<Matter> chunk, int localX, int y, int localZ) {
        if (localX <= 0 || localX >= 15 || localZ <= 0 || localZ >= 15
                || y <= 1 || !isSolid(chunk, localX, y - 1, localZ)
                || !isSolid(chunk, localX, y - 2, localZ)) {
            return false;
        }

        int support = 0;
        if (isSolid(chunk, localX + 1, y, localZ)) {
            support++;
        }
        if (isSolid(chunk, localX - 1, y, localZ)) {
            support++;
        }
        if (isSolid(chunk, localX, y, localZ + 1)) {
            support++;
        }
        if (isSolid(chunk, localX, y, localZ - 1)) {
            support++;
        }
        if (isSolid(chunk, localX, y + 1, localZ)) {
            support++;
        }
        return support >= 4;
    }

    private static boolean isSolid(MantleChunk<Matter> chunk, int localX, int y, int localZ) {
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return false;
        }
        MatterCavern cavern = getCavern(chunk, localX, y, localZ);
        return cavern == null || !cavern.isCavern();
    }

    private static MatterCavern getCavern(MantleChunk<Matter> chunk, int localX, int y, int localZ) {
        Matter section = chunk.get(y >> 4);
        if (section == null) {
            return null;
        }

        MatterSlice<MatterCavern> cavernSlice = section.getSlice(MatterCavern.class);
        return cavernSlice == null ? null : cavernSlice.get(localX, y & 15, localZ);
    }

    private static final class FluidCandidateGroup {
        private final MatterCavern fluid;
        private final MatterCavern air;
        private final BitSet positions = new BitSet();

        private FluidCandidateGroup(MatterCavern fluid, MatterCavern air) {
            this.fluid = fluid;
            this.air = air;
        }
    }
}
