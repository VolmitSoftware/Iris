package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.MantleWriter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SeaInletRasterizerTest {
    private static final int SEA = 40;
    private static final String FLUID = HydrologyCaveCell.SEA_FLUID;
    private static final int CAVE_CLEARANCE = 2;

    @Test
    public void waterLevelIsSeaLevelEverywhere() {
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(inlet(1L, 15f)), order(false));
        int wetColumns = 0;
        Map<Long, Integer> tops = new TreeMap<>();
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            int y = y(entry.getKey());
            HydrologyCaveAction action = entry.getValue().action();
            if (action == HydrologyCaveAction.WET_SOURCE) {
                assertTrue("water at " + y, y <= SEA);
                tops.merge(column(entry.getKey()), y, Math::max);
            } else if (action == HydrologyCaveAction.DRY_AIR) {
                assertTrue("air at " + y, y > SEA);
            }
            assertEquals(FLUID, entry.getValue().fluidProfileKey());
        }
        for (int top : tops.values()) {
            assertEquals(SEA, top);
            wetColumns++;
        }
        assertTrue("wet columns " + wetColumns, wetColumns > 300);
    }

    @Test
    public void everyWaterCellIsHeldBySealedOrWetNeighbours() {
        List<SeaInlet> inlets = List.of(inlet(1L, 15f), inlet(2L, 30f));
        Map<Long, HydrologyCaveCell> cells = rasterize(inlets, order(false));
        int checked = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.WET_SOURCE) {
                continue;
            }
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            for (int[] side : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                if (paintedSlot(inlets, x + side[0], y + side[1], z + side[2])) {
                    continue;
                }
                HydrologyCaveCell neighbour = cells.get(key(x + side[0], y + side[1], z + side[2]));
                assertNotNull("water at " + x + "," + y + "," + z + " has an open side " + side[0] + "," + side[1] + "," + side[2], neighbour);
                assertTrue(neighbour.action() == HydrologyCaveAction.WET_SOURCE || neighbour.action() == HydrologyCaveAction.SEAL_GUARD);
            }
            checked++;
        }
        assertTrue(checked > 1000);
    }

    @Test
    public void sealKeepsCavesTwoBlocksFromTheWater() {
        List<SeaInlet> inlets = List.of(inlet(1L, 15f));
        Map<Long, HydrologyCaveCell> cells = rasterize(inlets, order(false));
        int checked = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.WET_SOURCE) {
                continue;
            }
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            for (int below = 0; below <= CAVE_CLEARANCE; below++) {
                for (int dz = -CAVE_CLEARANCE; dz <= CAVE_CLEARANCE; dz++) {
                    for (int dx = -CAVE_CLEARANCE; dx <= CAVE_CLEARANCE; dx++) {
                        if (paintedSlot(inlets, x + dx, y - below, z + dz)) {
                            continue;
                        }
                        HydrologyCaveCell held = cells.get(key(x + dx, y - below, z + dz));
                        assertNotNull("no seal at " + (x + dx) + "," + (y - below) + "," + (z + dz) + " near water at " + x + "," + y + "," + z,
                                held);
                        assertTrue(held.action() == HydrologyCaveAction.WET_SOURCE || held.action() == HydrologyCaveAction.SEAL_GUARD);
                    }
                }
            }
            if (cells.get(key(x, y - 1, z)).action() == HydrologyCaveAction.SEAL_GUARD) {
                for (int under = 1; under <= CAVE_CLEARANCE + 1; under++) {
                    assertEquals("bed and two blocks under the water at " + x + "," + y + "," + z, HydrologyCaveAction.SEAL_GUARD,
                            cells.get(key(x, y - under, z)).action());
                }
            }
            checked++;
        }
        assertTrue(checked > 1000);
    }

    @Test
    public void openSlotColumnsLeaveTheirWaterToThePainter() {
        SeaInlet inlet = inlet(1L, 15f);
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(inlet), order(false));
        int[] column = new int[2];
        int sealed = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            int x = x(entry.getKey());
            int z = z(entry.getKey());
            if (!SeaInletRasterizer.column(inlet, x, z, column) || column[1] != SeaInlet.OPEN) {
                continue;
            }
            assertEquals("open slot column " + x + "," + z + " holds only its seal", HydrologyCaveAction.SEAL_GUARD, entry.getValue().action());
            assertTrue(y(entry.getKey()) <= column[0]);
            sealed++;
        }
        assertTrue("open slot seal cells " + sealed, sealed > 100);
        assertTrue(cells.values().stream().anyMatch(cell -> cell.action() == HydrologyCaveAction.DRY_AIR));
        assertTrue(cells.values().stream().anyMatch(cell -> cell.action() == HydrologyCaveAction.WET_SOURCE));
    }

    @Test
    public void openSlotBedsKeepTheirSeaFloorOnTwoSealedBlocks() {
        SeaInlet inlet = inlet(1L, 15f);
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(inlet), order(false));
        int[] column = new int[2];
        int beds = 0;
        int covered = 0;
        for (int z = -16; z <= 63; z++) {
            for (int x = -32; x <= 79; x++) {
                if (!SeaInletRasterizer.column(inlet, x, z, column)) {
                    continue;
                }
                int floor = column[0];
                if (column[1] == SeaInlet.OPEN) {
                    assertNull("the open slot bed at " + x + "," + floor + "," + z + " keeps the painted sea floor", cells.get(key(x, floor, z)));
                    for (int under = 1; under <= SeaInletRasterizer.SHELL; under++) {
                        HydrologyCaveCell sealed = cells.get(key(x, floor - under, z));
                        assertNotNull("rock under the open slot bed at " + x + "," + (floor - under) + "," + z, sealed);
                        assertEquals(HydrologyCaveAction.SEAL_GUARD, sealed.action());
                    }
                    beds++;
                } else {
                    assertEquals("covered bed at " + x + "," + floor + "," + z, HydrologyCaveAction.SEAL_GUARD, cells.get(key(x, floor, z)).action());
                    covered++;
                }
            }
        }
        assertTrue("open slot beds " + beds, beds > 20);
        assertTrue("covered beds " + covered, covered > 100);
    }

    @Test
    public void subterrainFluidWithinTwoBlocksOfInletAirAboveSeaLevelIsSealed() {
        SeaInlet inlet = inlet(1L, 15f);
        SeaInletRasterizer.SubterrainFluid flooded = (x, y, z) -> Math.floorMod(x + 2 * z + y, 5) != 0;
        Map<Long, HydrologyCaveCell> plain = rasterize(List.of(inlet), order(false));
        for (Map.Entry<Long, HydrologyCaveCell> entry : plain.entrySet()) {
            assertTrue("without subterrain fluid nothing above sea level is sealed",
                    y(entry.getKey()) <= SEA || entry.getValue().action() == HydrologyCaveAction.DRY_AIR);
        }
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(inlet), order(false), flooded);
        int checked = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.DRY_AIR) {
                continue;
            }
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (y + dy <= SEA || !flooded.at(x + dx, y + dy, z + dz)) {
                            continue;
                        }
                        HydrologyCaveCell held = cells.get(key(x + dx, y + dy, z + dz));
                        assertNotNull("subterrain fluid at " + (x + dx) + "," + (y + dy) + "," + (z + dz) + " beside inlet air at " + x + "," + y
                                + "," + z, held);
                        assertTrue(held.action() == HydrologyCaveAction.DRY_AIR || held.action() == HydrologyCaveAction.SEAL_GUARD);
                        checked++;
                    }
                }
            }
        }
        int sealed = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            if (y <= SEA || entry.getValue().action() != HydrologyCaveAction.SEAL_GUARD) {
                continue;
            }
            assertTrue("only subterrain fluid is sealed above sea level, not " + x + "," + y + "," + z, flooded.at(x, y, z));
            assertTrue("the seal at " + x + "," + y + "," + z + " lies within two blocks of inlet air", nearAir(cells, x, y, z));
            sealed++;
        }
        assertTrue("checked " + checked, checked > 1000);
        assertTrue("sealed " + sealed, sealed > 200);
        assertEquals(cells, rasterize(List.of(inlet), order(true), flooded));
    }

    private static boolean nearAir(Map<Long, HydrologyCaveCell> cells, int x, int y, int z) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    HydrologyCaveCell cell = cells.get(key(x + dx, y + dy, z + dz));
                    if (cell != null && cell.action() == HydrologyCaveAction.DRY_AIR) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean paintedSlot(List<SeaInlet> inlets, int x, int y, int z) {
        int[] column = new int[2];
        for (SeaInlet inlet : inlets) {
            if (SeaInletRasterizer.column(inlet, x, z, column) && column[1] == SeaInlet.OPEN && y >= column[0] && y <= inlet.waterLevel()) {
                return true;
            }
        }
        return false;
    }

    @Test(timeout = 20_000L)
    public void thinDiagonalPassagesSealTheirBedWithoutRunningAway() {
        float[] xs = {0f, 8f, 16f, 24f, 32f};
        float[] zs = {0f, 8f, 16f, 24f, 32f};
        float[] halfWidth = {0.6f, 0.6f, 0.6f, 0.6f, 0.6f};
        int[] floor = {SEA - 3, SEA - 3, SEA - 3, SEA - 3, SEA - 3};
        int[] ceiling = {SEA + 4, SEA + 4, SEA + 4, SEA + 4, SEA + 4};
        SeaInlet thin = new SeaInlet(9L, xs, zs, halfWidth, SEA, floor, ceiling, 32, 32, 2);
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(thin), order(false));
        int wet = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            assertTrue("no cell runs away below the shell", y(entry.getKey()) >= SEA - 3 - CAVE_CLEARANCE - 1);
            if (entry.getValue().action() == HydrologyCaveAction.WET_SOURCE) {
                wet++;
                assertEquals(HydrologyCaveAction.SEAL_GUARD, cells.get(key(x(entry.getKey()), SEA - 3, z(entry.getKey()))).action());
            }
        }
        assertTrue(wet > 10);
    }

    @Test
    public void chunkOrderAndInletOrderDoNotChangeTheCells() {
        List<SeaInlet> inlets = List.of(inlet(1L, 15f), inlet(2L, 22f));
        Map<Long, HydrologyCaveCell> forward = rasterize(inlets, order(false));
        Map<Long, HydrologyCaveCell> reverse = rasterize(List.of(inlets.get(1), inlets.get(0)), order(true));
        assertEquals(forward, reverse);
        assertFalse(forward.isEmpty());
    }

    @Test
    public void chunksWriteOnlyTheirOwnColumns() {
        SeaInlet inlet = inlet(1L, 15f);
        Map<Long, HydrologyCaveCell> cells = new TreeMap<>();
        MantleWriter writer = writer(cells, 1, 0);
        new SeaInletRasterizer().write(inlet, 1, 0, writer, FLUID, "", SeaInletRasterizer.SubterrainFluid.NOWHERE);
        assertFalse(cells.isEmpty());
        for (long key : cells.keySet()) {
            assertEquals(1, x(key) >> 4);
            assertEquals(0, z(key) >> 4);
        }
        Map<Long, HydrologyCaveCell> far = new TreeMap<>();
        new SeaInletRasterizer().write(inlet, 20, 20, writer(far, 20, 20), FLUID, "", SeaInletRasterizer.SubterrainFluid.NOWHERE);
        assertTrue(far.isEmpty());
        assertFalse(SeaInletRasterizer.touches(inlet, 20, 20));
    }

    private static SeaInlet inlet(long id, float z) {
        float[] xs = {-8f, 0f, 8f, 16f, 24f, 32f, 40f, 48f};
        float[] zs = new float[xs.length];
        float[] halfWidth = new float[xs.length];
        int[] floor = new int[xs.length];
        int[] ceiling = new int[xs.length];
        for (int station = 0; station < xs.length; station++) {
            zs[station] = z + (station % 2 == 0 ? 0f : 3f);
            halfWidth[station] = 5f - station * 0.4f;
            floor[station] = SEA - 2 - station % 3;
            ceiling[station] = station < 2 ? SeaInlet.OPEN : SEA + 4 + station % 4;
        }
        return new SeaInlet(id, xs, zs, halfWidth, SEA, floor, ceiling, 48, (int) z, 7);
    }

    private static List<int[]> order(boolean reverse) {
        List<int[]> chunks = new ArrayList<>();
        for (int chunkZ = -1; chunkZ <= 3; chunkZ++) {
            for (int chunkX = -2; chunkX <= 4; chunkX++) {
                chunks.add(new int[]{chunkX, chunkZ});
            }
        }
        if (reverse) {
            Collections.reverse(chunks);
        }
        return chunks;
    }

    private static Map<Long, HydrologyCaveCell> rasterize(List<SeaInlet> inlets, List<int[]> chunks) {
        return rasterize(inlets, chunks, SeaInletRasterizer.SubterrainFluid.NOWHERE);
    }

    private static Map<Long, HydrologyCaveCell> rasterize(List<SeaInlet> inlets, List<int[]> chunks, SeaInletRasterizer.SubterrainFluid subterrain) {
        Map<Long, HydrologyCaveCell> cells = new TreeMap<>();
        SeaInletRasterizer rasterizer = new SeaInletRasterizer();
        for (int[] chunk : chunks) {
            MantleWriter writer = writer(cells, chunk[0], chunk[1]);
            for (SeaInlet inlet : inlets) {
                rasterizer.write(inlet, chunk[0], chunk[1], writer, FLUID, "", subterrain);
            }
        }
        return cells;
    }

    private static MantleWriter writer(Map<Long, HydrologyCaveCell> cells, int chunkX, int chunkZ) {
        MantleWriter writer = mock(MantleWriter.class);
        doAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(2);
            if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                throw new AssertionError("chunk " + chunkX + "," + chunkZ + " wrote " + x + "," + z);
            }
            cells.put(key(x, call.getArgument(1), z), call.getArgument(3));
            return null;
        }).when(writer).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
        when(writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class)))
                .thenAnswer(call -> cells.get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
        return writer;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (z + 4096) << 20) | (y + 4096);
    }

    private static long column(long key) {
        return key >>> 20;
    }

    private static int x(long key) {
        return (int) (key >>> 40) - 4096;
    }

    private static int z(long key) {
        return (int) ((key >>> 20) & 0xFFFFF) - 4096;
    }

    private static int y(long key) {
        return (int) (key & 0xFFFFF) - 4096;
    }
}
