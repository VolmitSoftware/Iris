package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GenerationSemanticPointReadTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsRetainDurableShardAfterEvictionDuringUnforcedAppend() throws Exception {
        GenerationSemanticIndex index = index();
        for (int region = 0; region <= 70; region++) {
            index.claimAndPersist(claim(region * 32, 1));
        }
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try (AppendGate gate = append(() -> index.claimAndPersist(claim(1, 1)), Failure.NONE)) {
            Future<?> inspected = reader.submit(() -> {
                assertSameRecord(claim(0, 1), index.get(0, 0));
                assertTrue(index.get(1, 0).isEmpty());
                assertTrue(index.hasSealedClaim(0, 0, 1));
                assertFalse(index.hasSealedClaim(1, 0, 1));
                for (int region = 1; region <= 70; region++) {
                    assertSameRecord(claim(region * 32, 1), index.get(region * 32, 0));
                }
                assertSameRecord(claim(0, 1), index.get(0, 0));
                assertTrue(index.get(1, 0).isEmpty());
                assertTrue(index.get(-1, -1).isEmpty());
            });
            inspected.get(2, TimeUnit.SECONDS);
            gate.finish();
            assertNull(gate.failure.get());
            assertSameRecord(claim(1, 1), index.get(1, 0));
            assertTrue(index.cachedRegionCount() <= 64);
            assertEquals(72, index.recordCount());
        } finally {
            drain(reader);
        }
    }

    @Test
    public void newRegionRemainsAbsentUntilItsFirstForceCompletes() throws Exception {
        GenerationSemanticIndex index = index();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try (AppendGate gate = append(() -> index.claimAndPersist(claim(-32, 1)), Failure.NONE)) {
            assertTrue(reader.submit(() -> index.get(-32, 0).isEmpty()).get(2, TimeUnit.SECONDS));
            gate.finish();
            assertNull(gate.failure.get());
            assertSameRecord(claim(-32, 1), index.get(-32, 0));
        } finally {
            drain(reader);
        }
    }

    @Test
    public void confirmedRollbackKeepsPublishedReadsAndPermitsRetry() throws Exception {
        GenerationSemanticIndex index = index();
        index.claimAndPersist(claim(0, 1));
        try (AppendGate gate = append(() -> index.claimAndPersist(claim(1, 1)), Failure.ROLLBACK_CONFIRMED)) {
            gate.finish();
            assertSame(gate.originalFailure, gate.failure.get());
            assertSameRecord(claim(0, 1), index.get(0, 0));
            assertTrue(index.get(1, 0).isEmpty());
            assertTrue(index.claimAndPersist(claim(1, 1)));
            assertEquals(index.recordsSnapshot(),
                    GenerationSemanticIndex.loadRequired(index.storageDirectory().getParent().getParent().getParent()).recordsSnapshot());
        }
    }

    @Test
    public void uncertainRollbackAndClosePoisonHitsAndMisses() throws Exception {
        for (Failure mode : new Failure[]{Failure.ROLLBACK_UNCERTAIN, Failure.CLOSE_UNCERTAIN}) {
            GenerationSemanticIndex index = index();
            index.claimAndPersist(claim(0, 1));
            try (AppendGate gate = append(() -> index.claimAndPersist(claim(1, 1)), mode)) {
                gate.finish();
                assertTrue(gate.failure.get() instanceof IOException);
                assertSame(gate.originalFailure, gate.failure.get().getCause());
                assertThrows(IllegalStateException.class, () -> index.get(0, 0));
                assertThrows(IllegalStateException.class, () -> index.get(1, 0));
                assertThrows(IllegalStateException.class, () -> index.get(999, 999));
                assertThrows(IllegalStateException.class, () -> index.hasSealedClaim(0, 0, 1));
                assertThrows(IOException.class, () -> index.claimAndPersist(claim(2, 1)));
            }
        }
    }

    @Test
    public void maintenanceWaitsForAppendAndCallbacksRemainReentrant() throws Exception {
        GenerationSemanticIndex index = index();
        index.claimAndPersist(claim(0, 1));
        ExecutorService maintenance = Executors.newFixedThreadPool(2);
        Future<?> compact;
        Future<Integer> prune;
        try (AppendGate gate = append(() -> index.claimAndPersist(claim(1, 1)), Failure.NONE)) {
            CountDownLatch started = new CountDownLatch(2);
            compact = maintenance.submit(() -> {
                started.countDown();
                index.compactJournals();
                return null;
            });
            prune = maintenance.submit(() -> {
                started.countDown();
                return index.discardUnstoredClaims(WorldChunkInventory.ofPackedChunks(
                        ChunkGenerationOwnership.packChunk(0, 0)), Set.of(1L));
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> compact.get(100, TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class, () -> prune.get(100, TimeUnit.MILLISECONDS));
            gate.finish();
            assertNull(gate.failure.get());
            compact.get(5, TimeUnit.SECONDS);
            assertEquals(1, prune.get(5, TimeUnit.SECONDS).intValue());
            AtomicBoolean recorded = new AtomicBoolean();
            maintenance.submit(() -> {
                index.forEachRecord(record -> {
                    assertEquals(record, index.get(record.chunkX(), record.chunkZ()).orElseThrow());
                    if (recorded.compareAndSet(false, true)) {
                        index.claimAndPersist(claim(2, 1));
                    }
                });
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertEquals(2, index.recordCount());
            assertTrue(index.get(1, 0).isEmpty());
            assertSameRecord(claim(2, 1), index.get(2, 0));
        } finally {
            drain(maintenance);
        }
    }

    @Test
    public void historyPointReadsProceedWhileClaimStillFencesPromotion() throws Exception {
        GenerationHistory history = history();
        Path replacement = pack("replacement", "beta");
        GenerationActivation pending = history.stageUpdate(replacement, fingerprint(replacement),
                history.activeEpoch().dimensionContract(), GenerationRegistryContract.empty(), 256);
        ExecutorService tasks = Executors.newFixedThreadPool(2);
        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.GenerationStage stage = history.openStage(1, 0);
             AppendGate gate = append(() -> history.claimGeneratedSemantics(stage, claim(1, 1)), Failure.NONE)) {
            tasks.submit(() -> {
                assertTrue(history.semantics(1, 0).isEmpty());
                assertEquals(stage.activation(), history.resolveActivation(1, 0));
                assertEquals(stage.activation(), history.resolveActivation(-32, -32));
            }).get(2, TimeUnit.SECONDS);
            stage.close();
            CountDownLatch cutoverEntered = new CountDownLatch(1);
            Future<GenerationActivation> promoted = tasks.submit(() -> {
                try (GenerationHistory.LiveCutover cutover = history.beginLiveCutover()) {
                    cutoverEntered.countDown();
                    return cutover.promote(boundary -> (x, z) -> null);
                }
            });
            assertTrue(cutoverEntered.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> promoted.get(100, TimeUnit.MILLISECONDS));
            assertTrue(tasks.submit(() -> history.semantics(1, 0).isEmpty()).get(2, TimeUnit.SECONDS));
            gate.finish();
            assertNull(gate.failure.get());
            assertEquals(pending.activationId(), promoted.get(5, TimeUnit.SECONDS).activationId());
            assertEquals(pending.activationId(), history.resolveActivation(1, 0).activationId());
            assertEquals(pending.activationId(), history.resolveActivation(-32, -32).activationId());
            assertTrue(history.semantics(1, 0).isEmpty());
        } finally {
            drain(tasks);
        }
    }

    private GenerationSemanticIndex index() throws IOException {
        return GenerationSemanticIndex.initialize(temporaryFolder.newFolder().toPath());
    }

    private GenerationHistory history() throws Exception {
        Path world = temporaryFolder.newFolder().toPath();
        Path pack = pack("initial", "alpha");
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld_type", "NORMAL", "OVERWORLD", 127, -64, 384, 384, 1D,
                false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
        return GenerationHistory.create(world, pack, fingerprint(pack), 42L, contract, GenerationRegistryContract.empty());
    }

    private Path pack(String name, String content) throws IOException {
        Path pack = temporaryFolder.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), content);
        return pack;
    }

    private static String fingerprint(Path pack) throws IOException {
        return GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
    }

    private static ChunkGenerationSemantics claim(int chunkX, long activation) {
        return ChunkGenerationSemantics.builder(chunkX, 0, activation)
                .addSurfaceBiome("iris:forest").seal().build();
    }

    private static void assertSameRecord(ChunkGenerationSemantics expected, Optional<ChunkGenerationSemantics> actual) {
        assertEquals(expected, actual.orElseThrow());
    }

    private static AppendGate append(Callable<?> operation, Failure mode) throws InterruptedException {
        AppendGate gate = new AppendGate(operation, mode);
        gate.worker.start();
        assertTrue(gate.forcing.await(5, TimeUnit.SECONDS));
        return gate;
    }

    private static void drain(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    private enum Failure {
        NONE,
        ROLLBACK_CONFIRMED,
        ROLLBACK_UNCERTAIN,
        CLOSE_UNCERTAIN
    }

    private static final class AppendGate implements AutoCloseable {
        private final CountDownLatch forcing = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final IOException originalFailure = new IOException("Semantic journal failed");
        private final Thread worker;

        private AppendGate(Callable<?> operation, Failure mode) {
            worker = new Thread(() -> {
                try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
                    FileChannel source = (FileChannel) invocation.callRealMethod();
                    Path path = invocation.getArgument(0);
                    if (invocation.getMethod().getParameterCount() != 2
                            || !path.getFileName().toString().endsWith(".iswal")) {
                        return source;
                    }
                    FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
                    AtomicInteger forces = new AtomicInteger();
                    doAnswer(call -> {
                        int count = forces.incrementAndGet();
                        if (count == 1) {
                            forcing.countDown();
                            if (!release.await(10, TimeUnit.SECONDS)) {
                                throw new IOException("Timed out waiting to resume semantic force");
                            }
                            if (mode == Failure.ROLLBACK_CONFIRMED || mode == Failure.ROLLBACK_UNCERTAIN) {
                                throw originalFailure;
                            }
                        }
                        if (count == 2 && mode == Failure.ROLLBACK_UNCERTAIN) {
                            throw new IOException("Semantic rollback failed");
                        }
                        source.force(true);
                        return null;
                    }).when(intercepted).force(true);
                    if (mode == Failure.CLOSE_UNCERTAIN) {
                        doAnswer(call -> {
                            source.close();
                            throw originalFailure;
                        }).when(intercepted).close();
                    }
                    return intercepted;
                })) {
                    operation.call();
                } catch (Throwable caught) {
                    failure.set(caught);
                }
            }, "semantic-point-read-test");
        }

        private void finish() throws InterruptedException {
            release.countDown();
            worker.join(5_000L);
            assertFalse("Semantic append did not finish", worker.isAlive());
        }

        @Override
        public void close() throws InterruptedException {
            finish();
        }
    }
}
