package art.arcane.iris.platform.bukkit.terrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisApiFaultGuardTest {
    @Test
    public void theFirstFaultIsAlwaysReported() {
        IrisApiFaultGuard guard = new IrisApiFaultGuard(60_000L);

        assertTrue(guard.record(0L));
        assertEquals(1L, guard.faults());
    }

    @Test
    public void faultsInsideTheIntervalAreCountedButNotReported() {
        IrisApiFaultGuard guard = new IrisApiFaultGuard(60_000L);
        guard.record(1_000L);

        assertFalse(guard.record(2_000L));
        assertFalse(guard.record(60_999L));
        assertEquals(3L, guard.faults());
    }

    @Test
    public void reportingResumesOnceTheIntervalElapses() {
        IrisApiFaultGuard guard = new IrisApiFaultGuard(60_000L);
        guard.record(1_000L);
        guard.record(2_000L);

        assertTrue(guard.record(61_000L));
        assertFalse(guard.record(61_001L));
        assertEquals(4L, guard.faults());
    }

    @Test
    public void aZeroIntervalReportsEveryFault() {
        IrisApiFaultGuard guard = new IrisApiFaultGuard(0L);

        assertTrue(guard.record(5L));
        assertTrue(guard.record(5L));
        assertEquals(2L, guard.faults());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeIntervalIsRejected() {
        new IrisApiFaultGuard(-1L);
    }
}
