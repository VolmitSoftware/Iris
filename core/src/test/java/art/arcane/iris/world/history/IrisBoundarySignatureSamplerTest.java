package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisBoundarySignatureSamplerTest {
    @Test
    public void capturesActualPlatformChunkOnceAndCheckpointsAfterSampling() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        SavedTerrainChunk terrain = terrain();
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(CompletableFuture.completedFuture(terrain));
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            assertSame(terrain.column(-17, 8), capture.sample(-17, 8));
            assertEquals("example:physical", capture.sample(-32, 8).biomeAtSample(0));
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).captureSavedTerrainChunk(engine, -2, 0);
        verify(hooks).flushSavedTerrainCapture(engine);
        verify(engine, never()).getComplex();
    }

    @Test
    public void platformCaptureAndSaveFailuresReachTheCaller() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(hooks.captureSavedTerrainChunk(engine, -2, 0))
                .thenReturn(CompletableFuture.failedFuture(new IOException("capture failure")));
        when(hooks.flushSavedTerrainCapture(engine))
                .thenReturn(CompletableFuture.failedFuture(new IOException("save failure")));
        TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine);
        assertThrows(IOException.class, () -> capture.sample(-17, 8));
        assertThrows(IOException.class, capture::close);
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void neverWaitsOnThePlatformTickThread() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(hooks.isMainThread()).thenReturn(true);
        assertThrows(IOException.class, () -> IrisBoundarySignatureSampler.INSTANCE.open(engine));
        assertThrows(IOException.class, () -> IrisBoundarySignatureSampler.checkpoint(engine));
        verify(hooks, never()).flushSavedTerrainCapture(engine);
    }

    @Test
    public void submitsTheWholeBatchBeforeWaitingForAnUnresolvedChunk() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        CompletableFuture<SavedTerrainChunk> first = new CompletableFuture<>();
        CompletableFuture<SavedTerrainChunk> second = new CompletableFuture<>();
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(first);
        when(hooks.captureSavedTerrainChunk(engine, -1, 0)).thenReturn(second);
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine);
             ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<?> prepared = worker.submit(() -> {
                capture.prepareChunks(List.of(new GenerationBoundary.ChunkCoordinate(-2, 0),
                        new GenerationBoundary.ChunkCoordinate(-1, 0)));
                return null;
            });
            prepared.get(3, TimeUnit.SECONDS);
            verify(hooks).captureSavedTerrainChunk(engine, -2, 0);
            verify(hooks).captureSavedTerrainChunk(engine, -1, 0);
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            Future<TerrainBoundarySignature> sampled = worker.submit(() -> capture.sample(-32, 8));
            SavedTerrainChunk firstTerrain = terrain(-2, 0);
            second.complete(terrain(-1, 0));
            assertFalse(sampled.isDone());
            first.complete(firstTerrain);
            assertSame(firstTerrain.column(-32, 8), sampled.get(3, TimeUnit.SECONDS));
            assertEquals("example:physical", capture.sample(-16, 8).biomeAtSample(0));
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void preparingFullWindowsDoesNotEvictAnyCaptureBeforeItIsSampled() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        List<CompletableFuture<SavedTerrainChunk>> requests = new ArrayList<>();
        when(hooks.captureSavedTerrainChunk(eq(engine), anyInt(), eq(0))).thenAnswer(invocation -> {
            CompletableFuture<SavedTerrainChunk> capture = new CompletableFuture<>();
            requests.add(capture);
            return capture;
        });
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine);
             ExecutorService worker = Executors.newSingleThreadExecutor()) {
            for (int window = 0; window < 2; window++) {
                List<GenerationBoundary.ChunkCoordinate> coordinates = new ArrayList<>(32);
                int first = window * 32;
                for (int index = 0; index < 32; index++) {
                    coordinates.add(new GenerationBoundary.ChunkCoordinate(first + index, 0));
                }
                worker.submit(() -> {
                    capture.prepareChunks(List.copyOf(coordinates));
                    return null;
                }).get(3, TimeUnit.SECONDS);
                assertEquals(first + 32, requests.size());
                for (int index = first; index < first + 32; index++) {
                    assertFalse(requests.get(index).isDone());
                }
                for (int index = first; index < first + 32; index++) {
                    SavedTerrainChunk terrain = terrain(index, 0);
                    assertTrue(requests.get(index).complete(terrain));
                    assertSame(terrain.column(index * 16, 0), capture.sample(index * 16, 0));
                }
            }
            assertEquals(64, requests.size());
            for (CompletableFuture<SavedTerrainChunk> request : requests) {
                assertFalse(request.isCancelled());
            }
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void failedBatchCaptureCancelsPendingChunksBeforeTheFinalCheckpoint() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        IOException failure = new IOException("capture failed");
        CompletableFuture<SavedTerrainChunk> pending = new CompletableFuture<>();
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(CompletableFuture.failedFuture(failure));
        when(hooks.captureSavedTerrainChunk(engine, -1, 0)).thenReturn(pending);
        when(hooks.flushSavedTerrainCapture(engine)).thenAnswer(invocation -> {
            assertTrue(pending.isCancelled());
            return CompletableFuture.completedFuture(null);
        });
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            capture.prepareChunks(List.of(new GenerationBoundary.ChunkCoordinate(-2, 0),
                    new GenerationBoundary.ChunkCoordinate(-1, 0)));
            IOException propagated = assertThrows(IOException.class, () -> capture.sample(-32, 8));
            assertSame(failure, propagated.getCause().getCause());
            assertTrue(pending.isCancelled());
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void failedBatchSubmissionCancelsRequestsAlreadySubmitted() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        CompletableFuture<SavedTerrainChunk> pending = new CompletableFuture<>();
        IllegalStateException failure = new IllegalStateException("capture submission failed");
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(pending);
        when(hooks.captureSavedTerrainChunk(engine, -1, 0)).thenThrow(failure);
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> capture.prepareChunks(List.of(
                    new GenerationBoundary.ChunkCoordinate(-2, 0), new GenerationBoundary.ChunkCoordinate(-1, 0)))));
            assertTrue(pending.isCancelled());
        }
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void pendingCacheIsBoundedAndCloseCancelsRemainingRequests() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        List<CompletableFuture<SavedTerrainChunk>> requests = new ArrayList<>();
        when(hooks.captureSavedTerrainChunk(eq(engine), anyInt(), eq(0))).thenAnswer(invocation -> {
            CompletableFuture<SavedTerrainChunk> future = new CompletableFuture<>();
            requests.add(future);
            return future;
        });
        when(hooks.flushSavedTerrainCapture(engine)).thenAnswer(invocation -> {
            for (CompletableFuture<SavedTerrainChunk> request : requests) {
                assertTrue(request.isCancelled());
            }
            return CompletableFuture.completedFuture(null);
        });
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            for (int chunkX = 0; chunkX < 40; chunkX++) {
                capture.prepareChunks(List.of(new GenerationBoundary.ChunkCoordinate(chunkX, 0)));
                int pendingCount = 0;
                for (CompletableFuture<SavedTerrainChunk> request : requests) {
                    if (!request.isDone()) {
                        pendingCount++;
                    }
                }
                assertEquals(Math.min(chunkX + 1, 32), pendingCount);
            }
            assertEquals(40, requests.size());
            assertTrue(requests.get(7).isCancelled());
            assertFalse(requests.get(8).isDone());
            verify(hooks, never()).flushSavedTerrainCapture(engine);
        }
        verify(hooks).flushSavedTerrainCapture(engine);
    }

    @Test
    public void preparationDeduplicatesRequestsAndRejectsOversizedBatches() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        CompletableFuture<SavedTerrainChunk> pending = new CompletableFuture<>();
        when(hooks.captureSavedTerrainChunk(engine, -2, 0)).thenReturn(pending);
        when(hooks.flushSavedTerrainCapture(engine)).thenReturn(CompletableFuture.completedFuture(null));
        GenerationBoundary.ChunkCoordinate coordinate = new GenerationBoundary.ChunkCoordinate(-2, 0);
        try (TerrainBoundarySignatureStore.SignatureSampler capture = IrisBoundarySignatureSampler.INSTANCE.open(engine)) {
            capture.prepareChunks(List.of(coordinate, coordinate));
            capture.prepareChunks(List.of(coordinate));
            verify(hooks).captureSavedTerrainChunk(engine, -2, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> capture.prepareChunks(Collections.nCopies(33, coordinate)));
        }
        assertTrue(pending.isCancelled());
    }

    private static SavedTerrainChunk terrain() throws IOException {
        return terrain(-2, 0);
    }

    private static SavedTerrainChunk terrain(int chunkX, int chunkZ) throws IOException {
        return SavedTerrainChunk.captureBoundary(chunkX, chunkZ, -16, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int localX, int y, int localZ) {
                return new BoundaryColumnGeometry.Voxel("minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
            }

            @Override
            public String biome(int localX, int y, int localZ) {
                return "example:physical";
            }
        });
    }
}
