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
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.placement.StructureVolumes;

import java.util.Objects;
import java.util.Optional;

/** A terrain that answers everything as another does; subclasses change what they need. */
public abstract class ForwardingTerrain implements PlacementTerrain {
    private final PlacementTerrain delegate;

    protected ForwardingTerrain(PlacementTerrain delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    protected final PlacementTerrain delegate() {
        return delegate;
    }

    @Override
    public int highest(int x, int z, boolean ignoreFluid) {
        return delegate.highest(x, z, ignoreFluid);
    }

    @Override
    public BlockState get(int x, int y, int z) {
        return delegate.get(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        delegate.set(x, y, z, state);
    }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) {
        delegate.set(x, y, z, state, blockEntity);
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return delegate.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return delegate.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return delegate.isUnderwater(x, z);
    }

    @Override
    public int fluidHeight() {
        return delegate.fluidHeight();
    }

    @Override
    public boolean preventsDecay() {
        return delegate.preventsDecay();
    }

    @Override
    public void recordPlacement(int x, int y, int z, PlacementRecord placement) {
        delegate.recordPlacement(x, y, z, placement);
    }

    @Override
    public Optional<PlacementRecord> placementAt(int x, int y, int z) {
        return delegate.placementAt(x, y, z);
    }

    @Override
    public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) {
        return delegate.structureVolumes(minX, minZ, maxX, maxZ);
    }

    @Override
    public void mark(PlacedMarker marker) {
        delegate.mark(marker);
    }
}
