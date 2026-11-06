package com.volmit.support;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.placement.StructureVolumes;
import com.volmit.world.BlockView;
import java.util.Optional;

/** Placement terrain over test blocks that answers column heights and reads; anything else fails the test. */
public final class ColumnTerrain implements PlacementTerrain {
    private final BlockView blocks;

    public ColumnTerrain(BlockView blocks) {
        this.blocks = blocks;
    }

    @Override
    public int highest(int x, int z, boolean ignoreFluid) {
        var bounds = blocks.bounds();
        for (int y = bounds.maxY() - 1; y >= bounds.minY(); y--) {
            if (!blocks.get(x, y, z).material().equals(TestTerrain.AIR.material())) {
                return y;
            }
        }
        return bounds.minY() - 1;
    }

    @Override
    public BlockState get(int x, int y, int z) {
        return blocks.get(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, BlockState state) { throw unused("set"); }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData blockEntity) { throw unused("set"); }

    @Override
    public boolean isCarved(int x, int y, int z) { throw unused("isCarved"); }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) { throw unused("isSurfaceSolid"); }

    @Override
    public boolean isUnderwater(int x, int z) { throw unused("isUnderwater"); }

    @Override
    public int fluidHeight() { throw unused("fluidHeight"); }

    @Override
    public boolean preventsDecay() { throw unused("preventsDecay"); }

    @Override
    public void recordPlacement(int x, int y, int z, PlacementRecord placement) { throw unused("recordPlacement"); }

    @Override
    public Optional<PlacementRecord> placementAt(int x, int y, int z) { throw unused("placementAt"); }

    @Override
    public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) { throw unused("structureVolumes"); }

    @Override
    public void mark(PlacedMarker marker) { throw unused("mark"); }

    private static AssertionError unused(String method) {
        return new AssertionError("Unused placement terrain " + method);
    }
}
