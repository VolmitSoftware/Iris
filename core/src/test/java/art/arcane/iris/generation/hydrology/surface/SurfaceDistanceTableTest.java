package art.arcane.iris.generation.hydrology.surface;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SurfaceDistanceTableTest {
    @Test
    public void latticeResidualsReuseExactDistancesIncludingSignedZero() {
        SurfaceDistanceTable table = new SurfaceDistanceTable(32, StrictMath::hypot);
        for (int x = -66; x <= 66; x++) {
            for (int z = -66; z <= 66; z++) {
                equalBits(StrictMath.hypot(x / 2D, z / 2D), table.hypot(x / 2D, z / 2D));
            }
        }
        double[] special = {-0D, 0D, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.25D, 31.75D, 32.5D};
        for (double x : special) {
            for (double z : special) {
                equalBits(StrictMath.hypot(x, z), table.hypot(x, z));
            }
        }
    }

    @Test
    public void scalarProjectionMatchesAcrossDirectionsAndExtremeCoordinates() {
        SurfaceDistanceTable table = new SurfaceDistanceTable(64, StrictMath::hypot);
        int[] origins = {Integer.MIN_VALUE, Integer.MIN_VALUE + 64, -30000000, -1, 0,
                30000000, Integer.MAX_VALUE - 64, Integer.MAX_VALUE};
        for (int origin : origins) {
            for (int directionX = -1; directionX <= 1; directionX++) {
                for (int directionZ = -1; directionZ <= 1; directionZ++) {
                    SurfaceCenterline centerline = segment(origin, -origin, origin + directionX, -origin + directionZ);
                    for (int station = 0; station < 2; station++) {
                        for (int x = -34; x <= 34; x++) {
                            for (int z = -34; z <= 34; z++) {
                                double pointX = origin + x;
                                double pointZ = -origin + z;
                                equalBits(centerline.distanceToSegment(station, pointX, pointZ),
                                        centerline.distanceToSegment(station, pointX, pointZ, table));
                            }
                        }
                    }
                }
            }
        }
        SurfaceCenterline arbitrary = segment(-1700000000, 1500000000, 1900000000, -1600000000);
        for (int x = -80; x <= 80; x++) {
            equalBits(arbitrary.distanceToSegment(0, x + 0.125D, x - 0.75D),
                    arbitrary.distanceToSegment(0, x + 0.125D, x - 0.75D, table));
        }
    }

    @Test
    public void repeatedStationSquaresOnlyCalculateTheBoundedTable() {
        AtomicInteger calculations = new AtomicInteger();
        SurfaceDistanceTable table = new SurfaceDistanceTable(33, (x, z) -> {
            calculations.incrementAndGet();
            return StrictMath.hypot(x, z);
        });
        int initialization = calculations.get();
        assertEquals(34 * 34 + 33, initialization);
        long samples = 0;
        for (int station = 0; station < 1000; station++) {
            SurfaceCenterline centerline = segment(station, -station, station + 1, -station + 1);
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 32; z++) {
                    centerline.distanceToSegment(0, station + x, -station + z, table);
                    samples++;
                }
            }
        }
        assertEquals(4_225_000L, samples);
        assertEquals(initialization, calculations.get());
        table.hypot(34D, 0D);
        assertEquals(initialization + 1, calculations.get());
    }

    private static SurfaceCenterline segment(int x, int z, int endX, int endZ) {
        return new SurfaceCenterline(new int[]{x, endX}, new int[]{z, endZ},
                new double[2], new double[2], new int[2]);
    }

    private static void equalBits(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
    }
}
