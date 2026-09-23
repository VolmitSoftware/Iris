package art.arcane.iris.generation.terrain;

import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Terrain3DFragmentFilterTest {
    @Test
    public void heightLeavesIrrelevantLowerFragmentsUnclassified() {
        Terrain3DColumn center = new Terrain3DColumn(10D, 11, true, new int[]{0, 10, 20, 20, 100, 612});
        BiFunction<Integer, Integer, Terrain3DColumn> source = (x, z) -> x == 0 && z == 0
                ? center : new Terrain3DColumn(10D, 11, true, new int[]{0, 10});
        Fixture reference = new Fixture(source);
        Fixture selective = new Fixture(source);
        Terrain3DColumn expected = reference.filter.column(0, 0);

        assertEquals(expected.topY(), selective.filter.height(0, 0), 0D);
        assertEquals(1, selective.reads.get());
        assertTrue(reference.reads.get() > selective.reads.get());
        assertEquals(expected, selective.filter.column(0, 0));
        assertEquals(2, expected.spanCount());
    }

    @Test
    public void heightRejectsSmallTopFragmentBeforeKeepingGroundConnectedLedge() {
        Fixture fixture = new Fixture((x, z) -> x == 0 && z == 0
                ? new Terrain3DColumn(10D, 11, true, new int[]{0, 10, 50, 70, 110, 112})
                : new Terrain3DColumn(100D, 101, true, new int[]{0, 100}));

        assertEquals(70D, fixture.filter.height(0, 0), 0D);
        assertEquals(70, fixture.filter.column(0, 0).topY());
        assertEquals(2, fixture.filter.column(0, 0).spanCount());
    }

    @Test
    public void heightUsesExactConnectedVolumeThresholdAndSignedCoordinateEdges() {
        for (int volume : new int[]{512, 513}) {
            BiFunction<Integer, Integer, Terrain3DColumn> source = (x, z) ->
                    x >= -volume && x < 0 && z == -17
                            ? new Terrain3DColumn(10D, 11, true, new int[]{0, 10, 20, 20})
                            : new Terrain3DColumn(10D, 11, true, new int[]{0, 10});
            Fixture reference = new Fixture(source);
            Fixture selective = new Fixture(source);
            assertEquals(volume == 512 ? 10D : 20D, selective.filter.height(-volume, -17), 0D);
            assertEquals(reference.filter.column(-volume, -17), selective.filter.column(-volume, -17));
        }
        for (int x : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            Fixture fixture = new Fixture((ignoredX, ignoredZ) ->
                    new Terrain3DColumn(10D, 11, true, new int[]{0, 10, 20, 20}));
            assertEquals(20D, fixture.filter.height(x, 7), 0D);
            assertEquals(20, fixture.filter.column(x, 7).topY());
        }
    }

    @Test
    public void concurrentHeightAndFullColumnQueriesMatchIndependentFullClassification() throws Exception {
        Random random = new Random(883719L);
        Map<Long, Terrain3DColumn> raw = new HashMap<>();
        List<Long> positions = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                int ground = random.nextInt(5) == 0 ? 36 : 5 + random.nextInt(6);
                int[] boundaries = new int[10];
                boundaries[0] = 0;
                boundaries[1] = ground;
                int count = 2;
                for (int y = 16; y <= 40; y += 8) {
                    if (y > ground + 1 && random.nextBoolean()) {
                        boundaries[count++] = y;
                        boundaries[count++] = y + random.nextInt(3);
                    }
                }
                long key = pack(x, z);
                raw.put(key, new Terrain3DColumn(ground + 0.25D, ground + 1, true, Arrays.copyOf(boundaries, count)));
                positions.add(key);
            }
        }
        Terrain3DColumn outside = new Terrain3DColumn(8D, 9, true, new int[]{0, 8});
        BiFunction<Integer, Integer, Terrain3DColumn> source = (x, z) -> raw.getOrDefault(pack(x, z), outside);
        Fixture reference = new Fixture(source);
        Map<Long, Terrain3DColumn> expected = new HashMap<>();
        for (long key : positions) {
            expected.put(key, reference.filter.column((int) (key >> 32), (int) key));
        }
        Fixture selective = new Fixture(source);
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                int shift = worker * 59;
                tasks.add(workers.submit(() -> {
                    for (int index = 0; index < positions.size() * 2; index++) {
                        long key = positions.get((index + shift) % positions.size());
                        int x = (int) (key >> 32);
                        int z = (int) key;
                        assertEquals(expected.get(key).topY(), selective.filter.height(x, z), 0D);
                        if ((index & 1) == 0) {
                            assertEquals(expected.get(key), selective.filter.column(x, z));
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }
        for (long key : positions) {
            assertEquals(expected.get(key), selective.filter.column((int) (key >> 32), (int) key));
        }
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | z & 0xffffffffL;
    }

    private static final class Fixture {
        private final AtomicInteger reads = new AtomicInteger();
        private final ConcurrentHashMap<Long, Terrain3DFragmentFilter.DensityColumn> columns = new ConcurrentHashMap<>();
        private final Terrain3DFragmentFilter filter;

        private Fixture(BiFunction<Integer, Integer, Terrain3DColumn> source) {
            filter = new Terrain3DFragmentFilter((x, z) -> {
                reads.incrementAndGet();
                return columns.computeIfAbsent(pack(x, z), ignored -> new Terrain3DFragmentFilter.DensityColumn(source.apply(x, z)));
            });
        }
    }

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
