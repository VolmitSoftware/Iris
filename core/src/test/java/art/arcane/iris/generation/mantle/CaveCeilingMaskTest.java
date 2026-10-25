package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.testsupport.WaterScene;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CaveCeilingMaskTest {
    private static final int UNMASKED = CaveCeilingMask.UNMASKED;

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
