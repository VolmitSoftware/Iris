package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.testsupport.TestPassage;
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

public class PassageRasterizerTest {
    private static final int SEA = 40;
    private static final String FLUID = HydrologyCaveCell.SEA_FLUID;
    private static final int CAVE_CLEARANCE = 2;

    @Test
    public void waterLevelIsSeaLevelEverywhere() {
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(passage(1L, 15f)), order(false));
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
        List<TestPassage> passages = List.of(passage(1L, 15f), passage(2L, 30f));
        Map<Long, HydrologyCaveCell> cells = rasterize(passages, order(false));
        int checked = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            if (entry.getValue().action() != HydrologyCaveAction.WET_SOURCE) {
                continue;
            }
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            for (int[] side : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
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
        List<TestPassage> passages = List.of(passage(1L, 15f));
        Map<Long, HydrologyCaveCell> cells = rasterize(passages, order(false));
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
    public void subterrainFluidWithinTwoBlocksOfPassageAirAboveSeaLevelIsSealed() {
        TestPassage passage = passage(1L, 15f);
        PassageRasterizer.SubterrainFluid flooded = (x, y, z) -> Math.floorMod(x + 2 * z + y, 5) != 0;
        Map<Long, HydrologyCaveCell> plain = rasterize(List.of(passage), order(false));
        for (Map.Entry<Long, HydrologyCaveCell> entry : plain.entrySet()) {
            assertTrue("without subterrain fluid nothing above sea level is sealed",
                    y(entry.getKey()) <= SEA || entry.getValue().action() == HydrologyCaveAction.DRY_AIR);
        }
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(passage), order(false), flooded);
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
                        assertNotNull("subterrain fluid at " + (x + dx) + "," + (y + dy) + "," + (z + dz) + " beside passage air at " + x + "," + y
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
            assertTrue("the seal at " + x + "," + y + "," + z + " lies within two blocks of passage air", nearAir(cells, x, y, z));
            sealed++;
        }
        assertTrue("checked " + checked, checked > 1000);
        assertTrue("sealed " + sealed, sealed > 200);
        assertEquals(cells, rasterize(List.of(passage), order(true), flooded));
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

    @Test(timeout = 20_000L)
    public void thinDiagonalPassagesSealTheirBedWithoutRunningAway() {
        float[] xs = {0f, 8f, 16f, 24f, 32f};
        float[] zs = {0f, 8f, 16f, 24f, 32f};
        float[] halfWidth = {0.6f, 0.6f, 0.6f, 0.6f, 0.6f};
        int[] floor = {SEA - 3, SEA - 3, SEA - 3, SEA - 3, SEA - 3};
        int[] ceiling = {SEA + 4, SEA + 4, SEA + 4, SEA + 4, SEA + 4};
        TestPassage thin = new TestPassage(9L, xs, zs, halfWidth, SEA, floor, ceiling, 32, 32, 2);
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
    public void passageEndingAtAnOldChunkIsSealed() {
        TestPassage passage = passage(1L, 15f);
        PassageRasterizer.HistoricalChunks old = (chunkX, chunkZ) -> chunkX == 2;
        Map<Long, HydrologyCaveCell> open = rasterize(List.of(passage), order(false), PassageRasterizer.SubterrainFluid.NOWHERE,
                PassageRasterizer.HistoricalChunks.NONE);
        Map<Long, HydrologyCaveCell> cells = rasterize(List.of(passage), newChunks(order(false), old), PassageRasterizer.SubterrainFluid.NOWHERE,
                old);
        int[] column = new int[2];
        int seamColumns = 0;
        for (int z = -16; z <= 63; z++) {
            for (int x = -32; x <= 79; x++) {
                if (old.at(x >> 4, z >> 4) || !nearOldChunk(old, x, z)) {
                    continue;
                }
                if (!passage.column(x, z, column)) {
                    continue;
                }
                assertEquals("without old chunks the seam column " + x + "," + z + " holds water", HydrologyCaveAction.WET_SOURCE,
                        open.get(key(x, passage.waterLevel(), z)).action());
                for (int y = column[0] - PassageRasterizer.SHELL; y <= column[1]; y++) {
                    HydrologyCaveCell sealed = cells.get(key(x, y, z));
                    assertNotNull("no seal at " + x + "," + y + "," + z + " beside the old chunk", sealed);
                    assertEquals("seal at " + x + "," + y + "," + z + " beside the old chunk", HydrologyCaveAction.SEAL_GUARD, sealed.action());
                }
                seamColumns++;
            }
        }
        int wet = 0;
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            int x = x(entry.getKey());
            int y = y(entry.getKey());
            int z = z(entry.getKey());
            assertFalse("old chunk " + (x >> 4) + "," + (z >> 4) + " was written", old.at(x >> 4, z >> 4));
            HydrologyCaveAction action = entry.getValue().action();
            if (action == HydrologyCaveAction.SEAL_GUARD) {
                continue;
            }
            assertFalse(action + " at " + x + "," + y + "," + z + " lies within two blocks of an old chunk", nearOldChunk(old, x, z));
            if (action != HydrologyCaveAction.WET_SOURCE) {
                continue;
            }
            for (int[] side : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
                HydrologyCaveCell neighbour = cells.get(key(x + side[0], y + side[1], z + side[2]));
                assertNotNull("water at " + x + "," + y + "," + z + " has an open side " + side[0] + "," + side[1] + "," + side[2], neighbour);
                assertTrue(neighbour.action() == HydrologyCaveAction.WET_SOURCE || neighbour.action() == HydrologyCaveAction.SEAL_GUARD);
            }
            wet++;
        }
        assertTrue("covered seam columns " + seamColumns, seamColumns > 10);
        assertTrue("water cells away from the old chunks " + wet, wet > 300);
        assertEquals(cells, rasterize(List.of(passage), newChunks(order(true), old), PassageRasterizer.SubterrainFluid.NOWHERE, old));
    }

    private static boolean nearOldChunk(PassageRasterizer.HistoricalChunks old, int x, int z) {
        for (int dz = -PassageRasterizer.SHELL; dz <= PassageRasterizer.SHELL; dz++) {
            for (int dx = -PassageRasterizer.SHELL; dx <= PassageRasterizer.SHELL; dx++) {
                if (old.at((x + dx) >> 4, (z + dz) >> 4)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<int[]> newChunks(List<int[]> chunks, PassageRasterizer.HistoricalChunks old) {
        List<int[]> fresh = new ArrayList<>();
        for (int[] chunk : chunks) {
            if (!old.at(chunk[0], chunk[1])) {
                fresh.add(chunk);
            }
        }
        return fresh;
    }

    @Test
    public void chunkOrderAndPassageOrderDoNotChangeTheCells() {
        List<TestPassage> passages = List.of(passage(1L, 15f), passage(2L, 22f));
        Map<Long, HydrologyCaveCell> forward = rasterize(passages, order(false));
        Map<Long, HydrologyCaveCell> reverse = rasterize(List.of(passages.get(1), passages.get(0)), order(true));
        assertEquals(forward, reverse);
        assertFalse(forward.isEmpty());
    }

    @Test
    public void chunksWriteOnlyTheirOwnColumns() {
        TestPassage passage = passage(1L, 15f);
        Map<Long, HydrologyCaveCell> cells = new TreeMap<>();
        MantleWriter writer = writer(cells, 1, 0);
        new PassageRasterizer().write(passage, 1, 0, writer, FLUID, PassageRasterizer.SubterrainFluid.NOWHERE,
                PassageRasterizer.HistoricalChunks.NONE);
        assertFalse(cells.isEmpty());
        for (long key : cells.keySet()) {
            assertEquals(1, x(key) >> 4);
            assertEquals(0, z(key) >> 4);
        }
        Map<Long, HydrologyCaveCell> far = new TreeMap<>();
        new PassageRasterizer().write(passage, 20, 20, writer(far, 20, 20), FLUID, PassageRasterizer.SubterrainFluid.NOWHERE,
                PassageRasterizer.HistoricalChunks.NONE);
        assertTrue(far.isEmpty());
        assertFalse(PassageRasterizer.touches(passage, 20, 20));
    }

    private static TestPassage passage(long id, float z) {
        float[] xs = {-8f, 0f, 8f, 16f, 24f, 32f, 40f, 48f};
        float[] zs = new float[xs.length];
        float[] halfWidth = new float[xs.length];
        int[] floor = new int[xs.length];
        int[] ceiling = new int[xs.length];
        for (int station = 0; station < xs.length; station++) {
            zs[station] = z + (station % 2 == 0 ? 0f : 3f);
            halfWidth[station] = 5f - station * 0.4f;
            floor[station] = SEA - 2 - station % 3;
            ceiling[station] = SEA + 4 + station % 4;
        }
        return new TestPassage(id, xs, zs, halfWidth, SEA, floor, ceiling, 48, (int) z, 7);
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

    private static Map<Long, HydrologyCaveCell> rasterize(List<TestPassage> passages, List<int[]> chunks) {
        return rasterize(passages, chunks, PassageRasterizer.SubterrainFluid.NOWHERE);
    }

    private static Map<Long, HydrologyCaveCell> rasterize(List<TestPassage> passages, List<int[]> chunks, PassageRasterizer.SubterrainFluid subterrain) {
        return rasterize(passages, chunks, subterrain, PassageRasterizer.HistoricalChunks.NONE);
    }

    private static Map<Long, HydrologyCaveCell> rasterize(List<TestPassage> passages, List<int[]> chunks, PassageRasterizer.SubterrainFluid subterrain,
                                                          PassageRasterizer.HistoricalChunks old) {
        Map<Long, HydrologyCaveCell> cells = new TreeMap<>();
        PassageRasterizer rasterizer = new PassageRasterizer();
        for (int[] chunk : chunks) {
            MantleWriter writer = writer(cells, chunk[0], chunk[1]);
            for (TestPassage passage : passages) {
                rasterizer.write(passage, chunk[0], chunk[1], writer, FLUID, subterrain, old);
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
