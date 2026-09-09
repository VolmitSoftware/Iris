package art.arcane.iris.engine.terrain;

import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Terrain3DFragmentFilterTest {
    @Test
    public void perThreadTraversalScratchDoesNotPinTheFilterAfterItIsDropped() throws Exception {
        // A hotload retires the runtime that owns the filter; the worker threads that classified
        // fragments for it keep living. Their per-thread scratch must not keep the retired runtime
        // (and the stream caches behind it) reachable.
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            WeakReference<Terrain3DFragmentFilter> dropped = worker.submit(this::classifyAndDrop)
                    .get(10, TimeUnit.SECONDS);
            assertNotNull(dropped);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (dropped.get() != null && System.nanoTime() < deadline) {
                System.gc();
                Thread.sleep(20);
            }
            assertNull("Retired fragment filter is still reachable from a worker thread", dropped.get());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private WeakReference<Terrain3DFragmentFilter> classifyAndDrop() {
        Terrain3DColumn raw = new Terrain3DColumn(64D, 0, false, new int[]{0, 40, 50, 60});
        Terrain3DFragmentFilter.DensityColumn density = new Terrain3DFragmentFilter.DensityColumn(raw);
        Terrain3DFragmentFilter filter = new Terrain3DFragmentFilter((x, z) -> density);
        assertNotNull(filter.column(0, 0));
        return new WeakReference<>(filter);
    }
}
