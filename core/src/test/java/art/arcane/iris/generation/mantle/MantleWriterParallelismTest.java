package art.arcane.iris.generation.mantle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MantleWriterParallelismTest {
    @Test
    public void multicorePrefetchKeepsOneWorkerOnSingleProcessorSystems() {
        assertEquals(1, MantleWriter.resolvePrefetchParallelism(false, true, 1));
        assertEquals(1, MantleWriter.resolvePrefetchParallelism(false, true, 0));
    }

    @Test
    public void maintenanceAndSequentialPrefetchKeepTheirExistingLimits() {
        assertEquals(1, MantleWriter.resolvePrefetchParallelism(true, true, 32));
        assertEquals(4, MantleWriter.resolvePrefetchParallelism(false, false, 1));
        assertEquals(8, MantleWriter.resolvePrefetchParallelism(false, true, 16));
    }
}
