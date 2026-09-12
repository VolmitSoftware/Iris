package art.arcane.iris.structure.object;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisAxisRotationClampPurityTest {
    private static IrisAxisRotationClamp unlimited(double interval) {
        IrisAxisRotationClamp clamp = new IrisAxisRotationClamp();
        clamp.setEnabled(true);
        clamp.setInterval(interval);
        return clamp;
    }

    private static double reference(double interval, int rng) {
        double resolved = interval < 1 ? 1 : interval;
        return Math.toRadians((resolved * (Math.ceil(Math.abs((rng % 360D) / resolved)))) % 360D);
    }

    @Test
    public void getRadiansDoesNotMutateIntervalWhenUnlimited() {
        IrisAxisRotationClamp clamp = unlimited(0);
        clamp.getRadians(37);
        assertEquals(0D, clamp.getInterval(), 0D);
    }

    @Test
    public void getRadiansValuesAreUnchangedByTheFix() {
        double[] intervals = {0, 0.5, 1, 90};
        int[] rngs = {0, 1, 37, 91, 359, 720, -13};
        for (double interval : intervals) {
            for (int rng : rngs) {
                assertEquals("interval=" + interval + " rng=" + rng,
                        reference(interval, rng), unlimited(interval).getRadians(rng), 0D);
            }
        }
    }

    @Test
    public void equalsAndHashCodeSurviveRepeatedGetRadians() {
        IrisAxisRotationClamp a = unlimited(0);
        IrisAxisRotationClamp b = unlimited(0);
        a.getRadians(123);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void concurrentGetRadiansLeavesIntervalUntouched() throws InterruptedException {
        IrisAxisRotationClamp shared = unlimited(0);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 10_000; i++) {
                    shared.getRadians(i);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertTrue("interval must stay at the authored value", shared.getInterval() == 0D);
    }
}
