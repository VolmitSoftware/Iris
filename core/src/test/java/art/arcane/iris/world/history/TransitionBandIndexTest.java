package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TransitionBandIndexTest extends GenerationHistorySupport {
    @Test
    public void membershipMatchesExposedColumnsAcrossNegativeCoordinatesAndRegionEdges() throws Exception {
        List<GenerationBoundary.ChunkCoordinate> chunks = List.of(
                new GenerationBoundary.ChunkCoordinate(-33, -33),
                new GenerationBoundary.ChunkCoordinate(-1, -1),
                new GenerationBoundary.ChunkCoordinate(0, 0),
                new GenerationBoundary.ChunkCoordinate(31, 31),
                new GenerationBoundary.ChunkCoordinate(32, 31));
        GenerationBoundary boundary = GenerationBoundary.freeze("boundary", chunks);
        List<GenerationBoundary.BlockColumn> columns = boundary.exposedBlockColumns();
        List<TerrainBoundarySignature> signatures = new ArrayList<>(columns.size());
        for (GenerationBoundary.BlockColumn column : columns) {
            signatures.add(signature(column.blockX(), column.blockZ()));
        }
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder("membership").toPath());
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(2L, signatures);
        for (int width : new int[]{1, 2, 16, 17, 32, 256}) {
            TransitionBandIndex index = new TransitionBandIndex(boundary, new TransitionBoundarySampler(width, snapshot));
            for (GenerationBoundary.ChunkCoordinate chunk : chunks) {
                for (int x = chunk.chunkX() - 4; x <= chunk.chunkX() + 4; x++) {
                    for (int z = chunk.chunkZ() - 4; z <= chunk.chunkZ() + 4; z++) {
                        assertEquals(expected(boundary, columns, x, z, width), index.contains(x, z));
                    }
                }
            }
        }
    }

    @Test
    public void repeatedMembershipReusesPositiveNegativeAndHistoricalResults() {
        GenerationBoundary boundary = mock(GenerationBoundary.class);
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(boundary.isHistoricalChunk(0, 0)).thenReturn(true);
        when(snapshot.intersectsTerrainBand(bounds(1, 0), 32)).thenReturn(true);
        TransitionBandIndex index = new TransitionBandIndex(boundary, new TransitionBoundarySampler(32, snapshot));

        for (int query = 0; query < 10_000; query++) {
            assertFalse(index.contains(0, 0));
            assertTrue(index.contains(1, 0));
            assertFalse(index.contains(2, 0));
        }

        verify(boundary, times(1)).isHistoricalChunk(0, 0);
        verify(boundary, times(1)).isHistoricalChunk(1, 0);
        verify(boundary, times(1)).isHistoricalChunk(2, 0);
        verify(snapshot, times(2)).intersectsTerrainBand(any(), eq(32));
    }

    @Test
    public void concurrentQueriesShareOneColdEvaluation() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.intersectsTerrainBand(any(), eq(32))).thenReturn(true);
        TransitionBandIndex index = new TransitionBandIndex(GenerationBoundary.freeze("empty", List.of()),
                new TransitionBoundarySampler(32, snapshot));
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        List<Future<Boolean>> queries = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                queries.add(workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return index.contains(-1, -1);
                }));
            }
            for (Future<Boolean> query : queries) {
                assertTrue(query.get(5, TimeUnit.SECONDS));
            }
            verify(snapshot, times(1)).intersectsTerrainBand(bounds(-1, -1), 32);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cachedQueriesDoNotWaitForAColdQueryInTheSameRegion() throws Exception {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(snapshot.intersectsTerrainBand(bounds(0, 0), 32)).thenReturn(true);
        when(snapshot.intersectsTerrainBand(bounds(1, 0), 32)).thenAnswer(invocation -> {
            scanning.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return false;
        });
        TransitionBandIndex index = new TransitionBandIndex(GenerationBoundary.freeze("empty", List.of()),
                new TransitionBoundarySampler(32, snapshot));
        assertTrue(index.contains(0, 0));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> cold = workers.submit(() -> index.contains(1, 0));
            assertTrue(scanning.await(5, TimeUnit.SECONDS));
            Future<Boolean> warm = workers.submit(() -> index.contains(0, 0));
            assertTrue(warm.get(2, TimeUnit.SECONDS));
            release.countDown();
            assertFalse(cold.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedMembershipCanBeRetried() {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.intersectsTerrainBand(bounds(0, 0), 32))
                .thenThrow(new IllegalStateException("Unavailable boundary shard"))
                .thenReturn(true);
        TransitionBandIndex index = new TransitionBandIndex(GenerationBoundary.freeze("empty", List.of()),
                new TransitionBoundarySampler(32, snapshot));

        assertThrows(IllegalStateException.class, () -> index.contains(0, 0));
        assertTrue(index.contains(0, 0));
        assertTrue(index.contains(0, 0));
        verify(snapshot, times(2)).intersectsTerrainBand(bounds(0, 0), 32);
    }

    @Test
    public void negativeRegionChurnKeepsMemoryBoundedAndResultsIndependent() {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(snapshot.intersectsTerrainBand(any(), anyInt()))
                .thenAnswer(invocation -> ((TerrainBoundarySignatureStore.BlockBounds) invocation.getArgument(0))
                        .minimumX() < 0L);
        TransitionBandIndex index = new TransitionBandIndex(GenerationBoundary.freeze("empty", List.of()),
                new TransitionBoundarySampler(32, snapshot));

        for (int region = 0; region < 10_000; region++) {
            assertFalse(index.contains(region * 32, region * 32));
            assertTrue(index.contains(-region * 32 - 1, -region * 32 - 1));
        }
        assertTrue(index.cachedRegionCount() <= 1_024);
        assertFalse(index.contains(0, 0));
        assertTrue(index.contains(-1, -1));
    }

    @Test
    public void historicalCoordinatesKeepTheirOriginalOverflowBehavior() {
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        GenerationBoundary boundary = GenerationBoundary.freeze("extreme", List.of(
                new GenerationBoundary.ChunkCoordinate(Integer.MAX_VALUE, Integer.MIN_VALUE)));
        TransitionBandIndex index = new TransitionBandIndex(boundary, new TransitionBoundarySampler(32, snapshot));

        assertFalse(index.contains(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> index.contains(Integer.MAX_VALUE, 0));
        assertThrows(ArithmeticException.class, () -> index.contains(Integer.MAX_VALUE, 0));
        verifyNoInteractions(snapshot);
    }

    private static TerrainBoundarySignatureStore.BlockBounds bounds(int chunkX, int chunkZ) {
        return new TerrainBoundarySignatureStore.BlockBounds(chunkX * 16, chunkZ * 16,
                chunkX * 16 + 15, chunkZ * 16 + 15);
    }

    private static boolean expected(GenerationBoundary boundary, List<GenerationBoundary.BlockColumn> columns,
                                    int chunkX, int chunkZ, int width) {
        if (boundary.isHistoricalChunk(chunkX, chunkZ)) {
            return false;
        }
        int minimumX = chunkX * 16;
        int minimumZ = chunkZ * 16;
        for (GenerationBoundary.BlockColumn column : columns) {
            long dx = Math.max(0L, Math.max((long) minimumX - column.blockX(), column.blockX() - minimumX - 15L));
            long dz = Math.max(0L, Math.max((long) minimumZ - column.blockZ(), column.blockZ() - minimumZ - 15L));
            if (dx * dx + dz * dz < (long) width * width) {
                return true;
            }
        }
        return false;
    }
}
