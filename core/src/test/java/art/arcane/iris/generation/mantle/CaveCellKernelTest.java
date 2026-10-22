package art.arcane.iris.generation.mantle;

import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CaveCellKernelTest {
    private static final int TRIALS = 200_000;

    @Test
    public void decidedCellsAgreeWithEveryInterpolatedColumn() {
        SplittableRandom random = new SplittableRandom(91_337L);
        long decided = 0L;
        for (int trial = 0; trial < TRIALS; trial++) {
            double scale = Math.scalb(1D, random.nextInt(-30, 4));
            double base = (random.nextDouble() * 2D - 1D) * scale;
            double spread = random.nextInt(4) == 0 ? 0D : random.nextDouble() * scale * Math.scalb(1D, -random.nextInt(0, 40));
            double d00 = base + spread * (random.nextDouble() * 2D - 1D);
            double d01 = random.nextInt(8) == 0 ? d00 : base + spread * (random.nextDouble() * 2D - 1D);
            double d10 = base + spread * (random.nextDouble() * 2D - 1D);
            double d11 = random.nextInt(8) == 0 ? d10 : base + spread * (random.nextDouble() * 2D - 1D);
            double low = Math.min(Math.min(d00, d01), Math.min(d10, d11));
            double high = Math.max(Math.max(d00, d01), Math.max(d10, d11));
            int mode = random.nextInt(6);
            double[] thresholds = new double[64];
            for (int column = 0; column < 64; column++) {
                thresholds[column] = pickThreshold(random, low, high, mode);
            }
            double thresholdLow = Double.POSITIVE_INFINITY;
            double thresholdHigh = Double.NEGATIVE_INFINITY;
            for (double threshold : thresholds) {
                thresholdLow = Math.min(thresholdLow, threshold);
                thresholdHigh = Math.max(thresholdHigh, threshold);
            }
            int decision = CaveCellKernel.decide(d00, d01, d10, d11, thresholdLow, thresholdHigh);
            if (decision == CaveCellKernel.MIXED) {
                continue;
            }
            decided++;
            for (int column = 0; column < 64; column++) {
                double tx = (column >> 3) / 8D;
                double tz = (column & 7) / 8D;
                boolean carved = bilinear(d00, d01, d10, d11, tx, tz) <= thresholds[column];
                assertEquals("trial " + trial + " column " + column, decision == CaveCellKernel.ALL, carved);
            }
        }
        assertTrue("decided " + decided, decided > TRIALS / 4);
    }

    @Test
    public void thresholdsAtTheCornerExtremesStayMixed() {
        double d00 = 0.1D;
        double d01 = -0.2D;
        double d10 = 0.3D;
        double d11 = 0.05D;
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(d00, d01, d10, d11, 0.3D, 0.3D));
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(d00, d01, d10, d11, Math.nextUp(0.3D), 0.4D));
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(d00, d01, d10, d11, -0.3D, Math.nextDown(-0.2D)));
        assertEquals(CaveCellKernel.ALL, CaveCellKernel.decide(d00, d01, d10, d11, 0.3D + 1.0E-12D, 0.5D));
        assertEquals(CaveCellKernel.NONE, CaveCellKernel.decide(d00, d01, d10, d11, -0.9D, -0.2D - 1.0E-12D));
    }

    @Test
    public void nonFiniteCornersAreNeverDecided() {
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(Double.NaN, 0D, 0D, 0D, 5D, 5D));
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(0D, Double.POSITIVE_INFINITY, 0D, 0D, -5D, -5D));
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(0D, 0D, Double.NEGATIVE_INFINITY, 0D,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertEquals(CaveCellKernel.MIXED, CaveCellKernel.decide(0D, 0D, 0D, 0D, Double.NaN, 1D));
    }

    @Test
    public void monotoneThresholdMappingPreservesBounds() {
        SplittableRandom random = new SplittableRandom(7_771L);
        for (int trial = 0; trial < TRIALS; trial++) {
            double normalization = 0.25D + random.nextDouble() * 3D;
            double inverse = 1D / normalization;
            double a = (random.nextDouble() * 2D - 1D) * Math.scalb(1D, random.nextInt(-20, 3));
            double b = random.nextInt(4) == 0 ? Math.nextUp(a) : a + random.nextDouble() * Math.scalb(1D, random.nextInt(-50, 0));
            assertTrue(CaveCellKernel.threshold(a, normalization, inverse) <= CaveCellKernel.threshold(b, normalization, inverse));
            assertEquals(Double.doubleToRawLongBits((a * normalization) * inverse),
                    Double.doubleToRawLongBits(CaveCellKernel.threshold(a, normalization, inverse)));
        }
    }

    private static double pickThreshold(SplittableRandom random, double low, double high, int mode) {
        double width = Math.max(high - low, Math.ulp(Math.max(Math.abs(low), Math.abs(high))));
        return switch (mode) {
            case 0 -> high + Math.ulp(high) * random.nextInt(0, 64);
            case 1 -> low - Math.ulp(low) * random.nextInt(0, 64);
            case 2 -> high + width * random.nextDouble() * 0.5D;
            case 3 -> low - width * random.nextDouble() * 0.5D;
            case 4 -> high + width * (1D + random.nextDouble() * 4D);
            default -> low - width * (1D + random.nextDouble() * 4D);
        };
    }

    private static double bilinear(double d00, double d01, double d10, double d11, double tx, double tz) {
        double dx0 = d00 + ((d10 - d00) * tx);
        double dx1 = d01 + ((d11 - d01) * tx);
        return dx0 + ((dx1 - dx0) * tz);
    }
}
