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
package art.arcane.iris.core.modules;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The voids Iris's mantle holds for chunks to take over, IrisApi's {@link com.volmit.mantle.Mantle#caverns()}: where the cave
 * planner marked a cavern in the mantle's cavern slice, whatever the cavern holds (air, fluid, lava). Stages only read them.
 */
final class MantleCaverns implements MantleLayer<Boolean> {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;
    private static final String READ_ONLY = "Stages read the caverns; the cave planner writes them";
    private static final Comparator<Position> VISIT_ORDER = Comparator.comparingInt(Position::x)
            .thenComparingInt(Position::y).thenComparingInt(Position::z);

    private final BlockBox bounds;
    private final IrisModuleMantle.Chunks chunks;

    MantleCaverns(BlockBox bounds, IrisModuleMantle.Chunks chunks) {
        this.bounds = bounds;
        this.chunks = chunks;
    }

    @Override
    public Boolean get(int x, int y, int z) {
        requireInside(x, y, z);
        return cavernAt(requireChunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT), x, y, z) ? Boolean.TRUE : null;
    }

    @Override
    public void visit(BlockBox area, MantleVisitor<Boolean> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        requireInside(area);
        for (Position carved : carvedIn(area)) {
            visitor.accept(carved.x(), carved.y(), carved.z(), Boolean.TRUE);
        }
    }

    @Override
    public void set(int x, int y, int z, Boolean value) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public void remove(int x, int y, int z) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public void clear(BlockBox area) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    /** The carved positions in {@code area}, ordered by x, then y, then z, as {@link MantleLayer#visit} promises. */
    private List<Position> carvedIn(BlockBox area) {
        List<Position> carved = new ArrayList<>();
        for (int chunkX = area.minX() >> CHUNK_SHIFT; chunkX <= (area.maxX() - 1) >> CHUNK_SHIFT; chunkX++) {
            for (int chunkZ = area.minZ() >> CHUNK_SHIFT; chunkZ <= (area.maxZ() - 1) >> CHUNK_SHIFT; chunkZ++) {
                collect(chunkX, chunkZ, area, carved);
            }
        }
        carved.sort(VISIT_ORDER);
        return carved;
    }

    private void collect(int chunkX, int chunkZ, BlockBox area, List<Position> carved) {
        int originX = chunkX << CHUNK_SHIFT;
        int originZ = chunkZ << CHUNK_SHIFT;
        requireChunk(chunkX, chunkZ).iterate(MatterCavern.class, (localX, y, localZ, cavern) -> {
            int x = originX + localX;
            int z = originZ + localZ;
            if (cavern != null && area.contains(x, y, z)) {
                carved.add(new Position(x, y, z));
            }
        });
    }

    private static boolean cavernAt(MantleChunk<Matter> chunk, int x, int y, int z) {
        int section = y >> SECTION_SHIFT;
        if (!chunk.exists(section)) {
            return false;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(MatterCavern.class)) {
            return false;
        }
        return matter.<MatterCavern>getSlice(MatterCavern.class).get(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK) != null;
    }

    private MantleChunk<Matter> requireChunk(int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = chunks.chunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("Mantle chunk " + chunkX + ", " + chunkZ + " is inside " + bounds + " but was not prepared");
        }
        return chunk;
    }

    private void requireInside(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the mantle this invocation prepared, " + bounds);
        }
    }

    private void requireInside(BlockBox area) {
        Objects.requireNonNull(area, "area");
        if (!bounds.contains(area)) {
            throw new IndexOutOfBoundsException("Area " + area + " is not inside the mantle this invocation prepared, " + bounds);
        }
    }

    private record Position(int x, int y, int z) {
    }
}
