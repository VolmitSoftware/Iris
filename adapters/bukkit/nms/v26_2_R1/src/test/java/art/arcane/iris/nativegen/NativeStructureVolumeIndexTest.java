package art.arcane.iris.nativegen;

import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.util.collection.KList;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureVolumeIndexTest {
    private static final String STRUCTURE_KEY = "minecraft:swamp_hut";

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void assembledPiecesBecomeWorldSpacePieceVolumes() {
        StructureStart start = swampHut(0, 0, 4, 6);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();

        KList<NativeStructureVolume> volumes = NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, start);

        assertEquals(1, volumes.size());
        NativeStructureVolume volume = volumes.getFirst();
        assertEquals(STRUCTURE_KEY, volume.structure());
        assertEquals(bounds.minX(), volume.minX());
        assertEquals(bounds.minY(), volume.minY());
        assertEquals(bounds.minZ(), volume.minZ());
        assertEquals(bounds.maxX(), volume.maxX());
        assertEquals(bounds.maxY(), volume.maxY());
        assertEquals(bounds.maxZ(), volume.maxZ());
    }

    @Test
    public void invalidStartsContributeNoVolumes() {
        assertNull(NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, StructureStart.INVALID_START));
        assertNull(NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, null));
    }

    @Test
    public void coldAndCachedQueriesResolveIdenticalVolumes() {
        CountingResolver resolver = new CountingResolver(0, 0);
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);

        KList<NativeStructureVolume> cold = index.resolve(null, 0, 0, 15, 15);
        int coldResolutions = resolver.resolutions();
        KList<NativeStructureVolume> cached = index.resolve(null, 0, 0, 15, 15);

        assertTrue(coldResolutions > 0);
        assertFalse(cold.isEmpty());
        assertEquals(cold, cached);
        assertEquals(coldResolutions, resolver.resolutions());
    }

    @Test
    public void runtimeBucketsStayIsolatedAndRetirementEvictsOnlyTheirEntries() {
        AtomicInteger runtimeId = new AtomicInteger(1);
        Engine engine = runtimeEngine(runtimeId);
        CountingResolver resolver = new CountingResolver(0, 0);
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);

        index.resolve(engine, 0, 0, 15, 15);
        assertEquals(289, resolver.resolutions());
        index.resolve(engine, 0, 0, 15, 15);
        assertEquals(289, resolver.resolutions());

        runtimeId.set(2);
        index.resolve(engine, 0, 0, 15, 15);
        assertEquals(578, resolver.resolutions());
        index.resolve(engine, 0, 0, 15, 15);
        assertEquals(578, resolver.resolutions());

        index.evictRuntime(1);
        runtimeId.set(1);
        index.resolve(engine, 0, 0, 15, 15);
        assertEquals(867, resolver.resolutions());
    }

    @Test
    public void pinnedRuntimeReusesCachedOriginsWithoutOpeningCoordinateScopes() throws Exception {
        ScopedEngine scoped = scopedEngine(true, 1);
        CountingResolver resolver = new CountingResolver(0, 0);
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);

        KList<NativeStructureVolume> first = index.resolve(scoped.engine(), 0, 0, 15, 15);
        assertEquals(289, scoped.scopes().get());
        index.resolve(scoped.engine(), 16, 0, 31, 15);
        assertEquals(306, resolver.resolutions());
        assertEquals(306, scoped.scopes().get());
        assertEquals(first, index.resolve(scoped.engine(), 0, 0, 15, 15));
        assertEquals(306, scoped.scopes().get());
    }

    @Test
    public void absentOrMismatchedRuntimeOwnershipKeepsCoordinateRouting() throws Exception {
        ScopedEngine detached = scopedEngine(true, 1);
        when(detached.engine().getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        for (ScopedEngine scoped : List.of(scopedEngine(false, 1), scopedEngine(true, 0),
                scopedEngine(true, 2), detached)) {
            NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));
            index.resolve(scoped.engine(), 0, 0, 15, 15);
            index.resolve(scoped.engine(), 16, 0, 31, 15);
            assertEquals(578, scoped.scopes().get());
        }
    }

    @Test
    public void retiredPinnedOriginsRebuildThroughCoordinateScopes() throws Exception {
        ScopedEngine scoped = scopedEngine(true, 1);
        CountingResolver resolver = new CountingResolver(0, 0);
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);

        KList<NativeStructureVolume> first = index.resolve(scoped.engine(), 0, 0, 15, 15);
        index.evictRuntime(1);
        assertEquals(first, index.resolve(scoped.engine(), 0, 0, 15, 15));
        assertEquals(578, resolver.resolutions());
        assertEquals(578, scoped.scopes().get());
    }

    @Test
    public void invalidatedIndexReceivesRuntimeRetirementAndDetachesOnUninstall() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        Set<IntConsumer> listeners = new CopyOnWriteArraySet<>();
        doAnswer(invocation -> {
            listeners.add(invocation.getArgument(0));
            return null;
        }).when(engine).addGenerationRuntimeRetirementListener(any());
        doAnswer(invocation -> {
            listeners.remove(invocation.getArgument(0));
            return null;
        }).when(engine).removeGenerationRuntimeRetirementListener(any());
        NativeStructureVolumeIndex.Context context = new NativeStructureVolumeIndex.Context(
                null, null, null, null, () -> null, () -> null, () -> null);
        NativeStructureVolumeIndex.install(engine, context);
        try {
            assertEquals(1, listeners.size());
            IntConsumer original = listeners.iterator().next();
            NativeStructureVolumeIndex.invalidate(engine);
            assertEquals(1, listeners.size());
            assertFalse(listeners.contains(original));

            NativeStructureVolumeIndex.volumes(engine, 0, 0, 15, 15);
            NativeStructureVolumeIndex current = installedIndexes().get(engine);
            assertEquals(289, cache(current, "originCache").size());
            assertEquals(1, cache(current, "queryCache").size());
            for (IntConsumer listener : listeners) {
                listener.accept(0);
            }
            assertTrue(cache(current, "originCache").isEmpty());
            assertTrue(cache(current, "queryCache").isEmpty());
        } finally {
            NativeStructureVolumeIndex.uninstall(engine);
        }
        assertTrue(listeners.isEmpty());
    }

    @Test
    public void retiringOriginBuildCannotRepublishWhileQueryEvictionWaits() throws Exception {
        BlockingResolver resolver = new BlockingResolver();
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<?, ?> origins = cache(index, "originCache");
        Map<?, ?> queries = cache(index, "queryCache");
        try {
            Future<KList<NativeStructureVolume>> builder = executor.submit(() -> index.originVolumes(null, 0, 0));
            assertTrue(resolver.awaitFirstEntry());
            index.originVolumes(null, 1, 0);
            assertEquals(1, origins.size());
            Future<?> retirement;
            synchronized (queries) {
                retirement = executor.submit(() -> index.evictRuntime(0));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean cleared = false;
                while (System.nanoTime() < deadline) {
                    synchronized (origins) {
                        cleared = origins.isEmpty();
                    }
                    if (cleared) {
                        break;
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L));
                }
                assertTrue("Origin eviction did not complete before the query cache lock", cleared);
                resolver.release();
                assertTrue(builder.get(5, TimeUnit.SECONDS).isEmpty());
                synchronized (origins) {
                    assertTrue("The retired in-flight origin was cached again", origins.isEmpty());
                }
            }
            retirement.get(5, TimeUnit.SECONDS);
            index.originVolumes(null, 0, 0);
            assertEquals(3, resolver.resolutions());
        } finally {
            resolver.release();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void independentIndexesResolveIdenticalVolumesFromTheSameSeed() {
        NativeStructureVolumeIndex first = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));
        NativeStructureVolumeIndex second = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));

        assertEquals(first.resolve(null, 0, 0, 15, 15), second.resolve(null, 0, 0, 15, 15));
        assertEquals(first.resolve(null, -32, -32, 47, 47), second.resolve(null, -32, -32, 47, 47));
    }

    @Test
    public void volumesOutsideTheQueryRectAreExcluded() {
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(new CountingResolver(0, 0));
        BoundingBox bounds = swampHut(0, 0, 4, 6).getPieces().getFirst().getBoundingBox();

        KList<NativeStructureVolume> hit = index.resolve(null, bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
        KList<NativeStructureVolume> miss = index.resolve(null,
                bounds.maxX() + 1, bounds.minZ(), bounds.maxX() + 8, bounds.maxZ());

        assertEquals(1, hit.size());
        assertTrue(miss.isEmpty());
    }

    @Test
    public void startsWithinTheOriginReachContributeAndBeyondItDoNot() {
        int reach = NativeStructureVolumeIndex.originReachChunks();
        NativeStructureVolumeIndex reachable = NativeStructureVolumeIndex.forTesting(new CountingResolver(reach, 0));
        NativeStructureVolumeIndex unreachable = NativeStructureVolumeIndex.forTesting(new CountingResolver(reach + 1, 0));

        assertFalse(reachable.resolve(null, 0, 0, 15, 15).isEmpty());
        assertTrue(unreachable.resolve(null, 0, 0, 15, 15).isEmpty());
    }

    @Test
    public void overlappingColdQueriesCoordinateBeforeResolvingOrigins() throws Exception {
        BlockingResolver resolver = new BlockingResolver();
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<KList<NativeStructureVolume>> first = executor.submit(() -> index.resolve(null, 0, 0, 15, 15));
            assertTrue(resolver.awaitFirstEntry());
            Future<KList<NativeStructureVolume>> adjacent = executor.submit(() -> index.resolve(null, 16, 0, 31, 15));

            assertFalse(resolver.awaitSecondEntry(250));
            resolver.release();
            assertTrue(first.get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(adjacent.get(5, TimeUnit.SECONDS).isEmpty());
            assertEquals(306, resolver.resolutions());
        } finally {
            resolver.release();
            executor.shutdownNow();
        }
    }

    @Test
    public void distantColdQueriesCanResolveOriginsConcurrently() throws Exception {
        BlockingResolver resolver = new BlockingResolver();
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(resolver);
        int distantChunkX = findDisjointWindow(0, 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<KList<NativeStructureVolume>> first = executor.submit(() -> index.resolve(null, 0, 0, 15, 15));
            assertTrue(resolver.awaitFirstEntry());
            Future<KList<NativeStructureVolume>> distant = executor.submit(
                    () -> index.resolve(null, distantChunkX << 4, 0, (distantChunkX << 4) + 15, 15));

            assertTrue(resolver.awaitSecondEntry(5_000));
            resolver.release();
            assertTrue(first.get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(distant.get(5, TimeUnit.SECONDS).isEmpty());
        } finally {
            resolver.release();
            executor.shutdownNow();
        }
    }

    @Test
    public void coordinatedResolutionPreservesCanonicalOriginOrder() {
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting(new OrderedResolver());

        KList<NativeStructureVolume> volumes = index.resolve(null, 0, 0, 15, 15);

        assertEquals(289, volumes.size());
        assertEquals("-8:-8", volumes.getFirst().structure());
        assertEquals("0:0", volumes.get(144).structure());
        assertEquals("8:8", volumes.getLast().structure());
    }

    private static ScopedEngine scopedEngine(boolean scoped, int ownerRuntimeId) throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(engine.hasGenerationRuntimeScope()).thenReturn(scoped);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(engine.getCacheID()).thenReturn(1);
        if (ownerRuntimeId != 0) {
            IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
            when(binding.runtimeId()).thenReturn(ownerRuntimeId);
            when(router.currentRuntimeOwnership()).thenReturn(Optional.of(
                    new GenerationHistoryRuntimeRouter.RuntimeOwnership(1L, binding)));
        }
        AtomicInteger scopes = new AtomicInteger();
        when(engine.openGenerationHistoryCoordinateScope(anyInt(), anyInt())).thenAnswer(invocation -> {
            scopes.incrementAndGet();
            return null;
        });
        return new ScopedEngine(engine, scopes);
    }

    @SuppressWarnings("unchecked")
    private static Map<Engine, NativeStructureVolumeIndex> installedIndexes() throws Exception {
        Field indexes = NativeStructureVolumeIndex.class.getDeclaredField("INDEXES");
        indexes.setAccessible(true);
        return (Map<Engine, NativeStructureVolumeIndex>) indexes.get(null);
    }

    private static Map<?, ?> cache(NativeStructureVolumeIndex index, String name) throws Exception {
        Field field = NativeStructureVolumeIndex.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(index);
    }

    private static StructureStart swampHut(int chunkX, int chunkZ, int x, int z) {
        Structure source = new SwampHutStructure(new Structure.StructureSettings(HolderSet.empty()));
        SwampHutPiece piece = new SwampHutPiece(RandomSource.create(17L), x, z);
        return new StructureStart(source, new ChunkPos(chunkX, chunkZ), 0, new PiecesContainer(List.of(piece)));
    }

    private static Engine runtimeEngine(AtomicInteger runtimeId) {
        return (Engine) Proxy.newProxyInstance(
                Engine.class.getClassLoader(),
                new Class<?>[]{Engine.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getCacheID")) {
                        return runtimeId.get();
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == arguments[0];
                    }
                    if (method.getName().equals("toString")) {
                        return "runtime-engine-" + runtimeId.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static int findDisjointWindow(int chunkX, int chunkZ) {
        long sourceMask = NativeStructureVolumeIndex.originWindowStripeMask(chunkX, chunkZ);
        for (int candidate = 64; candidate < 16_384; candidate += 17) {
            if ((sourceMask & NativeStructureVolumeIndex.originWindowStripeMask(candidate, chunkZ)) == 0L) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find a disjoint native structure window stripe");
    }

    private static final class CountingResolver implements NativeStructureVolumeIndex.OriginResolver {
        private final AtomicInteger resolutions = new AtomicInteger();
        private final int originChunkX;
        private final int originChunkZ;

        private CountingResolver(int originChunkX, int originChunkZ) {
            this.originChunkX = originChunkX;
            this.originChunkZ = originChunkZ;
        }

        private int resolutions() {
            return resolutions.get();
        }

        @Override
        public KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ) {
            resolutions.incrementAndGet();
            if (chunkX != originChunkX || chunkZ != originChunkZ) {
                return NativeStructureVolume.NONE;
            }
            return NativeStructureVolumeIndex.appendPieces(null, STRUCTURE_KEY, swampHut(chunkX, chunkZ, 4, 6));
        }
    }

    private static final class BlockingResolver implements NativeStructureVolumeIndex.OriginResolver {
        private final AtomicInteger resolutions = new AtomicInteger();
        private final CountDownLatch firstEntry = new CountDownLatch(1);
        private final CountDownLatch secondEntry = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private int resolutions() {
            return resolutions.get();
        }

        private boolean awaitFirstEntry() throws InterruptedException {
            return firstEntry.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitSecondEntry(long timeoutMillis) throws InterruptedException {
            return secondEntry.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        private void release() {
            release.countDown();
        }

        @Override
        public KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ) {
            int resolution = resolutions.incrementAndGet();
            if (resolution == 1) {
                firstEntry.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release native structure resolution");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while coordinating native structure resolution", e);
                }
            } else if (resolution == 2) {
                secondEntry.countDown();
            }
            return NativeStructureVolume.NONE;
        }
    }

    private static final class OrderedResolver implements NativeStructureVolumeIndex.OriginResolver {
        @Override
        public KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ) {
            return new KList<>(new NativeStructureVolume(
                    chunkX + ":" + chunkZ,
                    0, 0, 0,
                    15, 255, 15));
        }
    }
    private record ScopedEngine(IrisEngine engine, AtomicInteger scopes) {
    }
}
