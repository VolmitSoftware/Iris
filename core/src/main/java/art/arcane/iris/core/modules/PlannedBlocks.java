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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The blocks Iris's mantle holds for chunks to take over, IrisApi's {@link com.volmit.mantle.Mantle#blocks()}: what object
 * and structure placements wrote into the mantle's block slice, read as IrisApi block states over the chunks one
 * invocation may touch. Stages only read them. A dimension that keeps no mantle ({@code useMantle: false}) holds none,
 * even where an older mantle file still has some, as Iris never copied those into chunks; neither does a chunk that
 * generates without its mantle (maintenance regeneration on Folia, {@link art.arcane.iris.engine.framework.EngineGeneration#bypassesMantleStages()}).
 */
final class PlannedBlocks implements MantleLayer<BlockState> {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;
    private static final String READ_ONLY = "Stages read the planned blocks; placements write them with PlacementAccess.set";
    private static final Comparator<Planned> VISIT_ORDER = Comparator.comparingInt(Planned::x)
            .thenComparingInt(Planned::y).thenComparingInt(Planned::z);

    private final BlockBox bounds;
    private final IrisModuleMantle.Chunks chunks;
    private final PlatformBlockStates states;
    private final BooleanSupplier kept;
    private Boolean keptHere;

    private PlannedBlocks(BlockBox bounds, IrisModuleMantle.Chunks chunks, PlatformBlockStates states, BooleanSupplier kept) {
        this.bounds = bounds;
        this.chunks = chunks;
        this.states = states;
        this.kept = kept;
    }

    /** The planned blocks of {@code engine}'s mantle in {@code bounds}, converted with the invocation's {@code states}. */
    static PlannedBlocks of(Engine engine, BlockBox bounds, IrisModuleMantle.Chunks chunks, PlatformBlockStates states) {
        return new PlannedBlocks(bounds, chunks, states,
                () -> engine.getDimension().isUseMantle() && !engine.getGeneration().bypassesMantleStages());
    }

    /** No planned blocks in {@code bounds}, as in a dimension that keeps no mantle. */
    static PlannedBlocks none(BlockBox bounds) {
        return new PlannedBlocks(bounds, (chunkX, chunkZ) -> null, null, () -> false);
    }

    @Override
    public BlockState get(int x, int y, int z) {
        requireInside(x, y, z);
        if (!isKept()) {
            return null;
        }
        PlatformBlockState planned = plannedAt(requireChunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT), x, y, z);
        return planned == null ? null : states.toApi(planned);
    }

    @Override
    public void visit(BlockBox area, MantleVisitor<BlockState> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        requireInside(area);
        if (!isKept()) {
            return;
        }
        for (Planned planned : plannedIn(area)) {
            visitor.accept(planned.x(), planned.y(), planned.z(), states.toApi(planned.block()));
        }
    }

    @Override
    public void set(int x, int y, int z, BlockState value) {
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

    /** Asked when a stage first reads, so invocations that never do never ask the dimension. */
    private boolean isKept() {
        if (keptHere == null) {
            keptHere = kept.getAsBoolean();
        }
        return keptHere;
    }

    /** The planned blocks in {@code area}, ordered by x, then y, then z, as {@link MantleLayer#visit} promises. */
    private List<Planned> plannedIn(BlockBox area) {
        List<Planned> planned = new ArrayList<>();
        for (int chunkX = area.minX() >> CHUNK_SHIFT; chunkX <= (area.maxX() - 1) >> CHUNK_SHIFT; chunkX++) {
            for (int chunkZ = area.minZ() >> CHUNK_SHIFT; chunkZ <= (area.maxZ() - 1) >> CHUNK_SHIFT; chunkZ++) {
                collect(chunkX, chunkZ, area, planned);
            }
        }
        planned.sort(VISIT_ORDER);
        return planned;
    }

    private void collect(int chunkX, int chunkZ, BlockBox area, List<Planned> planned) {
        int originX = chunkX << CHUNK_SHIFT;
        int originZ = chunkZ << CHUNK_SHIFT;
        requireChunk(chunkX, chunkZ).iterate(PlatformBlockState.class, (localX, y, localZ, block) -> {
            int x = originX + localX;
            int z = originZ + localZ;
            if (area.contains(x, y, z)) {
                planned.add(new Planned(x, y, z, block));
            }
        });
    }

    private static PlatformBlockState plannedAt(MantleChunk<Matter> chunk, int x, int y, int z) {
        int section = y >> SECTION_SHIFT;
        if (!chunk.exists(section)) {
            return null;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(PlatformBlockState.class)) {
            return null;
        }
        return matter.<PlatformBlockState>getSlice(PlatformBlockState.class).get(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK);
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

    private record Planned(int x, int y, int z, PlatformBlockState block) {
    }
}
