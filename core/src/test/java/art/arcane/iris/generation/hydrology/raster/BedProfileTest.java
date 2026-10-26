package art.arcane.iris.generation.hydrology.raster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BedProfileTest {
    @Test
    public void fineDetailHasBoundedAmplitudeAndAdjacentSteps() {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (long seed : new long[]{0L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (int x = -48; x <= 48; x++) {
                for (int z = -48; z <= 48; z++) {
                    double detail = BedProfile.detail(seed, x, z);
                    minimum = Math.min(minimum, detail);
                    maximum = Math.max(maximum, detail);
                    assertTrue(Math.abs(detail) <= 0.35D);
                    assertTrue(Math.abs(detail - BedProfile.detail(seed, x + 1, z)) <= 0.35D);
                    assertTrue(Math.abs(detail - BedProfile.detail(seed, x, z + 1)) <= 0.35D);
                }
            }
        }
        assertTrue(minimum < -0.3D);
        assertTrue(maximum > 0.3D);
    }

    @Test
    public void fineDetailBreaksUpLevelContoursWithoutTallFaces() {
        int changedChannels = 0;
        int changedPonds = 0;
        for (int x = -64; x <= 64; x++) {
            for (int z = -32; z <= 32; z++) {
                double detail = BedProfile.detail(1337L, x, z);
                int channel = BedProfile.channelDepth(6D, 0.5D, 0D, detail);
                int pond = BedProfile.pondDepth(8, 0.5D, 0D, detail);
                changedChannels += channel != BedProfile.channelDepth(6D, 0.5D, 0D, 0D) ? 1 : 0;
                changedPonds += pond != BedProfile.pondDepth(8, 0.5D, 0D, 0D) ? 1 : 0;
                assertTrue(channel == 4 || channel == 5);
                assertTrue(pond == 4 || pond == 5);
            }
        }
        assertTrue(changedChannels > 1000);
        assertTrue(changedPonds > 1000);
    }

    @Test
    public void depthLimitsAndShallowEdgesSurviveFineDetail() {
        for (int maximum : new int[]{1, 2, 6, 8, 16, 64}) {
            for (double variation : new double[]{-1D, -0.5D, 0D, 0.5D, 1D}) {
                for (double detail : new double[]{-0.35D, 0D, 0.35D}) {
                    assertEquals(1, BedProfile.channelDepth(maximum, 1D, variation, detail));
                    assertEquals(1, BedProfile.pondDepth(maximum, 0D, variation, detail));
                    for (int step = 0; step <= 100; step++) {
                        double position = step / 100D;
                        int channel = BedProfile.channelDepth(maximum, position, variation, detail);
                        int pond = BedProfile.pondDepth(maximum, position, variation, detail);
                        assertTrue(channel >= 1 && channel <= maximum);
                        assertTrue(pond >= 1 && pond <= maximum);
                        assertTrue(Math.abs(channel - BedProfile.channelDepth(maximum, position, variation, 0D)) <= 1);
                        assertTrue(Math.abs(pond - BedProfile.pondDepth(maximum, position, variation, 0D)) <= 1);
                    }
                }
            }
        }
    }

    @Test
    public void detailUsesWorldCoordinatesAcrossNegativeChunkBoundaries() {
        double[][] expected = new double[66][66];
        for (int x = -33; x <= 32; x++) {
            for (int z = -33; z <= 32; z++) {
                expected[x + 33][z + 33] = BedProfile.detail(1337L, x, z);
            }
        }
        int differentSeeds = 0;
        for (int chunkX = 2; chunkX >= -3; chunkX--) {
            for (int chunkZ = 2; chunkZ >= -3; chunkZ--) {
                for (int localX = 16; localX >= -1; localX--) {
                    for (int localZ = 16; localZ >= -1; localZ--) {
                        int x = (chunkX << 4) + localX;
                        int z = (chunkZ << 4) + localZ;
                        if (x < -33 || x > 32 || z < -33 || z > 32) {
                            continue;
                        }
                        double detail = BedProfile.detail(1337L, x, z);
                        assertEquals(expected[x + 33][z + 33], detail, 0D);
                        differentSeeds += detail != BedProfile.detail(1338L, x, z) ? 1 : 0;
                    }
                }
            }
        }
        assertTrue(differentSeeds > 4000);
    }
}
