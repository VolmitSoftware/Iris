package art.arcane.iris.engine.history;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class SavedBiomeRuntimeTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final IrisEngine engine = mock(IrisEngine.class);
    private final GenerationHistory history = mock(GenerationHistory.class);
    private final SavedBiomeStore store = mock(SavedBiomeStore.class);
    private IrisSettings previousSettings;

    @Before
    public void prepareServices() throws Exception {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        when(history.savedBiomes()).thenReturn(store);
        when(history.paths()).thenReturn(GenerationHistoryPaths.forDimension(temporaryFolder.newFolder().toPath()));
        String epochId = "a".repeat(64);
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        when(history.manifest()).thenReturn(manifest);
        when(manifest.activation(1L)).thenReturn(Optional.of(GenerationActivation.initial(epochId, 1L)));
        when(manifest.activation(2L)).thenReturn(Optional.of(GenerationActivation.next(2L, epochId, 1L, 2L, 64)));
        when(manifest.epoch(epochId)).thenReturn(Optional.of(epoch));
        when(epoch.epochId()).thenReturn(epochId);
    }

    @After
    public void restoreServices() {
        IrisServices.clear();
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void everyCallerReceivesLoadingWithoutWaitingForDisk() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.get(0, 0)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.of(unresolvedChunk(1));
        });
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<SavedBiomeUnavailableException> response = caller.submit(() -> assertThrows(
                    SavedBiomeUnavailableException.class, () -> runtime.resolve(0, 0, 0, true)));
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertTrue(response.get(1L, TimeUnit.SECONDS).isLoading());
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    @Test
    public void chunkReadinessDoesNotRequireEveryCellToHaveAResolvedIdentity() throws Exception {
        when(store.get(0, 0)).thenReturn(Optional.of(unresolvedChunk(1)));
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history)) {
            assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.prepareChunk(0, 0)).isLoading());
            awaitIdle(runtime);
            runtime.prepareChunk(0, 0);
            SavedBiomeUnavailableException unavailable = assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.resolve(0, 0, 0, true));
            assertEquals(false, unavailable.isLoading());
        }
    }

    @Test
    public void closeWaitsForInvalidatedWorkersAndRestoresInterruptStatus() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SavedBiomeChunk chunk = unresolvedChunk(1);
        when(store.get(0, 0)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.of(chunk);
        });
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                () -> runtime.resolve(0, 0, 0, true)).isLoading());
        assertTrue(entered.await(5L, TimeUnit.SECONDS));
        runtime.capture(chunk);
        assertEquals(1, runtime.pendingQueryCount());
        try (ExecutorService closer = Executors.newSingleThreadExecutor()) {
            Future<Boolean> closed = closer.submit(() -> {
                Thread.currentThread().interrupt();
                runtime.close();
                return Thread.currentThread().isInterrupted();
            });
            try {
                assertThrows(TimeoutException.class, () -> closed.get(100L, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertTrue(closed.get(5L, TimeUnit.SECONDS));
            assertEquals(0, runtime.pendingQueryCount());
            assertEquals(0, runtime.cachedQueryCount());
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    @Test
    public void completedSnapshotCacheRespectsItsByteBudgetAndNegativeCacheIsBounded() throws Exception {
        when(store.get(anyInt(), anyInt())).thenReturn(Optional.of(unresolvedChunk(16)));
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history)) {
            for (int chunkX = 0; chunkX < 110; chunkX++) {
                int blockX = chunkX * 16;
                assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                        () -> runtime.resolve(blockX, 0, 0, true)).isLoading());
                awaitIdle(runtime);
                assertTrue(runtime.cachedQueryBytes() <= SavedBiomeChunk.MAXIMUM_ESTIMATED_BYTES);
            }
            assertTrue(runtime.cachedQueryCount() < 110);
        }
        when(store.get(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history)) {
            for (int chunkX = 0; chunkX < 150; chunkX++) {
                int blockX = chunkX * 16;
                assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                        () -> runtime.resolve(blockX, 0, 0, true)).isLoading());
                awaitIdle(runtime);
                assertEquals(Optional.empty(), runtime.resolve(blockX, 0, 0, true));
                assertTrue(runtime.cachedQueryCount() <= 128);
            }
        }
    }

    @Test
    public void preparedEnvironmentSurvivesLoaderEvictionWithoutReadingPackFilesAgain() throws Exception {
        String epochId = "a".repeat(64);
        Path pack = history.paths().packRoot(epochId);
        Files.createDirectories(pack.resolve("dimensions"));
        Files.createDirectories(pack.resolve("biomes"));
        Files.createDirectories(pack.resolve("regions"));
        Files.writeString(pack.resolve("dimensions/main.json"), "{}");
        Files.writeString(pack.resolve("biomes/forest.json"), "{}");
        Files.writeString(pack.resolve("regions/main.json"), "{}");
        GenerationManifest manifest = mock(GenerationManifest.class);
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        GenerationEpoch.DimensionContract contract = mock(GenerationEpoch.DimensionContract.class);
        when(history.manifest()).thenReturn(manifest);
        when(history.packRoot(1L)).thenReturn(pack);
        when(history.packRoot(2L)).thenReturn(pack);
        when(manifest.activation(1L)).thenReturn(Optional.of(GenerationActivation.initial(epochId, 1L)));
        when(manifest.activation(2L)).thenReturn(Optional.of(GenerationActivation.next(2L, epochId, 1L, 2L, 64)));
        when(manifest.epoch(epochId)).thenReturn(Optional.of(epoch));
        when(epoch.epochId()).thenReturn(epochId);
        when(epoch.registryContract()).thenReturn(GenerationRegistryContract.empty());
        when(epoch.dimensionContract()).thenReturn(contract);
        when(contract.dimensionKey()).thenReturn("main");
        SavedBiomeChunk.Cell cell = new SavedBiomeChunk.Cell(1L, "forest", "main");
        SavedBiomeChunk.Cell repeatedActivation = new SavedBiomeChunk.Cell(2L, "forest", "main");
        SavedBiomeChunk.Cell removed = new SavedBiomeChunk.Cell(2L, "removed", "main");
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(0, 0, 2L, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SavedBiomeChunk.Cell selected = x < 8 ? cell : x < 12 ? repeatedActivation : removed;
                builder.column(x, z, new SavedBiomeChunk.Column(selected, cell,
                        List.of(new SavedBiomeChunk.Span(-64, 320, selected))));
            }
        }
        when(store.get(0, 0)).thenReturn(Optional.of(builder.build()));
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history)) {
            assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.resolve(0, 0, 0, true)).isLoading());
            awaitIdle(runtime);
            runtime.prepareChunk(0, 0);
            BiomeEnvironment environment = runtime.resolve(0, 0, 0, true).orElseThrow();
            BiomeEnvironment repeated = runtime.resolve(8, 0, 0, true).orElseThrow();
            assertEquals(2L, repeated.activationId());
            assertSame(environment.data(), repeated.data());
            SavedBiomeUnavailableException missing = assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.resolve(12, 0, 0, true));
            assertEquals(false, missing.isLoading());
            assertTrue(missing.getMessage().contains("removed"));
            assertSame(environment, runtime.resolveCaveBase(12, 0).orElseThrow());
            environment.data().getBiomeLoader().unload("forest");
            environment.data().getRegionLoader().unload("main");
            Files.delete(pack.resolve("biomes/forest.json"));
            Files.delete(pack.resolve("regions/main.json"));
            assertSame(environment, runtime.resolve(0, 0, 0, true).orElseThrow());
            assertEquals("forest", environment.biome().getLoadKey());
            assertSame(environment, runtime.readSurfaceBiome(0, 0, Optional::orElseThrow));
        }
    }

    @Test
    public void backgroundPreviewWaitsForTheReadWithoutRepeatingTheQuery() throws Exception {
        allowUnownedChunks();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.get(0, 0)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.empty();
        });
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Boolean> rendered = caller.submit(() -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> rendered.get(100L, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertTrue(rendered.get(5L, TimeUnit.SECONDS));
            verify(store).get(0, 0);
        }
    }

    @Test
    public void backgroundPreviewRetainsItsResultAcrossQueryCacheEviction() throws Exception {
        allowUnownedChunks();
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Field field = SavedBiomeRuntime.class.getDeclaredField("consumption");
            field.setAccessible(true);
            ReentrantReadWriteLock consumption = (ReentrantReadWriteLock) field.get(runtime);
            consumption.writeLock().lock();
            Future<Boolean> rendered = caller.submit(() -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
                while (runtime.cachedQueryCount() == 0 && System.nanoTime() < deadline) {
                    Thread.sleep(1L);
                }
                assertEquals(1, runtime.cachedQueryCount());
                for (int chunkX = 1; chunkX <= 130; chunkX++) {
                    int requestedX = chunkX;
                    assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                            () -> runtime.prepareChunk(requestedX, 0)).isLoading());
                    awaitIdle(runtime);
                }
                assertThrows(TimeoutException.class, () -> rendered.get(100L, TimeUnit.MILLISECONDS));
            } finally {
                consumption.writeLock().unlock();
            }
            assertTrue(rendered.get(5L, TimeUnit.SECONDS));
            verify(store).get(0, 0);
        }
    }

    @Test
    public void captureRefreshesThePendingPreviewWithoutAddingAnotherSlot() throws Exception {
        allowUnownedChunks();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger readCount = new AtomicInteger();
        when(store.get(0, 0)).thenAnswer(invocation -> {
            if (readCount.getAndIncrement() == 0) {
                entered.countDown();
                assertTrue(release.await(5L, TimeUnit.SECONDS));
            }
            return Optional.empty();
        });
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Boolean> rendered = caller.submit(() -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                runtime.capture(unresolvedChunk(1));
                assertEquals(1, runtime.pendingQueryCount());
            } finally {
                release.countDown();
            }
            assertTrue(rendered.get(5L, TimeUnit.SECONDS));
            verify(store, times(2)).get(0, 0);
        }
    }

    @Test
    public void closeWaitsForBackgroundConsumptionBeforeClosingDefinitions() throws Exception {
        allowUnownedChunks();
        CountDownLatch consuming = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> rendered = callers.submit(() -> runtime.readSurfaceBiome(0, 0, environment -> {
                consuming.countDown();
                try {
                    assertTrue(release.await(5L, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return environment.isEmpty();
            }));
            assertTrue(consuming.await(5L, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(runtime::close);
            try {
                assertThrows(TimeoutException.class, () -> closing.get(100L, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertTrue(rendered.get(5L, TimeUnit.SECONDS));
            closing.get(5L, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, () -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    @Test
    public void backgroundPreviewPreservesActualReadFailure() throws Exception {
        IOException failure = new IOException("Saved biome read failed");
        when(store.get(0, 0)).thenThrow(failure);
        try (SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history)) {
            SavedBiomeUnavailableException unavailable = assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            assertEquals(false, unavailable.isLoading());
            assertSame(failure, unavailable.getCause());
        }
    }

    @Test
    public void closeCompletesWaitingPreviewWithoutConsumingClosedDefinitions() throws Exception {
        allowUnownedChunks();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.get(0, 0)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.empty();
        });
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        AtomicInteger consumed = new AtomicInteger();
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            Future<Integer> rendered = callers.submit(() -> runtime.readSurfaceBiome(0, 0,
                    environment -> consumed.incrementAndGet()));
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(runtime::close);
            try {
                assertThrows(TimeoutException.class, () -> closing.get(100L, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            ExecutionException unavailable = assertThrows(ExecutionException.class,
                    () -> rendered.get(5L, TimeUnit.SECONDS));
            assertTrue(unavailable.getCause() instanceof IllegalStateException);
            assertEquals(0, consumed.get());
            closing.get(5L, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    @Test
    public void fullReadQueueLeavesThePreviewPendingWithoutAddingWork() throws Exception {
        allowUnownedChunks();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(store.get(anyInt(), anyInt())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.empty();
        });
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        try {
            for (int chunkX = 0; chunkX < 256; chunkX++) {
                int requestedX = chunkX;
                assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                        () -> runtime.prepareChunk(requestedX, 0)).isLoading());
            }
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertEquals(256, runtime.pendingQueryCount());
            assertTrue(assertThrows(SavedBiomeUnavailableException.class,
                    () -> runtime.readSurfaceBiome(256 * 16, 0, Optional::isEmpty)).isLoading());
            assertEquals(256, runtime.pendingQueryCount());
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    @Test
    public void interruptingPreviewDoesNotCancelTheSharedRead() throws Exception {
        allowUnownedChunks();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.get(0, 0)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return Optional.empty();
        });
        SavedBiomeRuntime runtime = new SavedBiomeRuntime(engine, history);
        try (ExecutorService caller = Executors.newSingleThreadExecutor()) {
            Future<Boolean> rendered = caller.submit(() -> runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                assertTrue(rendered.cancel(true));
                assertEquals(1, runtime.pendingQueryCount());
            } finally {
                release.countDown();
            }
            assertTrue(runtime.readSurfaceBiome(0, 0, Optional::isEmpty));
            verify(store).get(0, 0);
        } finally {
            release.countDown();
            runtime.close();
        }
    }

    private void allowUnownedChunks() throws IOException {
        when(store.get(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(true);
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
    }

    private static void awaitIdle(SavedBiomeRuntime runtime) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (runtime.pendingQueryCount() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertEquals(0, runtime.pendingQueryCount());
    }

    private static SavedBiomeChunk unresolvedChunk(int runCount) {
        SavedBiomeChunk.Cell first = SavedBiomeChunk.Cell.unresolved(1L);
        SavedBiomeChunk.Cell second = SavedBiomeChunk.Cell.unresolved(2L);
        ArrayList<SavedBiomeChunk.Span> spans = new ArrayList<>(runCount);
        for (int index = 0; index < runCount; index++) {
            int start = -64 + index * 384 / runCount;
            int end = -64 + (index + 1) * 384 / runCount;
            spans.add(new SavedBiomeChunk.Span(start, end, (index & 1) == 0 ? first : second));
        }
        return chunk(new SavedBiomeChunk.Column(first, second, spans));
    }

    private static SavedBiomeChunk chunk(SavedBiomeChunk.Column column) {
        SavedBiomeChunk.Builder builder = SavedBiomeChunk.builder(new SavedBiomeChunk.Header(0, 0, 2L, -64, 384));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                builder.column(x, z, column);
            }
        }
        return builder.build();
    }
}
