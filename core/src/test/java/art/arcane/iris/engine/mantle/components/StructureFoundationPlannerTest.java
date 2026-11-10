package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.structure.ColumnHeights;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureFoundationPlannerTest {
    private static final BlockState SOLID = new BlockState("minecraft:stone");
    private static final BlockState NON_OCCLUDING_SOLID = new BlockState("minecraft:glass");
    private static final BlockState DECORATION = new BlockState("minecraft:poppy");
    private static final BlockState FLUID = new BlockState("minecraft:water");
    private static final BlockRegistry BLOCKS = blocks();

    private static BlockRegistry blocks() {
        BlockRegistry registry = mock(BlockRegistry.class);
        when(registry.hasTrait(any(), any())).thenAnswer(call -> {
            BlockState state = call.getArgument(0);
            BlockTrait trait = call.getArgument(1);
            if (trait == BlockTrait.SOLID) {
                return state.equals(SOLID) || state.equals(NON_OCCLUDING_SOLID);
            }
            return trait == BlockTrait.OCCLUDING && state.equals(SOLID);
        });
        return registry;
    }

    @Test
    public void recordsLowestPlacedOccludingCellByDefault() {
        ColumnHeights columns = new ColumnHeights(0);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 48, 200, DECORATION, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 49, 200, SOLID, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 50, 200, SOLID, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 45, 200, SOLID, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, -17, 60, -33, SOLID, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 41, 44, SOLID, BLOCKS, false);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 36, 44, NON_OCCLUDING_SOLID, BLOCKS, false);

        assertEquals(3, columns.size());
        assertEquals(45, columns.get(StructureFoundationPlanner.pack(100, 200)));
        assertEquals(60, columns.get(StructureFoundationPlanner.pack(-17, -33)));
        assertEquals(41, columns.get(StructureFoundationPlanner.pack(12, 44)));
        assertEquals(-17, StructureFoundationPlanner.unpackX(StructureFoundationPlanner.pack(-17, -33)));
        assertEquals(-33, StructureFoundationPlanner.unpackZ(StructureFoundationPlanner.pack(-17, -33)));
    }

    @Test
    public void recordsSolidNonOccludingBaseWhenEnabled() {
        ColumnHeights columns = new ColumnHeights(0);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 41, 44, SOLID, BLOCKS, true);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 36, 44, NON_OCCLUDING_SOLID, BLOCKS, true);

        assertEquals(1, columns.size());
        assertEquals(36, columns.get(StructureFoundationPlanner.pack(12, 44)));
    }

    @Test
    public void scansThroughAirAndFluidUntilSolidGround() {
        int groundY = StructureFoundationPlanner.findGroundY(10, 10, 0, y -> y == 3);

        assertEquals(3, groundY);
        assertEquals(StructureFoundationPlanner.NO_GROUND,
                StructureFoundationPlanner.findGroundY(10, 6, 0, y -> y == 3));
    }

    @Test
    public void compositeGroundUsesOverlayThenCarvingThenTerrain() {
        assertTrue(StructureFoundationPlanner.isGroundSolid(SOLID, BLOCKS, true, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(FLUID, BLOCKS, false, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(null, BLOCKS, true, 12, 20));
        assertTrue(StructureFoundationPlanner.isGroundSolid(null, BLOCKS, false, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(null, BLOCKS, false, 21, 20));
        assertTrue(StructureFoundationPlanner.isGroundSolid(null, BLOCKS, true, 0, -20));
    }

    @Test
    public void surfaceSupportStopsAtCarvedCaveBoundary() {
        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(null, BLOCKS, true, 18, 24));
        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(SOLID, BLOCKS, false, 30, 24));
        assertFalse(StructureFoundationPlanner.isSurfaceSupportBoundary(FLUID, BLOCKS, false, 30, 24));
        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(null, BLOCKS, false, 24, 24));
    }

    @Test
    public void fillsOnlyTheGapBetweenTheFoundationAndGround() {
        List<Integer> written = new ArrayList<>();

        int count = StructureFoundationPlanner.fillSupportColumn(10, 3, written::add);

        assertEquals(6, count);
        assertEquals(List.of(9, 8, 7, 6, 5, 4), written);
    }

    @Test
    public void doesNotWriteWithoutReachableGroundOrOverExistingGround() {
        List<Integer> written = new ArrayList<>();

        assertEquals(0, StructureFoundationPlanner.fillSupportColumn(
                10, StructureFoundationPlanner.NO_GROUND, written::add));
        assertEquals(0, StructureFoundationPlanner.fillSupportColumn(10, 9, written::add));
        assertTrue(written.isEmpty());
    }
}
