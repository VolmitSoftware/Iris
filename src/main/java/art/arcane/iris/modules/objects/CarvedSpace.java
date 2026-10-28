/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.objects;

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.Optional;

/**
 * The voids the cave carver planned in the mantle around the chunk being placed, which cave objects anchor in. Heights
 * are above the dimension's bottom; outside the mantle the invocation reaches nothing is carved.
 */
public final class CarvedSpace {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int SECTION_HEIGHT = 16;
    private static final int LOCAL_MASK = 15;
    private static final byte CARVED = 1;

    private final MatterMantle mantle;
    private final int worldHeight;

    public CarvedSpace(MatterMantle mantle, int worldHeight) {
        this.mantle = mantle;
        this.worldHeight = worldHeight;
    }

    /** Whether the position is carved. */
    public boolean isCarved(int x, int y, int z) {
        return cavernData(x, y, z) != null;
    }

    /** Whether each height from 0 up to {@code height} is carved at the column, 1 for carved and 0 for not. */
    public byte[] column(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), worldHeight);
        byte[] carvedColumn = new byte[cappedHeight];
        if (cappedHeight <= 0) {
            return carvedColumn;
        }
        MantleChunk<Matter> chunk = mantle.chunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT);
        if (chunk == null) {
            return carvedColumn;
        }
        int localX = x & LOCAL_MASK;
        int localZ = z & LOCAL_MASK;
        int lastSection = (cappedHeight - 1) >> SECTION_SHIFT;
        for (int section = 0; section <= lastSection; section++) {
            MatterSlice<MatterCavern> slice = cavernSlice(chunk, section);
            if (slice == null) {
                continue;
            }
            int sectionBaseY = section << SECTION_SHIFT;
            int sectionMaxY = Math.min(cappedHeight, sectionBaseY + SECTION_HEIGHT);
            for (int y = sectionBaseY; y < sectionMaxY; y++) {
                if (slice.get(localX, y & LOCAL_MASK, localZ) != null) {
                    carvedColumn[y] = CARVED;
                }
            }
        }
        return carvedColumn;
    }

    /** The void at the position; empty where nothing was carved. */
    public Optional<Cavern> cavernAt(int x, int y, int z) {
        MatterCavern cavern = cavernData(x, y, z);
        if (cavern == null || !cavern.isCavern()) {
            return Optional.empty();
        }
        return Optional.of(new Cavern(cavern.isAir(), cavern.getLiquid()));
    }

    private MatterCavern cavernData(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) {
            return null;
        }
        MantleChunk<Matter> chunk = mantle.chunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT);
        if (chunk == null) {
            return null;
        }
        MatterSlice<MatterCavern> slice = cavernSlice(chunk, y >> SECTION_SHIFT);
        return slice == null ? null : slice.get(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK);
    }

    private static MatterSlice<MatterCavern> cavernSlice(MantleChunk<Matter> chunk, int section) {
        if (!chunk.exists(section)) {
            return null;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(MatterCavern.class)) {
            return null;
        }
        return matter.getSlice(MatterCavern.class);
    }
}
