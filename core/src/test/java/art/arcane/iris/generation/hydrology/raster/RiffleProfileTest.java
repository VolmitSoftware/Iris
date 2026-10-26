package art.arcane.iris.generation.hydrology.raster;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RiffleProfileTest {
    private static final int DRY = Integer.MIN_VALUE;

    @Test
    public void flatPoolsRetainEveryBedDepth() {
        IntBinaryOperator heads = (x, z) -> 100;
        Surface surface = new Surface(heads);
        for (int x = -32; x <= 32; x++) {
            for (int z = -16; z <= 16; z++) {
                int bed = 64 + Math.floorMod(x + z, 30);
                assertEquals(bed, surface.bed(x, z, bed));
            }
        }
    }

    @Test
    public void twoBlockDropsHaveGradedApproachesAndNoTallSubmergedFace() {
        Surface surface = new Surface((x, z) -> x < 0 ? 100 : 98);
        for (int z = -24; z <= 24; z++) {
            assertEquals(99, surface.bed(-1, z, 92));
            assertEquals(97, surface.bed(0, z, 92));
            for (int x = -24; x <= 24; x++) {
                int bed = surface.bed(x, z, 92);
                int next = surface.bed(x + 1, z, 92);
                assertTrue(x + "," + z + " bed " + bed, bed < (x < 0 ? 100 : 98));
                int headDrop = x == -1 ? 2 : 0;
                assertTrue(x + "," + z + " face " + bed + " to " + next, Math.abs(bed - next) <= Math.max(1, headDrop));
            }
            assertEquals(92, surface.bed(-18, z, 92));
            assertEquals(92, surface.bed(17, z, 92));
        }
    }

    @Test
    public void deeperBedsFadeWithoutACliffAtTheRadiusBoundary() {
        Surface surface = new Surface((x, z) -> x < 0 ? 100 : 98);
        for (int z = -8; z <= 8; z++) {
            for (int x : new int[]{-18, -17, -16, 14, 15, 16, 17}) {
                assertTrue(x + "," + z, Math.abs(surface.bed(x, z, 64) - surface.bed(x + 1, z, 64)) <= 1);
            }
            assertEquals(64, surface.bed(-18, z, 64));
            assertEquals(64, surface.bed(17, z, 64));
            assertEquals(99, surface.bed(-1, z, 64));
        }
    }

    @Test
    public void lowerPoolsRecoverTheirDepthWithoutAWideFlatShelf() {
        Surface surface = new Surface((x, z) -> x < 0 ? 104 : 100);
        for (int z = -8; z <= 8; z++) {
            assertEquals(103, surface.bed(-1, z, 96));
            assertEquals(99, surface.bed(0, z, 96));
            assertTrue(surface.bed(3, z, 96) < 99);
            assertEquals(96, surface.bed(6, z, 96));
            assertTrue(surface.bed(-6, z, 96) > 96);
        }
    }

    @Test
    public void nearbyDisconnectedPoolsKeepTheirDepth() {
        Surface surface = new Surface((x, z) -> z == 0 ? DRY : z > 0 ? 100 : x < 0 ? 100 : 98);
        for (int x = -24; x <= 24; x++) {
            assertEquals(92, surface.bed(x, 1, 92));
        }
        assertEquals(99, surface.bed(-1, -1, 92));
    }

    @Test
    public void aNearerLowerDropNeverCutsOffAHigherRiffle() {
        Surface mixed = new Surface((x, z) -> x < 0 ? 104 : z < 0 ? 102 : 100);
        Surface high = new Surface((x, z) -> x < 0 ? 104 : 100);
        Surface low = new Surface((x, z) -> z < 0 ? 102 : 100);
        for (int x = 0; x <= 20; x++) {
            for (int z = 0; z <= 20; z++) {
                int bed = mixed.bed(x, z, 94);
                assertTrue(x + "," + z + " lost the higher influence", bed >= high.bed(x, z, 94));
                assertTrue(x + "," + z + " lost the lower influence", bed >= low.bed(x, z, 94));
                assertTrue(x + "," + z + " east face", Math.abs(bed - mixed.bed(x + 1, z, 94)) <= 1);
                assertTrue(x + "," + z + " south face", Math.abs(bed - mixed.bed(x, z + 1, 94)) <= 1);
            }
        }
    }

    @Test
    public void diagonalJoinsHaveTheSameProfileAcrossChunkBoundaries() {
        IntBinaryOperator heads = (x, z) -> x + z < -7 ? 100 : 98;
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                RiffleProfile first = new RiffleProfile(chunkX, chunkZ, heads);
                RiffleProfile east = new RiffleProfile(chunkX + 1, chunkZ, heads);
                RiffleProfile south = new RiffleProfile(chunkX, chunkZ + 1, heads);
                for (int edge = 0; edge < 16; edge++) {
                    int x = (chunkX + 1) << 4;
                    int z = (chunkZ << 4) + edge;
                    double variation = BedProfile.variation(1337L, x, z);
                    assertEquals(first.bed(16, edge, 92, heads.applyAsInt(x, z), variation),
                            east.bed(0, edge, 92, heads.applyAsInt(x, z), variation));
                    x = (chunkX << 4) + edge;
                    z = (chunkZ + 1) << 4;
                    variation = BedProfile.variation(1337L, x, z);
                    assertEquals(first.bed(edge, 16, 92, heads.applyAsInt(x, z), variation),
                            south.bed(edge, 0, 92, heads.applyAsInt(x, z), variation));
                }
            }
        }
    }

    private static final class Surface {
        private final IntBinaryOperator heads;
        private final Map<Long, RiffleProfile> chunks = new HashMap<>();

        private Surface(IntBinaryOperator heads) {
            this.heads = heads;
        }

        private int bed(int x, int z, int original) {
            int cx = x >> 4;
            int cz = z >> 4;
            long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
            RiffleProfile profile = chunks.computeIfAbsent(key, ignored -> new RiffleProfile(cx, cz, heads));
            return profile.bed(x & 15, z & 15, original, heads.applyAsInt(x, z), BedProfile.variation(1337L, x, z));
        }
    }
}
