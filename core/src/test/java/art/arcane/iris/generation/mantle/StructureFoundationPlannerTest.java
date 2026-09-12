package art.arcane.iris.generation.mantle;

import art.arcane.iris.spi.PlatformBlockState;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureFoundationPlannerTest {
    @Test
    public void recordsLowestPlacedOccludingCellByDefault() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(solid.isOccluding()).thenReturn(true);
        when(solid.isSolid()).thenReturn(true);
        PlatformBlockState nonOccludingSolid = mock(PlatformBlockState.class);
        when(nonOccludingSolid.isSolid()).thenReturn(true);
        when(nonOccludingSolid.isOccluding()).thenReturn(false);
        PlatformBlockState decoration = mock(PlatformBlockState.class);
        when(decoration.isOccluding()).thenReturn(false);

        Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
        StructureFoundationPlanner.recordBaseCell(columns, 100, 48, 200, decoration, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 49, 200, solid, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 50, 200, solid, false);
        StructureFoundationPlanner.recordBaseCell(columns, 100, 45, 200, solid, false);
        StructureFoundationPlanner.recordBaseCell(columns, -17, 60, -33, solid, false);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 41, 44, solid, false);
        StructureFoundationPlanner.recordBaseCell(columns, 12, 36, 44, nonOccludingSolid, false);

        assertEquals(3, columns.size());
        assertEquals(45, columns.get(StructureFoundationPlanner.pack(100, 200)));
        assertEquals(60, columns.get(StructureFoundationPlanner.pack(-17, -33)));
        assertEquals(41, columns.get(StructureFoundationPlanner.pack(12, 44)));
        assertEquals(-17, StructureFoundationPlanner.unpackX(StructureFoundationPlanner.pack(-17, -33)));
        assertEquals(-33, StructureFoundationPlanner.unpackZ(StructureFoundationPlanner.pack(-17, -33)));
    }

    @Test
    public void recordsSolidNonOccludingBaseWhenEnabled() {
        PlatformBlockState partialStructureBlock = mock(PlatformBlockState.class);
        when(partialStructureBlock.isSolid()).thenReturn(true);
        when(partialStructureBlock.isOccluding()).thenReturn(false);
        PlatformBlockState fullStructureBlock = mock(PlatformBlockState.class);
        when(fullStructureBlock.isSolid()).thenReturn(true);
        when(fullStructureBlock.isOccluding()).thenReturn(true);

        Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
        StructureFoundationPlanner.recordBaseCell(
                columns, 12, 41, 44, fullStructureBlock, true);
        StructureFoundationPlanner.recordBaseCell(
                columns, 12, 36, 44, partialStructureBlock, true);

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
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        PlatformBlockState fluid = mock(PlatformBlockState.class);
        when(fluid.isSolid()).thenReturn(false);

        assertTrue(StructureFoundationPlanner.isGroundSolid(solid, true, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(fluid, false, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(null, true, 12, 20));
        assertTrue(StructureFoundationPlanner.isGroundSolid(null, false, 12, 20));
        assertFalse(StructureFoundationPlanner.isGroundSolid(null, false, 21, 20));
        assertTrue(StructureFoundationPlanner.isGroundSolid(null, true, 0, -20));
    }

    @Test
    public void surfaceSupportStopsAtCarvedCaveBoundary() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(solid.isSolid()).thenReturn(true);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isSolid()).thenReturn(false);

        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(null, true, 18, 24));
        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(solid, false, 30, 24));
        assertFalse(StructureFoundationPlanner.isSurfaceSupportBoundary(air, false, 30, 24));
        assertTrue(StructureFoundationPlanner.isSurfaceSupportBoundary(null, false, 24, 24));
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
