package art.arcane.iris.world.pregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MantleHeapPressureUrgencyTest {
    @Test
    public void headroomBelowTheLowWaterMarkAsksForNothing() {
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(0D), 0D);
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(0.5D), 0D);
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(0.82D), 0D);
    }

    @Test
    public void pressureAtOrAboveTheHighWaterMarkIsFullyUrgent() {
        assertEquals(1D, MantleHeapPressure.reclaimUrgency(0.92D), 0D);
        assertEquals(1D, MantleHeapPressure.reclaimUrgency(0.99D), 0D);
        assertEquals(1D, MantleHeapPressure.reclaimUrgency(4D), 0D);
    }

    @Test
    public void pressureBetweenTheMarksRampsLinearly() {
        assertEquals(0.25D, MantleHeapPressure.reclaimUrgency(0.845D), 1.0E-9D);
        assertEquals(0.5D, MantleHeapPressure.reclaimUrgency(0.87D), 1.0E-9D);
        assertEquals(0.75D, MantleHeapPressure.reclaimUrgency(0.895D), 1.0E-9D);
    }

    @Test
    public void urgencyNeverDecreasesAsPressureRises() {
        double previous = -1D;

        for (int step = 0; step <= 100; step++) {
            double urgency = MantleHeapPressure.reclaimUrgency(step / 100D);
            assertTrue("urgency dropped at " + step, urgency >= previous);
            previous = urgency;
        }
    }

    @Test
    public void unmeasurableHeapPressureAsksForNothing() {
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(Double.NaN), 0D);
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(Double.POSITIVE_INFINITY), 0D);
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(Double.NEGATIVE_INFINITY), 0D);
    }
}
