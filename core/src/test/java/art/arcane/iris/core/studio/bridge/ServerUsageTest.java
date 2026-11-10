package art.arcane.iris.core.studio.bridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerUsageTest {
    @Test
    public void cpuCountsAHundredPercentPerBusyThread() {
        ServerUsage usage = ServerUsage.of(0.25D, 8, 1L, 2L);

        assertEquals(200D, usage.cpuPercent(), 1e-9D);
        assertEquals(8, usage.threads());
    }

    @Test
    public void everyThreadBusyReachesAHundredPerThread() {
        assertEquals(1600D, ServerUsage.of(1D, 16, 1L, 2L).cpuPercent(), 1e-9D);
    }

    @Test
    public void anUnknownCpuLoadIsLeftOut() {
        assertNull(ServerUsage.of(-1D, 8, 1L, 2L).cpuPercent());
    }

    @Test
    public void aSampleHoldsTheHeapInUseWithinItsMaximum() {
        ServerUsage usage = ServerUsage.sample();

        assertNotNull(usage);
        assertTrue(usage.threads() >= 1);
        assertTrue(usage.heapUsedBytes() > 0L);
        assertTrue(usage.heapUsedBytes() <= usage.heapMaxBytes());
    }
}
