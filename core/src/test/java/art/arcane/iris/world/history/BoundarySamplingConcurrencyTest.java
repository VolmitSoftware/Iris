package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BoundarySamplingConcurrencyTest {
    private static final BoundaryColumnGeometry GEOMETRY = new BoundaryColumnGeometry(0,
            List.of(new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID,
                    "", false)), new int[]{65}, new short[]{0});

    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void simultaneousQueriesBuildOneCandidateIndex() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(snapshot.nearestCandidatesForChunk(0, 0, 32)).thenAnswer(invocation -> {
            scanning.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return List.of(signature(0, 0));
        });
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, snapshot);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        List<Future<BoundaryGeometryInfluence>> queries = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                queries.add(workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return sampler.geometryAt(1, 1);
                }));
            }
            assertTrue(scanning.await(5, TimeUnit.SECONDS));
            release.countDown();
            BoundaryGeometryInfluence expected = queries.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<BoundaryGeometryInfluence> query : queries) {
                assertEquals(expected, query.get(5, TimeUnit.SECONDS));
            }
            verify(snapshot, times(1)).nearestCandidatesForChunk(0, 0, 32);
            assertEquals(1L, sampler.candidateBuildCount());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedCandidateBuildCanBeRetried() {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.nearestCandidatesForChunk(0, 0, 32))
                .thenThrow(new IllegalStateException("Unavailable boundary shard"))
                .thenReturn(List.of(signature(0, 0)));
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, snapshot);

        assertThrows(IllegalStateException.class, () -> sampler.sample(1, 1));
        assertEquals(64D, sampler.sample(1, 1).historicalSurfaceHeight(), 0D);
        verify(snapshot, times(2)).nearestCandidatesForChunk(0, 0, 32);
    }

    @Test
    public void generationWindowReusesCandidatesAndEvictsPastItsChunkBound() {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.nearestCandidatesForChunk(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, snapshot);
        for (int chunk = 0; chunk < 512; chunk++) {
            sampler.geometryAt(chunk * 16, 0);
        }
        sampler.geometryAt(0, 0);
        assertEquals(512L, sampler.candidateBuildCount());
        sampler.geometryAt(512 * 16, 0);
        sampler.geometryAt(16, 0);
        assertEquals(514L, sampler.candidateBuildCount());
    }

    @Test
    public void denseCandidateIndexesRespectTheReferenceBudget() {
        List<TerrainBoundarySignature> dense = new ArrayList<>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                dense.add(signature(x, z));
            }
        }
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.nearestCandidatesForChunk(anyInt(), anyInt(), anyInt())).thenReturn(dense);
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, snapshot);
        for (int chunk = 0; chunk < 33; chunk++) {
            sampler.geometryAt(chunk * 16, 0);
        }
        sampler.geometryAt(0, 0);
        assertEquals(34L, sampler.candidateBuildCount());
    }

    @Test
    public void activeGeometryColumnKeepsItsCandidatesAfterSharedCacheEviction() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.nearestCandidatesForChunk(anyInt(), anyInt(), anyInt())).thenReturn(List.of(signature(0, 0)));
        TransitionBoundarySampler sampler = new TransitionBoundarySampler(32, snapshot);
        BoundaryGeometryInfluence first = sampler.geometryAt(1, 1);
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(() -> {
                for (int chunk = 1; chunk <= 512; chunk++) {
                    sampler.geometryAt(chunk * 16, 0);
                }
            }).get(5, TimeUnit.SECONDS);
            assertEquals(first, sampler.geometryAt(1, 1));
            verify(snapshot, times(1)).nearestCandidatesForChunk(0, 0, 32);
        } finally {
            other.shutdownNow();
            assertTrue(other.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void distinctBoundaryScansDoNotHoldTheShardCacheMonitor() throws Exception {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("parallel-scans").toPath());
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(1L,
                List.of(signature(0, 0), signature(512, 512)));
        snapshot.signatureAt(0, 0).orElseThrow();
        snapshot.signatureAt(512, 512).orElseThrow();
        Map<Long, List<TerrainBoundarySignature>> cache = shardCache(snapshot);
        BlockingSignatures blocked = new BlockingSignatures(cache.get(0L));
        cache.put(0L, blocked);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<List<TerrainBoundarySignature>> first = workers.submit(
                    () -> snapshot.nearestCandidatesForChunk(0, 0, 16));
            assertTrue(blocked.entered.await(5, TimeUnit.SECONDS));
            Future<List<TerrainBoundarySignature>> distant = workers.submit(
                    () -> snapshot.nearestCandidatesForChunk(32, 32, 16));
            assertEquals(512, distant.get(3, TimeUnit.SECONDS).getFirst().blockX());
            assertFalse(first.isDone());
            assertTrue(workers.submit(() -> snapshot.intersectsTerrainBand(
                    new TerrainBoundarySignatureStore.BlockBounds(512, 512, 527, 527), 16))
                    .get(3, TimeUnit.SECONDS));
            blocked.release.countDown();
            assertEquals(0, first.get(3, TimeUnit.SECONDS).getFirst().blockX());
            assertEquals(2L, snapshot.shardLoadCount());
        } finally {
            blocked.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, List<TerrainBoundarySignature>> shardCache(
            TerrainBoundarySignatureStore.Snapshot snapshot) throws Exception {
        Field sourceField = TerrainBoundarySignatureStore.Snapshot.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        Object source = sourceField.get(snapshot);
        Field cacheField = source.getClass().getDeclaredField("cache");
        cacheField.setAccessible(true);
        return (Map<Long, List<TerrainBoundarySignature>>) cacheField.get(source);
    }

    private static TerrainBoundarySignature signature(int x, int z) {
        return new TerrainBoundarySignature(
                new TerrainBoundarySignature.Column(x, z, 64, 64, OptionalInt.empty(), OptionalInt.empty()),
                new TerrainBoundarySignature.Samples(new TerrainBoundarySignature.VerticalLayout(0, 4, 0),
                        new TerrainBoundarySignature.BiomeEncoding(List.of(), new short[0])),
                GEOMETRY);
    }

    private static final class BlockingSignatures extends AbstractList<TerrainBoundarySignature> {
        private final List<TerrainBoundarySignature> values;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingSignatures(List<TerrainBoundarySignature> values) {
            this.values = values;
        }

        @Override
        public TerrainBoundarySignature get(int index) {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interruption);
            }
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }
    }
}
