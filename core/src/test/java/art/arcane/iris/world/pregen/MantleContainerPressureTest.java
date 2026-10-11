package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleContainerPressureTest {
    @Test
    public void containerPressureReclaimsEvenWhenTheHeapHasHeadroom() {
        double pressure = MantleHeapPressure.pressureFraction(0.45D, 0.90D);
        assertEquals(0.92D, pressure, 1E-9D);
        assertEquals(1D, MantleHeapPressure.reclaimUrgency(pressure), 0D);
    }

    @Test
    public void heapPressureStillControlsReclaimOutsideContainers() {
        assertEquals(0.95D, MantleHeapPressure.pressureFraction(0.95D, 0D), 0D);
        assertEquals(0.45D, MantleHeapPressure.pressureFraction(0.45D, 0D), 0D);
    }

    @Test
    public void containerBudgetsReserveMemoryOutsideTheHeap() {
        assertEquals(3L << 30, MantleHeapPressure.memoryBudgetBytes(8L << 30, 4L << 30));
        assertEquals(2L << 30, MantleHeapPressure.memoryBudgetBytes(2L << 30, 4L << 30));
        assertEquals(8L << 30, MantleHeapPressure.memoryBudgetBytes(8L << 30, Long.MAX_VALUE));
    }

    @Test
    public void containerPressureCannotResumeBecauseAWarningDeadlineExpired() {
        AtomicLong clock = new AtomicLong();
        MantleHeapPressure.HeapPressureGate gate = new MantleHeapPressure.HeapPressureGate(
                0.90D, 0.80D, Long.MAX_VALUE, clock::get, () -> {});
        assertTrue(gate.update(0.95D));
        clock.set(600_000L);
        assertTrue(gate.update(0.85D));
        assertFalse(gate.update(0.80D));
    }
}
