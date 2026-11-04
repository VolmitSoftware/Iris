package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MouthMetricsTest {
    private static final int SEA = 40;

    @Test
    public void readsTheDropAtTheSeaAndTheLargestStepNearIt() {
        Reach reach = reach(0L, new int[]{50, 46, 44, 40, 40}, ReachStyle.MOUTH);
        assertTrue(MouthMetrics.seaMouth(reach));
        assertEquals(4, MouthMetrics.finalDrop(reach, SEA));
        assertEquals(40D, MouthMetrics.seaRun(reach, SEA), 1e-6D);
        assertEquals(4, MouthMetrics.largestStep(reach, SEA, 64D));
    }

    @Test
    public void aFeederOrAPondEndIsNotASeaMouth() {
        assertFalse(MouthMetrics.seaMouth(reach(7L, new int[]{50, 46, 44, 40, 40}, ReachStyle.MOUTH)));
        assertFalse(MouthMetrics.seaMouth(reach(0L, new int[]{50, 46, 44, 44, 44}, ReachStyle.RUN)));
    }

    private static Reach reach(long downstream, int[] grade, ReachStyle end) {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology(), 1L, SEA, 0, 256);
        int count = grade.length;
        float[] xs = new float[count];
        float[] zs = new float[count];
        float[] half = new float[count];
        float[] depth = new float[count];
        ReachStyle[] style = new ReachStyle[count];
        Arrays.fill(half, 4f);
        Arrays.fill(depth, 2f);
        Arrays.fill(style, ReachStyle.RUN);
        for (int point = 0; point < count; point++) {
            xs[point] = 20f * point;
        }
        style[count - 1] = end;
        return new Reach(1L, settings.majorTier(), downstream, xs, zs, grade, half, depth, style, 1000f);
    }
}
