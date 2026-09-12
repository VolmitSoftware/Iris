package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeStructureOwnershipStoreTest {
    private static final String FINGERPRINT = "34".repeat(32);

    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        PlatformBlockState air = mock(PlatformBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    @Test
    public void fullReferenceEnvelopeWritesOnlyItsOriginAuthority() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record("test:origin_only", 4, -7, 91L);

        state.record(record);

        assertEquals(1, storage.chunks.size());
        assertEquals(record, storage.find(
                NativeStructureOwnershipStore.pack(4, -7), record));
        assertNull(storage.find(
                NativeStructureOwnershipStore.pack(4, -6), record));
        assertEquals(record, state.find(
                4, -6, record.structureKey(), record.originChunkX(), record.originChunkZ()));
    }

    @Test
    public void originAuthorityIgnoresAStaleTargetReplicaWhenOnlyPolicyChanged() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        NativeStructureOwnershipRecord stale = record("test:replacement", 2, 3, 11L,
                new IrisStructureTerrain().setHorizontalPadding(2), 8);
        NativeStructureOwnershipRecord current = record("test:replacement", 2, 3, 11L,
                new IrisStructureTerrain().setHorizontalPadding(9), 8);
        long origin = NativeStructureOwnershipStore.pack(2, 3);
        long target = NativeStructureOwnershipStore.pack(3, 4);
        storage.write(origin, current);
        storage.write(target, stale);
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);

        NativeStructureOwnershipRecord resolved = state.find(
                3, 4, current.structureKey(), 2, 3);

        assertEquals(current, resolved);
        assertEquals(9, resolved.restoredDecision().terrain().getHorizontalPadding());
        assertEquals(stale.contentFingerprint(), resolved.contentFingerprint());
        assertEquals(stale, storage.find(target, stale));
    }

    @Test
    public void persistedOriginAuthorityRemainsVisibleOutsideItsReferenceEnvelope() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:narrow_authority", 8, -3, 19L, 1);
        state.record(record);

        assertNull(state.find(
                10, -3, record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(record, state.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
    }

    @Test
    public void staleTargetReplicaCannotReplaceAMissingOriginAuthority() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        NativeStructureOwnershipRecord stale = record("test:deleted", -2, 5, 17L);
        storage.write(NativeStructureOwnershipStore.pack(-1, 5), stale);
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);

        assertNull(state.find(-1, 5, stale.structureKey(), -2, 5));
    }

    @Test
    public void denseOverlappingEnvelopesDoNotAmplifyIntoTheTargetChunk() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        int records = 0;
        for (int chunkX = -8; chunkX <= 8; chunkX++) {
            for (int chunkZ = -8; chunkZ <= 8; chunkZ++) {
                NativeStructureOwnershipRecord record = record(
                        "test:dense_" + chunkX + "_" + chunkZ,
                        chunkX, chunkZ, records);
                state.record(record);
                assertEquals(record, state.find(
                        0, 0, record.structureKey(), chunkX, chunkZ));
                records++;
            }
        }

        assertEquals(289, records);
        assertEquals(289, storage.chunks.size());
        NativeStructureOwnershipBundle target = storage.chunks.get(
                NativeStructureOwnershipStore.pack(0, 0));
        assertEquals(1, target.records().size());
    }

    @Test
    public void flushWaitsForAnOriginWriteAlreadyInFlight() throws Exception {
        Engine engine = engine();
        BlockingStorage storage = new BlockingStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record("test:flush_race", -4, 8, 42L);
        storage.blockedTarget = NativeStructureOwnershipStore.pack(-4, 8);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> recording = callers.submit(() -> state.record(record));
            assertTrue(storage.writeEntered.await(5, TimeUnit.SECONDS));
            Future<?> flushing = callers.submit(state::flush);

            assertThrows(TimeoutException.class,
                    () -> flushing.get(100, TimeUnit.MILLISECONDS));
            assertEquals(0, storage.flushes);
            storage.allowWrite.countDown();
            recording.get(5, TimeUnit.SECONDS);
            flushing.get(5, TimeUnit.SECONDS);
            assertEquals(1, storage.flushes);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void closeWaitsForAnOriginWriteAlreadyInFlight() throws Exception {
        Engine engine = engine();
        BlockingStorage storage = new BlockingStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record("test:close_race", 6, -9, 73L);
        storage.blockedTarget = NativeStructureOwnershipStore.pack(6, -9);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> recording = callers.submit(() -> state.record(record));
            assertTrue(storage.writeEntered.await(5, TimeUnit.SECONDS));
            Future<?> closing = callers.submit(state::close);

            assertThrows(TimeoutException.class,
                    () -> closing.get(100, TimeUnit.MILLISECONDS));
            storage.allowWrite.countDown();
            recording.get(5, TimeUnit.SECONDS);
            closing.get(5, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void closingEngineAllowsFirstOwnershipWriteUntilExplicitClose() {
        Engine engine = engine();
        TestStorage storage = new TestStorage();
        when(engine.isClosing()).thenReturn(true);
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:closing_session", -7, 12, 74L);

        state.record(record);

        assertEquals(record, state.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        state.close();
        assertThrows(IllegalStateException.class, () -> state.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
    }

    @Test
    public void explicitCloseCreatesAndRetainsAClosedOwnershipState() {
        Engine engine = engine();
        when(engine.isClosing()).thenReturn(true);

        NativeStructureOwnershipStore.close(engine);

        assertThrows(IllegalStateException.class, () ->
                NativeStructureOwnershipStore.findPersisted(
                        engine, "test:first_closing_use", 0, 0));
    }

    @Test
    public void sameOriginWritesCannotPublishCacheAuthorityOutOfPersistenceOrder() throws Exception {
        Engine engine = engine();
        PostWriteBlockingStorage storage = new PostWriteBlockingStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord first = record("test:same_origin", 3, -6, 1L);
        NativeStructureOwnershipRecord second = record("test:same_origin", 3, -6, 2L);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWrite = callers.submit(() -> state.record(first));
            assertTrue(storage.firstPersisted.await(5, TimeUnit.SECONDS));
            Future<?> secondWrite = callers.submit(() -> state.record(second));

            assertThrows(TimeoutException.class,
                    () -> secondWrite.get(100, TimeUnit.MILLISECONDS));
            storage.allowFirstReturn.countDown();
            firstWrite.get(5, TimeUnit.SECONDS);
            secondWrite.get(5, TimeUnit.SECONDS);

            assertEquals(second, state.find(
                    3, -6, second.structureKey(),
                    second.originChunkX(), second.originChunkZ()));
            assertEquals(second, storage.find(
                    NativeStructureOwnershipStore.pack(3, -6), second));
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void successfulAutosaveFlushSurvivesAbruptStateLoss() {
        Engine engine = engine();
        CrashableStorage storage = new CrashableStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:autosave_durable", -11, 14, 101L);

        state.record(record);
        state.flush();
        storage.crash();

        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(1, storage.flushes);
    }

    @Test
    public void failedFlushRemainsDirtyForSuccessfulRetry() {
        Engine engine = engine();
        FailingFlushStorage storage = new FailingFlushStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:flush_retry", 12, -15, 102L);

        state.record(record);
        assertThrows(IllegalStateException.class, state::flush);
        state.flush();
        storage.crash();

        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(2, storage.flushes);
    }

    @Test
    public void deferredFlushRemainsDirtyForSuccessfulRetryWithoutFailingAutosave() {
        Engine engine = engine();
        DeferringFlushStorage storage = new DeferringFlushStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:flush_deferred", 13, -16, 107L);

        state.record(record);
        state.flush();
        state.flush();
        storage.crash();

        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(2, storage.flushes);
    }

    @Test
    public void cleanFlushPerformsNoStorageIo() {
        Engine engine = engine();
        CrashableStorage storage = new CrashableStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:clean_flush", 16, 17, 103L);

        state.flush();
        state.record(record);
        state.flush();
        state.flush();

        assertEquals(1, storage.flushes);
    }

    @Test
    public void discardedAuthorityRemainsAbsentAfterAutosaveAndAbruptStateLoss() {
        Engine engine = engine();
        CrashableStorage storage = new CrashableStorage();
        NativeStructureOwnershipRecord record = record(
                "test:discard_durable", -18, 19, 104L);
        storage.write(NativeStructureOwnershipStore.pack(-18, 19), record);
        storage.flush();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);

        state.discard(record.structureKey(), record.originChunkX(), record.originChunkZ());
        state.flush();
        storage.crash();

        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertNull(recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(2, storage.flushes);
    }

    @Test
    public void successfulCloseFlushSurvivesAbruptStateLoss() {
        Engine engine = engine();
        CrashableStorage storage = new CrashableStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:close_durable", 20, -21, 105L);

        state.record(record);
        state.close();
        storage.crash();

        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(1, storage.flushes);
    }

    @Test
    public void failedCloseLeavesTheStoreOpenAndDirtyForRetry() {
        Engine engine = engine();
        FailingFlushStorage storage = new FailingFlushStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:close_retry", -22, 23, 106L);

        state.record(record);
        assertThrows(IllegalStateException.class, state::close);
        assertEquals(record, state.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));

        state.close();
        storage.crash();
        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(2, storage.flushes);
    }

    @Test
    public void deferredCloseLeavesTheStoreOpenAndDirtyForRetry() {
        Engine engine = engine();
        DeferringFlushStorage storage = new DeferringFlushStorage();
        NativeStructureOwnershipStore.State state =
                new NativeStructureOwnershipStore.State(engine, storage);
        NativeStructureOwnershipRecord record = record(
                "test:close_deferred", -23, 24, 108L);

        state.record(record);
        assertThrows(IllegalStateException.class, state::close);
        assertEquals(record, state.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));

        state.close();
        storage.crash();
        NativeStructureOwnershipStore.State recovered =
                new NativeStructureOwnershipStore.State(engine, storage);
        assertEquals(record, recovered.findPersisted(
                record.structureKey(), record.originChunkX(), record.originChunkZ()));
        assertEquals(2, storage.flushes);
    }

    @Test
    public void queuedOwnershipFlushUsesMantleCapturedBeforeRuntimePromotion() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        IrisEngine.GenerationRuntimeBinding bindingA = runtimeBinding(11);
        IrisEngine.GenerationRuntimeBinding bindingB = runtimeBinding(12);
        MantleFixture mantleA = mantleFixture();
        MantleFixture mantleB = mantleFixture();
        AtomicReference<GenerationHistoryRuntimeRouter.RuntimeOwnership> ownership =
                new AtomicReference<>(new GenerationHistoryRuntimeRouter.RuntimeOwnership(1L, bindingA));
        AtomicReference<EngineMantle> currentMantle = new AtomicReference<>(mantleA.engineMantle());
        AtomicInteger currentRuntime = new AtomicInteger(11);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.currentRuntimeOwnership()).thenAnswer(ignored -> Optional.of(ownership.get()));
        when(engine.getMantle()).thenAnswer(ignored -> currentMantle.get());
        when(engine.getCacheID()).thenAnswer(ignored -> currentRuntime.get());
        NativeStructureOwnershipStore.MantleStorage storage =
                new NativeStructureOwnershipStore.MantleStorage(engine);
        NativeStructureOwnershipRecord record = record("test:captured_mantle", 4, -5, 109L);

        storage.write(NativeStructureOwnershipStore.pack(4, -5), record);
        ownership.set(new GenerationHistoryRuntimeRouter.RuntimeOwnership(2L, bindingB));
        currentMantle.set(mantleB.engineMantle());
        currentRuntime.set(12);

        assertTrue(storage.flush());
        verify(mantleA.mantle(), times(2)).getChunk(4, -5);
        verify(mantleA.mantle()).saveIdleTectonicPlates(any());
        verify(mantleB.mantle(), never()).getChunk(anyInt(), anyInt());
        verify(mantleB.mantle(), never()).saveIdleTectonicPlates(any());
        storage.close();
    }

    @Test
    public void flushPartitionsPendingOwnershipByCapturedRuntime() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        IrisEngine.GenerationRuntimeBinding bindingA = runtimeBinding(21);
        IrisEngine.GenerationRuntimeBinding bindingB = runtimeBinding(22);
        MantleFixture mantleA = mantleFixture();
        MantleFixture mantleB = mantleFixture();
        AtomicReference<GenerationHistoryRuntimeRouter.RuntimeOwnership> ownership =
                new AtomicReference<>(new GenerationHistoryRuntimeRouter.RuntimeOwnership(3L, bindingA));
        AtomicReference<EngineMantle> currentMantle = new AtomicReference<>(mantleA.engineMantle());
        AtomicInteger currentRuntime = new AtomicInteger(21);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.currentRuntimeOwnership()).thenAnswer(ignored -> Optional.of(ownership.get()));
        when(engine.getMantle()).thenAnswer(ignored -> currentMantle.get());
        when(engine.getCacheID()).thenAnswer(ignored -> currentRuntime.get());
        NativeStructureOwnershipStore.MantleStorage storage =
                new NativeStructureOwnershipStore.MantleStorage(engine);
        NativeStructureOwnershipRecord recordA = record("test:partition_a", -7, 8, 110L);
        NativeStructureOwnershipRecord recordB = record("test:partition_b", 9, -10, 111L);

        storage.write(NativeStructureOwnershipStore.pack(-7, 8), recordA);
        ownership.set(new GenerationHistoryRuntimeRouter.RuntimeOwnership(4L, bindingB));
        currentMantle.set(mantleB.engineMantle());
        currentRuntime.set(22);
        storage.write(NativeStructureOwnershipStore.pack(9, -10), recordB);

        assertTrue(storage.flush());
        verify(mantleA.mantle(), times(2)).getChunk(-7, 8);
        verify(mantleA.mantle()).saveIdleTectonicPlates(any());
        verify(mantleB.mantle(), times(2)).getChunk(9, -10);
        verify(mantleB.mantle()).saveIdleTectonicPlates(any());
        storage.close();
    }

    private static Engine engine() {
        Engine engine = mock(Engine.class);
        when(engine.isClosing()).thenReturn(false);
        when(engine.isClosed()).thenReturn(false);
        return engine;
    }

    private static IrisEngine.GenerationRuntimeBinding runtimeBinding(int runtimeId) {
        IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
        when(binding.runtimeId()).thenReturn(runtimeId);
        return binding;
    }

    @SuppressWarnings("unchecked")
    private static MantleFixture mantleFixture() {
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter section = mock(Matter.class);
        MatterSlice<NativeStructureOwnershipBundle> slice = mock(MatterSlice.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(chunk.getOrCreate(0)).thenReturn(section);
        when(section.<NativeStructureOwnershipBundle>slice(
                NativeStructureOwnershipBundle.class)).thenReturn(slice);
        when(slice.get(0, 0, 0)).thenReturn(null);
        when(mantle.saveIdleTectonicPlates(any())).thenReturn(Set.of());
        return new MantleFixture(engineMantle, mantle);
    }

    private static NativeStructureOwnershipRecord record(String key, int originX, int originZ,
                                                          long placementIdentity) {
        return record(key, originX, originZ, placementIdentity, new IrisStructureTerrain(), 8);
    }

    private static NativeStructureOwnershipRecord record(String key, int originX, int originZ,
                                                          long placementIdentity,
                                                          int referenceRadius) {
        return record(key, originX, originZ, placementIdentity, new IrisStructureTerrain(), referenceRadius);
    }

    private static NativeStructureOwnershipRecord record(String key, int originX, int originZ,
                                                          long placementIdentity,
                                                          IrisStructureTerrain terrain,
                                                          int referenceRadius) {
        return new NativeStructureOwnershipRecord(
                NativeStructureOwnershipRecord.CURRENT_SCHEMA,
                key,
                originX,
                originZ,
                placementIdentity,
                47,
                originX << 4,
                43,
                originZ << 4,
                (originX << 4) + 31,
                78,
                (originZ << 4) + 31,
                58,
                originX - referenceRadius,
                originX + referenceRadius,
                originZ - referenceRadius,
                originZ + referenceRadius,
                FINGERPRINT,
                NativeStructureOwnershipRecord.DecisionSnapshot.capture(
                        new IrisNativeStructureDecision(
                                NativeStructureGenerationStatus.GENERATE_NATIVE,
                                0,
                                null,
                                false,
                                null,
                                terrain
                        ))
        );
    }

    private static class TestStorage implements NativeStructureOwnershipStore.Storage {
        protected final Map<Long, NativeStructureOwnershipBundle> chunks = new ConcurrentHashMap<>();
        protected volatile int flushes;

        @Override
        public NativeStructureOwnershipBundle read(int chunkX, int chunkZ) {
            return chunks.get(NativeStructureOwnershipStore.pack(chunkX, chunkZ));
        }

        @Override
        public void write(long target, NativeStructureOwnershipRecord record) {
            chunks.compute(target, (ignored, bundle) ->
                    (bundle == null ? NativeStructureOwnershipBundle.empty() : bundle).with(record));
        }

        @Override
        public void remove(long target, String structureKey, int originChunkX, int originChunkZ) {
            chunks.computeIfPresent(target, (ignored, bundle) -> {
                NativeStructureOwnershipBundle updated = bundle.without(
                        structureKey, originChunkX, originChunkZ);
                return updated.records().isEmpty() ? null : updated;
            });
        }

        @Override
        public boolean flush() {
            flushes++;
            return true;
        }

        NativeStructureOwnershipRecord find(long target,
                                             NativeStructureOwnershipRecord record) {
            NativeStructureOwnershipBundle bundle = chunks.get(target);
            return bundle == null ? null : bundle.find(record.structureKey(),
                    record.originChunkX(), record.originChunkZ());
        }
    }

    private static class CrashableStorage extends TestStorage {
        protected final Map<Long, NativeStructureOwnershipBundle> durable = new ConcurrentHashMap<>();

        @Override
        public boolean flush() {
            super.flush();
            durable.clear();
            durable.putAll(chunks);
            return true;
        }

        void crash() {
            chunks.clear();
            chunks.putAll(durable);
        }
    }

    private static final class FailingFlushStorage extends CrashableStorage {
        private final AtomicBoolean fail = new AtomicBoolean(true);

        @Override
        public boolean flush() {
            flushes++;
            if (fail.compareAndSet(true, false)) {
                throw new IllegalStateException("Simulated ownership flush failure");
            }
            durable.clear();
            durable.putAll(chunks);
            return true;
        }
    }

    private static final class DeferringFlushStorage extends CrashableStorage {
        private final AtomicBoolean defer = new AtomicBoolean(true);

        @Override
        public boolean flush() {
            flushes++;
            if (defer.compareAndSet(true, false)) {
                return false;
            }
            durable.clear();
            durable.putAll(chunks);
            return true;
        }
    }

    private static final class BlockingStorage extends TestStorage {
        private final CountDownLatch writeEntered = new CountDownLatch(1);
        private final CountDownLatch allowWrite = new CountDownLatch(1);
        private final AtomicBoolean blocked = new AtomicBoolean();
        private volatile long blockedTarget;

        @Override
        public void write(long target, NativeStructureOwnershipRecord record) {
            if (target == blockedTarget && blocked.compareAndSet(false, true)) {
                writeEntered.countDown();
                try {
                    if (!allowWrite.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release origin write");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting to release origin write", error);
                }
            }
            super.write(target, record);
        }
    }

    private static final class PostWriteBlockingStorage extends TestStorage {
        private final CountDownLatch firstPersisted = new CountDownLatch(1);
        private final CountDownLatch allowFirstReturn = new CountDownLatch(1);
        private final AtomicBoolean blocked = new AtomicBoolean();

        @Override
        public void write(long target, NativeStructureOwnershipRecord record) {
            super.write(target, record);
            if (!blocked.compareAndSet(false, true)) {
                return;
            }
            firstPersisted.countDown();
            try {
                if (!allowFirstReturn.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release first ownership write");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ownership write was interrupted", error);
            }
        }
    }

    private record MantleFixture(EngineMantle engineMantle, Mantle<Matter> mantle) {
    }
}
