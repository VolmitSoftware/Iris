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
    public void fallsKeepADeepPlungePoolBelowTheCurtain() {
        Surface surface = new Surface((x, z) -> x < 0 ? 110 : 98);
        for (int z = -8; z <= 8; z++) {
            for (int original : new int[]{92, 96}) {
                String bed = original + " bed at z " + z;
                assertEquals("lip sill for the " + bed, 109, surface.bed(-1, z, original));
                assertTrue("plunge pool at the foot for the " + bed, surface.bed(0, z, original) <= 95);
                assertTrue("deepest at the foot for the " + bed, surface.bed(0, z, original) <= surface.bed(6, z, original));
                for (int x = 0; x <= 20; x++) {
                    int here = surface.bed(x, z, original);
                    assertTrue("pool at " + x + " stays below the " + bed, here <= original);
                    assertTrue("downstream face at " + x + " in the " + bed, Math.abs(here - surface.bed(x + 1, z, original)) <= 2);
                }
                assertEquals(original, surface.bed(17, z, original));
            }
            assertTrue("the bowl cuts below a shallow bed at z " + z, surface.bed(0, z, 96) <= 94);
        }
    }

    @Test
    public void plungePoolsTailOutBeforeTheNextStep() {
        Surface pool = new Surface((x, z) -> x < 0 ? 110 : 98);
        for (int brink : new int[]{5, 2, 1}) {
            int next = brink + 1;
            Surface stepped = new Surface((x, z) -> x < 0 ? 110 : x < next ? 98 : 96);
            for (int z = -8; z <= 8; z++) {
                for (int original : new int[]{92, 96}) {
                    String bed = original + " bed at z " + z + " with the next step's brink at x " + brink;
                    assertEquals("lip sill for the " + bed, 109, stepped.bed(-1, z, original));
                    if (original == 96) {
                        assertEquals("plunge floor under the fall for the " + bed, pool.bed(0, z, original), stepped.bed(0, z, original));
                    }
                    assertTrue("plunge pool at the foot for the " + bed, stepped.bed(0, z, original) <= 95);
                    assertEquals("1-block water over the brink for the " + bed, 97, stepped.bed(brink, z, original));
                    for (int x = 0; x < brink; x++) {
                        int here = stepped.bed(x, z, original);
                        assertTrue("deepest at the foot, at " + x + " for the " + bed, stepped.bed(0, z, original) <= here);
                        assertTrue("tailout rises toward the brink at " + x + " for the " + bed, here <= stepped.bed(x + 1, z, original));
                        if (brink >= 5) {
                            assertTrue("tailout face at " + x + " for the " + bed, stepped.bed(x + 1, z, original) - here <= 2);
                        }
                    }
                }
            }
        }
        Surface wedged = new Surface((x, z) -> x < 0 ? 110 : x < 1 ? 98 : 96);
        for (int z = -8; z <= 8; z++) {
            assertEquals("a foot that is also the next step's brink keeps the sill at z " + z, 97, wedged.bed(0, z, 92));
        }
    }

    @Test
    public void plungeFieldsMatchAcrossChunkBorders() {
        IntBinaryOperator heads = (x, z) -> x + z < -7 ? 110 : 98;
        Surface surface = new Surface(heads);
        for (int x = -24; x <= 16; x++) {
            assertTrue("the foot at " + x + "," + (-7 - x) + " is carved", surface.bed(x, -7 - x, 96) < 96);
        }
        assertChunkBordersMatch(heads);
    }

    @Test
    public void tailoutsMatchAcrossChunkBordersWithADiagonalNextStep() {
        IntBinaryOperator heads = (x, z) -> x + z < -7 ? 110 : x + z < -1 ? 98 : 96;
        Surface stepped = new Surface(heads);
        Surface pool = new Surface((x, z) -> x + z < -7 ? 110 : 98);
        for (int x = -24; x <= 16; x++) {
            int z = -2 - x;
            assertEquals("1-block water over the brink at " + x + "," + z, 97, stepped.bed(x, z, 92));
            assertTrue("the brink at " + x + "," + z + " would lie in the pool without the next step", pool.bed(x, z, 92) < 97);
            assertTrue("the foot at " + x + "," + (-7 - x) + " is carved", stepped.bed(x, -7 - x, 96) < 96);
        }
        assertChunkBordersMatch(heads);
    }

    @Test
    public void tailoutsMatchAcrossChunkBordersWithAJoggedNextStep() {
        IntBinaryOperator heads = (x, z) -> x < -3 ? 110 : x < (z < 0 ? 2 : 0) ? 98 : 96;
        Surface stepped = new Surface(heads);
        Surface pool = new Surface((x, z) -> x < -3 ? 110 : 98);
        for (int z = -8; z <= 8; z++) {
            int brink = z < 0 ? 1 : -1;
            assertEquals("1-block water over the brink at " + brink + "," + z, 97, stepped.bed(brink, z, 92));
            assertTrue("the brink at " + brink + "," + z + " would lie in the pool without the next step", pool.bed(brink, z, 92) < 97);
            assertTrue("the foot at z " + z + " is carved", stepped.bed(-3, z, 96) < 96);
        }
        assertChunkBordersMatch(heads);
    }

    @Test
    public void plungeSkipsStandingWaterAndMouths() {
        IntBinaryOperator heads = (x, z) -> x < 0 ? 110 : 98;
        IntBinaryOperator features = (x, z) -> x < 0 ? 1 : 2;
        for (WaterRole standing : new WaterRole[]{WaterRole.POND, WaterRole.MOUTH, WaterRole.INLET}) {
            Surface surface = new Surface(heads, water(heads, features, (x, z) -> x < 0 ? WaterRole.CHANNEL : standing));
            for (int z = -8; z <= 8; z++) {
                assertEquals(standing + " lip sill at z " + z, 109, surface.bed(-1, z, 92));
                for (int x = 0; x <= 20; x++) {
                    assertEquals(standing + " floor at " + x + "," + z, 92, surface.bed(x, z, 92));
                    assertEquals(standing + " shallow floor at " + x + "," + z, 96, surface.bed(x, z, 96));
                }
            }
        }
        Surface beside = new Surface(heads, water(heads, (x, z) -> x < 0 ? 1 : z < 0 ? 2 : 3,
                (x, z) -> x >= 0 && z >= 0 ? WaterRole.POND : WaterRole.CHANNEL));
        for (int z = -8; z <= 8; z++) {
            for (int x = 0; x <= 20; x++) {
                if (z >= 0) {
                    assertEquals("pond floor beside a carved channel foot at " + x + "," + z, 96, beside.bed(x, z, 96));
                }
            }
            if (z < 0) {
                assertTrue("channel foot beside the pond at z " + z, beside.bed(0, z, 96) < 96);
            }
        }
    }

    @Test
    public void receivingChannelKeepsItsBedBelowAJoiningCascade() {
        IntBinaryOperator heads = (x, z) -> x < 0 ? 104 : 100;
        Surface joining = new Surface(heads, water(heads, (x, z) -> x < 0 ? 2 : 1, (x, z) -> WaterRole.CHANNEL));
        for (int z = -8; z <= 8; z++) {
            assertEquals("the joining channel keeps its lip sill at z " + z, 103, joining.bed(-1, z, 96));
            assertTrue("the joining channel's approach rises toward its lip at z " + z, joining.bed(-6, z, 96) > 96);
            for (int x = 0; x <= 20; x++) {
                assertEquals("receiving bed at " + x + "," + z, 96, joining.bed(x, z, 96));
            }
        }
        Surface outlet = new Surface(heads, water(heads, (x, z) -> x < 0 ? 2 : 1, (x, z) -> x < 0 ? WaterRole.POND : WaterRole.CHANNEL));
        assertEquals("a channel below a pond's shore still rises toward the lip", 99, outlet.bed(0, 0, 96));
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
                RiffleProfile first = new RiffleProfile(chunkX, chunkZ, channel(heads));
                RiffleProfile east = new RiffleProfile(chunkX + 1, chunkZ, channel(heads));
                RiffleProfile south = new RiffleProfile(chunkX, chunkZ + 1, channel(heads));
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

    private static RiffleProfile.Water channel(IntBinaryOperator heads) {
        return water(heads, (x, z) -> 1, (x, z) -> WaterRole.CHANNEL);
    }

    private static RiffleProfile.Water water(IntBinaryOperator heads, IntBinaryOperator features, Roles roles) {
        return (x, z, sample) -> {
            int head = heads.applyAsInt(x, z);
            sample.set(head, features.applyAsInt(x, z), head == DRY ? WaterRole.NONE : roles.at(x, z));
        };
    }

    private interface Roles {
        WaterRole at(int x, int z);
    }

    private static void assertChunkBordersMatch(IntBinaryOperator heads) {
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                RiffleProfile first = new RiffleProfile(chunkX, chunkZ, channel(heads));
                RiffleProfile east = new RiffleProfile(chunkX + 1, chunkZ, channel(heads));
                RiffleProfile south = new RiffleProfile(chunkX, chunkZ + 1, channel(heads));
                for (int edge = 0; edge < 16; edge++) {
                    for (int original : new int[]{92, 96}) {
                        int x = (chunkX + 1) << 4;
                        int z = (chunkZ << 4) + edge;
                        double variation = BedProfile.variation(1337L, x, z);
                        assertEquals(first.bed(16, edge, original, heads.applyAsInt(x, z), variation),
                                east.bed(0, edge, original, heads.applyAsInt(x, z), variation));
                        x = (chunkX << 4) + edge;
                        z = (chunkZ + 1) << 4;
                        variation = BedProfile.variation(1337L, x, z);
                        assertEquals(first.bed(edge, 16, original, heads.applyAsInt(x, z), variation),
                                south.bed(edge, 0, original, heads.applyAsInt(x, z), variation));
                    }
                }
            }
        }
    }

    private static final class Surface {
        private final IntBinaryOperator heads;
        private final RiffleProfile.Water water;
        private final Map<Long, RiffleProfile> chunks = new HashMap<>();

        private Surface(IntBinaryOperator heads) {
            this(heads, channel(heads));
        }

        private Surface(IntBinaryOperator heads, RiffleProfile.Water water) {
            this.heads = heads;
            this.water = water;
        }

        private int bed(int x, int z, int original) {
            int cx = x >> 4;
            int cz = z >> 4;
            long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
            RiffleProfile profile = chunks.computeIfAbsent(key, ignored -> new RiffleProfile(cx, cz, water));
            return profile.bed(x & 15, z & 15, original, heads.applyAsInt(x, z), BedProfile.variation(1337L, x, z));
        }
    }
}
