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
import art.arcane.iris.util.common.data.B;
import com.volmit.block.BlockState;
import com.volmit.host.MantleRegistration;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

import java.util.Collection;
import java.util.Objects;

/**
 * The mantle a placement's stages see: the engine's mantle around the placement, with IrisApi's heights, which are
 * the placer's own (0 is the bottom of the dimension), so a stage's mantle writes land where its block writes do. Everything a stage writes is buffered in one mantle edit of the placement: it reaches the
 * mantle only when {@link #commit()} runs with the placement's own commit, and {@link #close()} discards it.
 * IrisApi keeps stages from opening edits of their own: the placement owns this one.
 * <p>
 * The placement area has no vertical limit, so the bounds say so; a block above or below the dimension holds
 * nothing and cannot be written.
 */
final class PlacementMantle implements Mantle, AutoCloseable {
    private final BlockBox bounds;
    private final BlockBox storedBounds;
    private final PinnedMantleChunks chunks;
    private final MantleEdit placement;

    PlacementMantle(Engine engine, BlockBox area, Collection<MantleRegistration<?>> layers) {
        this.bounds = area;
        this.storedBounds = ApiHeights.columns(engine, area.minX(), area.minZ(), area.maxX(), area.maxZ());
        this.chunks = new PinnedMantleChunks(engine);
        PlannedBlocks plannedBlocks = PlannedBlocks.of(engine, storedBounds, chunks, new PlatformBlockStates(B::getState));
        this.placement = new IrisModuleMantle(storedBounds, chunks, layers, plannedBlocks).edit();
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public <T> MantleLayer<T> layer(MantleKey<T> key) {
        return new StoredRange<>(placement.layer(key));
    }

    @Override
    public MantleLayer<BlockState> blocks() {
        return new StoredRange<>(placement.blocks());
    }

    @Override
    public MantleLayer<Boolean> caverns() {
        return new StoredRange<>(placement.caverns());
    }

    @Override
    public MantleEdit edit() {
        throw new UnsupportedOperationException("The placement owns the mantle edit; write through layer()");
    }

    /** Publishes everything the placement wrote, or nothing when a mantle chunk it touches is missing. */
    void commit() {
        try {
            placement.commit();
        } finally {
            close();
        }
    }

    /** Discards what was not committed and lets go of the mantle chunks. */
    @Override
    public void close() {
        placement.close();
        chunks.close();
    }

    /** A layer of the placement edit, limited to the heights the mantle stores. */
    private final class StoredRange<T> implements MantleLayer<T> {
        private final MantleLayer<T> layer;

        StoredRange(MantleLayer<T> layer) {
            this.layer = layer;
        }

        @Override
        public T get(int x, int y, int z) {
            requireInArea(x, y, z);
            return isStored(y) ? layer.get(x, y, z) : null;
        }

        @Override
        public void set(int x, int y, int z, T value) {
            requireInArea(x, y, z);
            requireStored(x, y, z);
            layer.set(x, y, z, value);
        }

        @Override
        public void remove(int x, int y, int z) {
            requireInArea(x, y, z);
            if (isStored(y)) {
                layer.remove(x, y, z);
            }
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            Objects.requireNonNull(visitor, "visitor");
            layer.visit(storedPart(area), visitor);
        }

        @Override
        public void clear(BlockBox area) {
            layer.clear(storedPart(area));
        }

        private BlockBox storedPart(BlockBox area) {
            Objects.requireNonNull(area, "area");
            if (!bounds.contains(area)) {
                throw new IndexOutOfBoundsException("Area " + area + " is not inside the placement area " + bounds);
            }
            int minY = Math.max(area.minY(), storedBounds.minY());
            int maxY = Math.min(area.maxY(), storedBounds.maxY());
            return new BlockBox(area.minX(), Math.min(minY, maxY), area.minZ(), area.maxX(), maxY, area.maxZ());
        }

        private void requireInArea(int x, int y, int z) {
            if (!bounds.contains(x, y, z)) {
                throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the placement area " + bounds);
            }
        }

        private void requireStored(int x, int y, int z) {
            if (!isStored(y)) {
                throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the heights the mantle stores, "
                        + storedBounds.minY() + " to " + (storedBounds.maxY() - 1));
            }
        }

        private boolean isStored(int y) {
            return y >= storedBounds.minY() && y < storedBounds.maxY();
        }
    }
}
