package art.arcane.iris.generation.hydrology.underground;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GrottoRasterizerTest {
    private static final int SEA = GrottoShapeTest.SEA;

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Test
    public void cellsMatchTheShapeInEveryChunk() {
        Grotto grotto = GrottoShapeTest.winding(11L, 0.6f);
        GrottoShape shape = new GrottoShape(grotto);
        int[] bounds = grotto.bounds();
        long wet = 0L;
        long dry = 0L;
        for (int chunkZ = bounds[1] >> 4; chunkZ <= bounds[3] >> 4; chunkZ++) {
            for (int chunkX = bounds[0] >> 4; chunkX <= bounds[2] >> 4; chunkX++) {
                GrottoRasterizer.Cells cells = GrottoRasterizer.cells(grotto, chunkX, chunkZ, GrottoRasterizer.Exclusion.NOWHERE);
                if (cells == null) {
                    continue;
                }
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int y = cells.minY(); y <= cells.maxY(); y++) {
                            int x = (chunkX << 4) + lx;
                            int z = (chunkZ << 4) + lz;
                            byte kind = cells.at(lx, y, lz);
                            boolean inside = shape.inside(x, y, z);
                            if (inside) {
                                assertEquals(x + "," + y + "," + z, y <= SEA ? GrottoRasterizer.WET : GrottoRasterizer.DRY, kind);
                            } else {
                                assertTrue(x + "," + y + "," + z + " kind " + kind, kind == GrottoRasterizer.NONE || kind == GrottoRasterizer.GUARD);
                            }
                            wet += kind == GrottoRasterizer.WET ? 1L : 0L;
                            dry += kind == GrottoRasterizer.DRY ? 1L : 0L;
                        }
                    }
                }
            }
        }
        assertTrue("wet " + wet, wet > 2000L);
        assertTrue("dry " + dry, dry > 2000L);
    }

    @Test
    public void everyWetCellIsWalledByGuardAcrossChunkLines() {
        Grotto grotto = GrottoShapeTest.winding(12L, 0.6f);
        int[] bounds = grotto.bounds();
        Map<Long, GrottoRasterizer.Cells> chunks = new HashMap<>();
        for (int chunkZ = (bounds[1] >> 4) - 1; chunkZ <= (bounds[3] >> 4) + 1; chunkZ++) {
            for (int chunkX = (bounds[0] >> 4) - 1; chunkX <= (bounds[2] >> 4) + 1; chunkX++) {
                chunks.put(key(chunkX, chunkZ), GrottoRasterizer.cells(grotto, chunkX, chunkZ, GrottoRasterizer.Exclusion.NOWHERE));
            }
        }
        long checked = 0L;
        for (Map.Entry<Long, GrottoRasterizer.Cells> entry : chunks.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) (long) entry.getKey();
            GrottoRasterizer.Cells cells = entry.getValue();
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = cells.minY(); y <= SEA; y++) {
                        if (cells.at(lx, y, lz) != GrottoRasterizer.WET) {
                            continue;
                        }
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = x + side[0];
                            int nz = z + side[1];
                            GrottoRasterizer.Cells other = chunks.get(key(nx >> 4, nz >> 4));
                            assertNotNull("chunk beside " + nx + "," + nz, other);
                            byte beside = other.at(nx & 15, y, nz & 15);
                            assertTrue(nx + "," + y + "," + nz + " is " + beside, beside == GrottoRasterizer.WET || beside == GrottoRasterizer.GUARD);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue("checked " + checked, checked > 2000L);
    }

    @Test
    public void guardStaysAtOrBelowTheWaterSoItCannotFillOpenAir() {
        Grotto grotto = GrottoShapeTest.winding(13L, 0.6f);
        int[] bounds = grotto.bounds();
        boolean any = false;
        for (int chunkZ = bounds[1] >> 4; chunkZ <= bounds[3] >> 4; chunkZ++) {
            for (int chunkX = bounds[0] >> 4; chunkX <= bounds[2] >> 4; chunkX++) {
                GrottoRasterizer.Cells cells = GrottoRasterizer.cells(grotto, chunkX, chunkZ, GrottoRasterizer.Exclusion.NOWHERE);
                if (cells == null) {
                    continue;
                }
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int y = cells.minY(); y <= cells.maxY(); y++) {
                            if (cells.at(lx, y, lz) == GrottoRasterizer.GUARD) {
                                any = true;
                                assertTrue("guard at " + y, y <= SEA);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(any);
    }

    @Test
    public void excludedCellsStayRockAndNextWaterIsGuarded() {
        Grotto grotto = GrottoShapeTest.winding(14L, 0.6f);
        int wallX = (int) grotto.xs()[24];
        GrottoRasterizer.Exclusion beyond = (x, y, z) -> x >= wallX;
        int chunkX = wallX >> 4;
        int chunkZ = ((int) grotto.zs()[24]) >> 4;
        GrottoRasterizer.Cells cells = GrottoRasterizer.cells(grotto, chunkX, chunkZ, beyond);
        assertNotNull(cells);
        GrottoShape shape = new GrottoShape(grotto);
        boolean guarded = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (chunkX << 4) + lx;
                int z = (chunkZ << 4) + lz;
                for (int y = cells.minY(); y <= cells.maxY(); y++) {
                    byte kind = cells.at(lx, y, lz);
                    if (x >= wallX) {
                        assertFalse(kind == GrottoRasterizer.WET || kind == GrottoRasterizer.DRY);
                        guarded |= kind == GrottoRasterizer.GUARD && shape.inside(x, y, z);
                    }
                }
            }
        }
        assertTrue(guarded);
    }

    @Test
    public void chunksFarFromTheGrottoHoldNothing() {
        Grotto grotto = GrottoShapeTest.winding(15L, 0.6f);
        assertNull(GrottoRasterizer.cells(grotto, 1000, 1000, GrottoRasterizer.Exclusion.NOWHERE));
    }
}
