package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CaveCeilingMaskTest {
    private static final int UNMASKED = CaveCeilingMask.UNMASKED;
    private static final int SEA = 63;
    private static final int ROOF_CLEARANCE = 6;
    private static final int NO_ROOF = Integer.MIN_VALUE;

    @Test
    public void cavesStopTwoBlocksBelowEveryBedAndColumnsWithinTwoBlocksTakeTheLowestCeiling() {
        WaterScene scene = scene(new IrisHydrology());
        int[] field = new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD];
        int[] ceilings = new int[256];
        int wet = 0;
        int extended = 0;
        for (int chunkX = -2; chunkX <= 3; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 2; chunkZ++) {
                boolean masked = CaveCeilingMask.fill(chunkX, chunkZ, scene::raster, field, ceilings);
                boolean expectedMasked = false;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        int expected = lowestCeilingWithinTwo(scene, x, z);
                        int ceiling = ceilings[PowerOfTwoCoordinates.packLocal16(lx, lz)];
                        if (scene.raster(chunkX, chunkZ).empty()) {
                            assertEquals("an empty raster leaves " + x + "," + z + " unmasked", UNMASKED, expected);
                            continue;
                        }
                        assertEquals("cave ceiling at " + x + "," + z, expected, ceiling);
                        expectedMasked |= expected != UNMASKED;
                        if (scene.water(x, z)) {
                            wet++;
                            assertTrue("wet column " + x + "," + z + " keeps its bed and two solid blocks under it",
                                    ceiling <= scene.terrain(x, z) - 3);
                        } else if (expected != UNMASKED) {
                            extended++;
                        }
                    }
                }
                assertEquals("mask flag for chunk " + chunkX + "," + chunkZ, expectedMasked, masked);
            }
        }
        assertTrue("wet columns " + wet, wet > 200);
        assertTrue("dry columns masked within two blocks " + extended, extended > 100);
    }

    @Test
    public void riverlessChunksReadOnlyTheirOwnRaster() {
        WaterScene scene = scene(new IrisHydrology());
        List<String> reads = new ArrayList<>();
        boolean masked = CaveCeilingMask.fill(20, 20, (chunkX, chunkZ) -> {
            reads.add(chunkX + "," + chunkZ);
            return scene.raster(chunkX, chunkZ);
        }, new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD], new int[256]);

        assertFalse(masked);
        assertEquals(List.of("20,20"), reads);
    }

    @Test
    public void provisionalRastersFailTheChunk() {
        WaterScene scene = scene(new IrisHydrology());
        int[] field = new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD];
        assertThrows(IllegalStateException.class, () -> CaveCeilingMask.fill(0, 0,
                (chunkX, chunkZ) -> ChunkWaterRaster.PROVISIONAL, field, new int[256]));
        assertThrows(IllegalStateException.class, () -> CaveCeilingMask.fill(0, 0,
                (chunkX, chunkZ) -> chunkX == 1 && chunkZ == 1 ? ChunkWaterRaster.PROVISIONAL : scene.raster(chunkX, chunkZ),
                field, new int[256]));
    }

    @Test
    public void disabledCaveMaskLeavesEveryColumnOpen() {
        IrisHydrology config = new IrisHydrology();
        config.getDeformation().setCaveMask(false);
        WaterScene scene = scene(config);
        int[] field = new int[CaveCeilingMask.FIELD * CaveCeilingMask.FIELD];
        int[] ceilings = new int[256];
        for (int chunkX = -2; chunkX <= 3; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 2; chunkZ++) {
                assertFalse(CaveCeilingMask.fill(chunkX, chunkZ, scene::raster, field, ceilings));
            }
        }
    }

    @Test
    public void cavesStayOutOfInletHeadroom() {
        SeaInlet inlet = inlet();
        int[] bounds = SeaInletRasterizer.bounds(inlet);
        long[] bands = new long[256];
        int[] column = new int[2];
        int covered = 0;
        int around = 0;
        for (int chunkZ = (bounds[1] >> 4) - 1; chunkZ <= (bounds[3] >> 4) + 1; chunkZ++) {
            for (int chunkX = (bounds[0] >> 4) - 1; chunkX <= (bounds[2] >> 4) + 1; chunkX++) {
                boolean masked = CaveCeilingMask.fillHeadroom(chunkX, chunkZ, List.of(inlet), ROOF_CLEARANCE, bands);
                long[] headroom = masked ? bands : null;
                boolean expectedMasked = false;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int x = (chunkX << 4) + lx;
                        int z = (chunkZ << 4) + lz;
                        int index = PowerOfTwoCoordinates.packLocal16(lx, lz);
                        int top = highestRoofWithinTwo(inlet, x, z);
                        if (top == NO_ROOF) {
                            for (int y = 0; y <= SEA + 64; y++) {
                                assertFalse("caves stay possible at " + x + "," + y + "," + z + " away from the inlet",
                                        CaveCeilingMask.masked(null, headroom, index, y));
                            }
                            continue;
                        }
                        expectedMasked = true;
                        for (int y = inlet.waterLevel() + 1; y <= top; y++) {
                            assertTrue("caves stay out of the inlet headroom and roof at " + x + "," + y + "," + z,
                                    CaveCeilingMask.masked(null, headroom, index, y));
                        }
                        assertFalse("the seal holds caves at the water level " + x + "," + z,
                                CaveCeilingMask.masked(null, headroom, index, inlet.waterLevel()));
                        assertFalse("caves stay possible above the inlet roof at " + x + "," + z,
                                CaveCeilingMask.masked(null, headroom, index, top + 1));
                        if (SeaInletRasterizer.column(inlet, x, z, column) && column[1] != SeaInlet.OPEN) {
                            covered++;
                        } else {
                            around++;
                        }
                    }
                }
                assertEquals("headroom flag for chunk " + chunkX + "," + chunkZ, expectedMasked, masked);
            }
        }
        assertTrue("covered columns " + covered, covered > 300);
        assertTrue("columns around the passage " + around, around > 100);
        assertFalse(CaveCeilingMask.fillHeadroom(0, 0, List.of(), ROOF_CLEARANCE, bands));
    }

    @Test
    public void headroomJoinsTheWaterCeilings() {
        int[] ceilings = new int[256];
        Arrays.fill(ceilings, UNMASKED);
        ceilings[3] = 40;
        long[] bands = new long[256];
        assertTrue(CaveCeilingMask.fillHeadroom(0, 0, List.of(inlet()), ROOF_CLEARANCE, bands));
        int index = PowerOfTwoCoordinates.packLocal16(12, 6);
        assertTrue(CaveCeilingMask.masked(ceilings, null, 3, 41));
        assertFalse(CaveCeilingMask.masked(ceilings, null, 3, 40));
        assertFalse(CaveCeilingMask.masked(ceilings, bands, index, SEA));
        assertTrue(CaveCeilingMask.masked(ceilings, bands, index, SEA + 1));
        assertFalse(CaveCeilingMask.masked(null, null, index, SEA + 1));
    }

    private static SeaInlet inlet() {
        float[] xs = {-8f, 0f, 8f, 16f, 24f, 32f, 40f, 48f};
        float[] zs = new float[xs.length];
        float[] halfWidth = new float[xs.length];
        int[] floor = new int[xs.length];
        int[] ceiling = new int[xs.length];
        for (int station = 0; station < xs.length; station++) {
            zs[station] = 6f + (station % 2 == 0 ? 0f : 3f);
            halfWidth[station] = 5f - station * 0.4f;
            floor[station] = SEA - 2 - station % 3;
            ceiling[station] = station < 2 ? SeaInlet.OPEN : SEA + 4 + station % 4;
        }
        return new SeaInlet(1L, xs, zs, halfWidth, SEA, floor, ceiling, 48, 6, 7);
    }

    private static int highestRoofWithinTwo(SeaInlet inlet, int x, int z) {
        int top = NO_ROOF;
        int[] column = new int[2];
        for (int dz = -CaveCeilingMask.CONTAINMENT; dz <= CaveCeilingMask.CONTAINMENT; dz++) {
            for (int dx = -CaveCeilingMask.CONTAINMENT; dx <= CaveCeilingMask.CONTAINMENT; dx++) {
                if (SeaInletRasterizer.column(inlet, x + dx, z + dz, column) && column[1] != SeaInlet.OPEN) {
                    top = Math.max(top, column[1] + ROOF_CLEARANCE);
                }
            }
        }
        return top;
    }

    static WaterScene scene(IrisHydrology config) {
        IntBinaryOperator natural = (x, z) -> 80;
        return new WaterScene(config, natural)
                .reach(WaterScene.line(51L, WaterScene.stream(), -30, 15, 60, 15, (x, z) -> 79))
                .pond(WaterScene.pond(52L, 79, 16, 32, 7));
    }

    private static int lowestCeilingWithinTwo(WaterScene scene, int x, int z) {
        int lowest = UNMASKED;
        for (int dz = -CaveCeilingMask.CONTAINMENT; dz <= CaveCeilingMask.CONTAINMENT; dz++) {
            for (int dx = -CaveCeilingMask.CONTAINMENT; dx <= CaveCeilingMask.CONTAINMENT; dx++) {
                int ceiling = scene.caveCeiling(x + dx, z + dz);
                lowest = Math.min(lowest, ceiling == UNMASKED ? UNMASKED : ceiling - CaveCeilingMask.ROOF);
            }
        }
        return lowest;
    }
}
