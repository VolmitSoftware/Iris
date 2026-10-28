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

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds back what an object writes until all of it is known to be acceptable, then writes everything in the order it was
 * written, or nothing. Reads see the held writes. Subclasses say which blocks are acceptable.
 */
public abstract class TerrainTransaction extends ForwardingTerrain {
    private final List<Write> writes = new ArrayList<>();
    private final Map<BlockPosition, BlockState> heldBlocks = new HashMap<>();
    private int blockWrites;

    public enum Commit {
        /** Everything was written. */
        COMMITTED,
        /** No block was written, so nothing was. */
        EMPTY,
        /** Some write was not acceptable, so nothing was written. */
        REJECTED
    }

    protected TerrainTransaction(PlacementTerrain delegate) {
        super(delegate);
    }

    /** Whether the transaction may write the block. */
    protected abstract boolean accepts(int x, int y, int z);

    public final Commit commit() {
        if (blockWrites == 0) {
            discard();
            return Commit.EMPTY;
        }
        for (Write write : writes) {
            BlockPosition position = write.position();
            if (!accepts(position.x(), position.y(), position.z())) {
                discard();
                return Commit.REJECTED;
            }
        }
        for (Write write : writes) {
            write.applyTo(delegate());
        }
        discard();
        return Commit.COMMITTED;
    }

    public final void discard() {
        writes.clear();
        heldBlocks.clear();
        blockWrites = 0;
    }

    @Override
    public BlockState get(int x, int y, int z) {
        BlockState held = heldBlocks.get(new BlockPosition(x, y, z));
        return held == null ? delegate().get(x, y, z) : held;
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        holdBlock(new BlockWrite(new BlockPosition(x, y, z), state, null));
    }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
        holdBlock(new BlockWrite(new BlockPosition(x, y, z), state, blockEntity));
    }

    @Override
    public void recordPlacement(int x, int y, int z, PlacementRecord placement) {
        writes.add(new PlacementWrite(new BlockPosition(x, y, z), placement));
    }

    @Override
    public Optional<PlacementRecord> placementAt(int x, int y, int z) {
        BlockPosition position = new BlockPosition(x, y, z);
        for (int index = writes.size() - 1; index >= 0; index--) {
            if (writes.get(index) instanceof PlacementWrite write && write.position().equals(position)) {
                return Optional.of(write.placement());
            }
        }
        return delegate().placementAt(x, y, z);
    }

    @Override
    public void mark(PlacedMarker marker) {
        writes.add(new MarkerWrite(marker));
    }

    private void holdBlock(BlockWrite write) {
        writes.add(write);
        heldBlocks.put(write.position(), write.state());
        blockWrites++;
    }

    private sealed interface Write permits BlockWrite, PlacementWrite, MarkerWrite {
        BlockPosition position();

        void applyTo(PlacementTerrain terrain);
    }

    private record BlockWrite(BlockPosition position, BlockState state, BlockEntityData blockEntity) implements Write {
        @Override
        public void applyTo(PlacementTerrain terrain) {
            if (blockEntity == null) {
                terrain.set(position.x(), position.y(), position.z(), state);
            } else {
                terrain.set(position.x(), position.y(), position.z(), state, blockEntity);
            }
        }
    }

    private record PlacementWrite(BlockPosition position, PlacementRecord placement) implements Write {
        @Override
        public void applyTo(PlacementTerrain terrain) {
            terrain.recordPlacement(position.x(), position.y(), position.z(), placement);
        }
    }

    private record MarkerWrite(PlacedMarker marker) implements Write {
        @Override
        public BlockPosition position() {
            return marker.position();
        }

        @Override
        public void applyTo(PlacementTerrain terrain) {
            terrain.mark(marker);
        }
    }
}
