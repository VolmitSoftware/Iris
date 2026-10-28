package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.diagnostics.ContainerMemory;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleContainerPressureTest {
    private static final long MIB = 1L << 20;

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

    @Test
    public void freeCommittedHeapDoesNotCountAsContainerPressure() {
        ContainerMemory.Snapshot container = containerAt(8_601L * MIB, 8_240L * MIB);
        double containerPressure = MantleHeapPressure.containerPressureFraction(container, 7_388L * MIB, 2_355L * MIB);
        assertEquals((8_240D - 5_033D) / 8_601D, containerPressure, 1E-9D);
        assertEquals(0D, MantleHeapPressure.reclaimUrgency(MantleHeapPressure.pressureFraction(0.31D, containerPressure)), 0D);
    }

    @Test
    public void heapFillingItsCommittedSpaceStillRaisesContainerPressure() {
        ContainerMemory.Snapshot container = containerAt(8_601L * MIB, 8_240L * MIB);
        double containerPressure = MantleHeapPressure.containerPressureFraction(container, 7_388L * MIB, 7_200L * MIB);
        assertTrue(containerPressure >= 0.90D);
        assertEquals(1D, MantleHeapPressure.reclaimUrgency(MantleHeapPressure.pressureFraction(0.94D, containerPressure)), 0D);
    }

    @Test
    public void containerPressureNeverDropsBelowZero() {
        ContainerMemory.Snapshot container = containerAt(4_096L * MIB, 1_024L * MIB);
        assertEquals(0D, MantleHeapPressure.containerPressureFraction(container, 3_072L * MIB, 512L * MIB), 0D);
    }

    @Test
    public void unlimitedContainersReportNoContainerPressure() {
        ContainerMemory.Snapshot container = new ContainerMemory.Snapshot(Long.MAX_VALUE, 0L, 0L, 0D, Long.MAX_VALUE);
        assertEquals(0D, MantleHeapPressure.containerPressureFraction(container, 7_388L * MIB, 2_355L * MIB), 0D);
    }

    private static ContainerMemory.Snapshot containerAt(long limitBytes, long workingSetBytes) {
        return new ContainerMemory.Snapshot(limitBytes, workingSetBytes, workingSetBytes,
                workingSetBytes / (double) limitBytes, limitBytes - workingSetBytes);
    }
}
