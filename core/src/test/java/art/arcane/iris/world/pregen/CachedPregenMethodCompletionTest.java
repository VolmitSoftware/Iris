package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.bukkit.World;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CachedPregenMethodCompletionTest {
    private File directory;
    private PregenCache cache;
    private CapturingMethod underlying;
    private PregenTask task;
    private CachedPregenMethod method;
    private RecordingListener listener;
    private Set<String> savedFull;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("iris-pregen-cache-test").toFile();
        cache = PregenCache.create(directory);
        underlying = new CapturingMethod();
        task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(256)
                .radiusZ(256)
                .build();
        savedFull = new HashSet<>();
        method = new CachedPregenMethod(new CachedPregenMethod.Configuration(underlying, cache, task,
                new PregenSavedChunkStatus((x, z) -> savedFull.contains(x + "," + z))));
        listener = new RecordingListener();
    }

    @Test
    public void generateChunkDoesNotCacheOnSubmitOnlyOnCompletion() {
        method.generateChunk(3, 4, listener);

        assertEquals(1, underlying.generateChunkCalls.get());
        assertFalse(cache.isChunkCached(3, 4));

        underlying.capturedListener.get().onChunkGenerated(3, 4, false);

        assertTrue(cache.isChunkCached(3, 4));
        assertEquals(1, listener.generated.get());
    }

    @Test
    public void generateChunkFailureIsNotCached() {
        method.generateChunk(6, 9, listener);
        underlying.capturedListener.get().onChunkFailed(6, 9);

        assertFalse(cache.isChunkCached(6, 9));
        assertEquals(0, listener.generated.get());
        assertEquals(1, listener.failed.get());
    }

    @Test
    public void generateChunkSkipsUnderlyingMethodWhenCached() {
        method.generateChunk(1, 2, listener);
        underlying.capturedListener.get().onChunkGenerated(1, 2, false);
        assertEquals(1, underlying.generateChunkCalls.get());

        savedFull.add("1,2");
        method.generateChunk(1, 2, listener);

        assertEquals(1, underlying.generateChunkCalls.get());
        assertEquals(2, listener.generated.get());
        assertEquals(1, listener.generatedCached.get());
    }

    @Test
    public void generateRegionReplayRespectsTaskBounds() {
        cache.cacheRegion(0, 0);
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                savedFull.add(x + "," + z);
            }
        }
        List<long[]> expected = new ArrayList<>();
        task.iterateChunks(0, 0, (x, z) -> expected.add(new long[]{x, z}));
        assertTrue(expected.size() < 1024);

        method.generateRegion(0, 0, listener);

        assertEquals(0, underlying.generateRegionCalls.get());
        assertEquals(expected.size(), listener.generated.get());
        assertEquals(expected.size(), listener.generatedCached.get());
    }

    @Test
    public void pregenStartReachesTheSelectedMethodThroughEveryWrapper() throws Exception {
        Constructor<AsyncOrMedievalPregenMethod> selectorConstructor = AsyncOrMedievalPregenMethod.class
                .getDeclaredConstructor(PregeneratorMethod.class);
        selectorConstructor.setAccessible(true);
        AsyncOrMedievalPregenMethod selector = selectorConstructor.newInstance(underlying);
        Constructor<HybridPregenMethod> hybridConstructor = HybridPregenMethod.class
                .getDeclaredConstructor(World.class, PregeneratorMethod.class);
        hybridConstructor.setAccessible(true);
        HybridPregenMethod hybrid = hybridConstructor.newInstance(null, selector);
        CachedPregenMethod wrapped = new CachedPregenMethod(new CachedPregenMethod.Configuration(hybrid, cache, task,
                new PregenSavedChunkStatus((x, z) -> false)));

        wrapped.onPregenStart(123, -456);

        assertEquals(1, underlying.pregenStartCalls.get());
        assertEquals(123, underlying.centerBlockX.get());
        assertEquals(-456, underlying.centerBlockZ.get());
    }

    @Test
    public void closePersistsCacheAfterTheNativeBackendCloses() {
        PregenCache trackedCache = mock(PregenCache.class);
        when(trackedCache.sync()).thenReturn(trackedCache);
        PregeneratorMethod trackedMethod = mock(PregeneratorMethod.class);
        CachedPregenMethod wrapped = new CachedPregenMethod(new CachedPregenMethod.Configuration(
                trackedMethod, trackedCache, task, new PregenSavedChunkStatus((x, z) -> false)));
        wrapped.close();
        InOrder order = inOrder(trackedMethod, trackedCache);
        order.verify(trackedCache).sync();
        order.verify(trackedMethod).close();
        order.verify(trackedCache).write();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void staleChunkBitRequiresNativeCompletionAgain() {
        cache.cacheChunk(126, 103);
        method.generateChunk(126, 103, listener);
        assertEquals(1, underlying.generateChunkCalls.get());
        assertEquals(0, listener.generated.get());
        underlying.capturedListener.get().onChunkGenerated(126, 103, false);
        assertEquals(1, listener.generated.get());
        assertEquals(0, listener.generatedCached.get());
    }

    @Test
    public void staleRegionBitCannotSkipPartialNativeChunks() {
        cache.cacheRegion(0, 0);
        assertFalse(method.supportsRegions(0, 0, listener));
        method.generateRegion(0, 0, listener);
        AtomicInteger expected = new AtomicInteger();
        task.iterateChunks(0, 0, (x, z) -> expected.incrementAndGet());
        assertEquals(expected.get(), underlying.generateChunkCalls.get());
        assertEquals(0, underlying.generateRegionCalls.get());
        assertEquals(0, listener.generated.get());
    }

    @Test
    public void regionSubmissionDoesNotPublishWholeRegionCompletion() {
        underlying.regionSupport = true;
        method.generateRegion(0, 0, listener);
        assertFalse(cache.isRegionCached(0, 0));
        assertFalse(cache.isChunkCached(1, 2));
        underlying.capturedListener.get().onChunkGenerated(1, 2, false);
        assertTrue(cache.isChunkCached(1, 2));
        assertFalse(cache.isChunkCached(31, 31));
    }

    private static final class CapturingMethod implements PregeneratorMethod {
        private boolean regionSupport;
        private final AtomicInteger generateChunkCalls = new AtomicInteger();
        private final AtomicInteger generateRegionCalls = new AtomicInteger();
        private final AtomicInteger pregenStartCalls = new AtomicInteger();
        private final AtomicInteger centerBlockX = new AtomicInteger();
        private final AtomicInteger centerBlockZ = new AtomicInteger();
        private final AtomicReference<PregenListener> capturedListener = new AtomicReference<>();

        @Override
        public void init() {
        }

        @Override
        public void close() {
        }

        @Override
        public void save() {
        }

        @Override
        public boolean supportsRegions(int x, int z, PregenListener listener) {
            return regionSupport;
        }

        @Override
        public String getMethod(int x, int z) {
            return "capture";
        }

        @Override
        public void generateRegion(int x, int z, PregenListener listener) {
            generateRegionCalls.incrementAndGet();
            capturedListener.set(listener);
        }

        @Override
        public void generateChunk(int x, int z, PregenListener listener) {
            generateChunkCalls.incrementAndGet();
            capturedListener.set(listener);
        }

        @Override
        public void onPregenStart(int centerBlockX, int centerBlockZ) {
            pregenStartCalls.incrementAndGet();
            this.centerBlockX.set(centerBlockX);
            this.centerBlockZ.set(centerBlockZ);
        }

        @Override
        public Mantle getMantle() {
            return null;
        }
    }

    private static final class RecordingListener implements PregenListener {
        private final AtomicInteger generated = new AtomicInteger();
        private final AtomicInteger generatedCached = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();

        @Override
        public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generated, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
        }

        @Override
        public void onChunkGenerating(int x, int z) {
        }

        @Override
        public void onChunkGenerated(int x, int z, boolean cached) {
            generated.incrementAndGet();
            if (cached) {
                generatedCached.incrementAndGet();
            }
        }

        @Override
        public void onChunkFailed(int x, int z) {
            failed.incrementAndGet();
        }

        @Override
        public void onRegionGenerated(int x, int z) {
        }

        @Override
        public void onRegionGenerating(int x, int z) {
        }

        @Override
        public void onChunkCleaned(int x, int z) {
        }

        @Override
        public void onRegionSkipped(int x, int z) {
        }

        @Override
        public void onNetworkStarted(int x, int z) {
        }

        @Override
        public void onNetworkFailed(int x, int z) {
        }

        @Override
        public void onNetworkReclaim(int revert) {
        }

        @Override
        public void onNetworkGeneratedChunk(int x, int z) {
        }

        @Override
        public void onNetworkDownloaded(int x, int z) {
        }

        @Override
        public void onClose() {
        }

        @Override
        public void onSaving() {
        }

        @Override
        public void onChunkExistsInRegionGen(int x, int z) {
        }
    }
}
