package art.arcane.iris.probe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TidalReachMetricsTest {
    private static float[] line(int points, float step) {
        float[] values = new float[points];
        for (int point = 0; point < points; point++) {
            values[point] = point * step;
        }
        return values;
    }

    @Test
    public void straightLineRunsItsWholeLengthWithoutTurning() {
        float[] xs = line(25, 4f);
        float[] zs = new float[25];
        assertEquals(1D, TidalReachMetrics.sinuosity(xs, zs), 1e-9D);
        assertEquals(96D, TidalReachMetrics.straightRun(xs, zs), 1e-9D);
        assertEquals(0D, TidalReachMetrics.sharpestTurn(xs, zs), 1e-9D);
    }

    @Test
    public void rightAngleShowsAsASharpTurn() {
        float[] xs = {0f, 40f, 40f};
        float[] zs = {0f, 0f, 40f};
        assertEquals(80D / Math.hypot(40D, 40D), TidalReachMetrics.sinuosity(xs, zs), 1e-6D);
        assertTrue(TidalReachMetrics.sharpestTurn(xs, zs) >= 45D);
        assertEquals(40D, TidalReachMetrics.straightRun(xs, zs), 4.01D);
    }

    @Test
    public void wavyLineHasShortStraightRunsAndAboveOneSinuosity() {
        int points = 200;
        float[] xs = new float[points];
        float[] zs = new float[points];
        for (int point = 0; point < points; point++) {
            xs[point] = point;
            zs[point] = (float) (6D * Math.sin(point / 8D));
        }
        assertTrue(TidalReachMetrics.sinuosity(xs, zs) > 1.1D);
        assertTrue(TidalReachMetrics.straightRun(xs, zs) < 24D);
        assertEquals(1D, TidalReachMetrics.percentile(new double[]{0D, 1D, 2D}, 0.5D), 0D);
    }
}
