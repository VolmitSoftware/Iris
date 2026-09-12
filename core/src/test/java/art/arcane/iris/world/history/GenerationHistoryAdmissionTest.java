package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.Await;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GenerationHistoryAdmissionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stageMetadataProceedsWhileUnrelatedSemanticPersistenceWaits() throws Exception {
        GenerationHistory history = history();
        GenerationActivation active = history.activeActivation();
        GenerationEpoch epoch = history.activeEpoch();
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        GenerationHistory.GenerationStage writing = history.openStage(1, 2);
        ChunkGenerationSemantics claim = claim(writing);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Boolean> persisted = executor.submit(() -> persistPaused(history, writing, claim, entered, release));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> admitted = executor.submit(() -> {
                try (GenerationHistory.GenerationStage stage = history.openStage(32, 64)) {
                    assertEquals(active, stage.activation());
                    assertEquals(epoch, stage.epoch());
                    assertEquals(history.paths().packRoot(epoch.epochId()), stage.packRoot());
                }
                return null;
            });
            admitted.get(1, TimeUnit.SECONDS);
            assertFalse(persisted.isDone());
            Future<Optional<ChunkGenerationSemantics>> observed = executor.submit(() -> history.semantics(1, 2));
            assertTrue(observed.get(1, TimeUnit.SECONDS).isEmpty());
            release.countDown();
            assertTrue(persisted.get(5, TimeUnit.SECONDS));
            assertEquals(claim, history.semantics(1, 2).orElseThrow());
            assertEquals(claim, GenerationSemanticIndex.loadRequired(history.paths().dimensionRoot()).get(1, 2).orElseThrow());
        } finally {
            release.countDown();
            drain(executor);
            writing.close();
            runtime.close();
        }
        assertThrows(IllegalStateException.class, () -> history.openStage(1, 2));
        assertThrows(IllegalStateException.class, () -> history.claimGeneratedSemantics(writing, claim));
    }

    @Test
    public void closingAStageCannotLetPromotionOvertakeItsDurableClaim() throws Exception {
        GenerationHistory history = history();
        Path replacement = pack("replacement", "beta");
        GenerationActivation pending = history.stageUpdate(replacement, fingerprint(replacement),
                history.activeEpoch().dimensionContract(), GenerationRegistryContract.empty(), 32);
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        GenerationHistory.GenerationStage writing = history.openStage(1, 2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cutoverEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Boolean> persisted = executor.submit(() -> persistPaused(history, writing, claim(writing), entered, release));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<GenerationActivation> promoted = executor.submit(() -> {
                try (GenerationHistory.LiveCutover cutover = history.beginLiveCutover()) {
                    cutoverEntered.countDown();
                    return cutover.promote(boundary -> (x, z) -> null);
                }
            });
            assertFalse(cutoverEntered.await(100, TimeUnit.MILLISECONDS));
            writing.close();
            assertTrue(cutoverEntered.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> promoted.get(100, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, runtime::close);
            Future<?> admitted = executor.submit(() -> {
                try (GenerationHistory.GenerationStage stage = history.openStage(32, 64)) {
                    assertEquals(pending.activationId(), stage.activation().activationId());
                    assertEquals(pending.epochId(), stage.epoch().epochId());
                    assertEquals(history.paths().packRoot(pending.epochId()), stage.packRoot());
                }
                return null;
            });
            assertThrows(TimeoutException.class, () -> admitted.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertTrue(persisted.get(5, TimeUnit.SECONDS));
            assertEquals(pending.activationId(), promoted.get(5, TimeUnit.SECONDS).activationId());
            admitted.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            drain(executor);
            writing.close();
            runtime.close();
        }
    }

    @Test
    public void failedMetadataReadReleasesItsStageAdmission() throws Exception {
        GenerationHistory history = history();
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        Field storeField = GenerationHistory.class.getDeclaredField("store");
        storeField.setAccessible(true);
        GenerationHistoryStore store = (GenerationHistoryStore) storeField.get(history);
        Field failureField = GenerationHistoryStore.class.getDeclaredField("failure");
        failureField.setAccessible(true);
        IOException failure = new IOException("Manifest publication failed");
        failureField.set(store, failure);
        try {
            IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> history.openStage(1, 2));
            assertSame(failure, rejected.getCause());
        } finally {
            runtime.close();
        }
        assertThrows(IllegalStateException.class, history::retainRuntime);
    }

    @Test
    public void failedJournalWriteDoesNotPublishSemanticsOrPoisonStageMetadata() throws Exception {
        GenerationHistory history = history();
        IOException failure = new IOException("Journal write failed");
        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.GenerationStage stage = history.openStage(1, 2)) {
            ChunkGenerationSemantics claim = claim(stage);
            try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
                Path path = invocation.getArgument(0);
                if (path.getFileName().toString().endsWith(".iswal")) {
                    throw failure;
                }
                return invocation.callRealMethod();
            })) {
                assertSame(failure, assertThrows(IOException.class, () -> history.claimGeneratedSemantics(stage, claim)));
            }
            assertTrue(history.semantics(1, 2).isEmpty());
            assertTrue(GenerationSemanticIndex.loadRequired(history.paths().dimensionRoot()).get(1, 2).isEmpty());
            try (GenerationHistory.GenerationStage next = history.openStage(32, 64)) {
                assertEquals(stage.activation(), next.activation());
                assertEquals(stage.epoch(), next.epoch());
            }
            assertTrue(history.claimGeneratedSemantics(stage, claim));
        }
    }

    @Test
    public void queuedClaimsShareOneForceAndRetainIndividualValidation() throws Exception {
        GenerationHistory history = history();
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        List<GenerationHistory.GenerationStage> stages = new ArrayList<>();
        for (int x : new int[]{1, 2, 3, 1, 1, 4, 32, 33}) {
            stages.add(history.openStage(x, 0));
        }
        history.claimGeneratedSemantics(stages.getFirst(), claim(stages.getFirst()));
        List<Thread> workers = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(stages.size(), task -> {
            Thread thread = new Thread(task);
            workers.add(thread);
            return thread;
        });
        AtomicInteger forces = new AtomicInteger();
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            synchronized (history) {
                for (int index = 0; index < stages.size(); index++) {
                    GenerationHistory.GenerationStage stage = stages.get(index);
                    ChunkGenerationSemantics update = index == 4
                            ? ChunkGenerationSemantics.builder(1, 0, stage.activation().activationId())
                                    .addSurfaceBiome("iris:conflict").seal().build()
                            : claim(stage);
                    results.add(executor.submit(() -> persistCountingForces(history, stage, update, forces)));
                    awaitBlockedOnHistory(history, workers, index + 1);
                }
                stages.get(5).close();
            }
            assertFalse(results.get(0).get(5, TimeUnit.SECONDS));
            assertTrue(results.get(1).get(5, TimeUnit.SECONDS));
            assertTrue(results.get(2).get(5, TimeUnit.SECONDS));
            assertFalse(results.get(3).get(5, TimeUnit.SECONDS));
            assertTrue(assertThrows(ExecutionException.class, () -> results.get(4).get(5, TimeUnit.SECONDS))
                    .getCause() instanceof IllegalStateException);
            assertTrue(assertThrows(ExecutionException.class, () -> results.get(5).get(5, TimeUnit.SECONDS))
                    .getCause() instanceof IllegalStateException);
            assertTrue(results.get(6).get(5, TimeUnit.SECONDS));
            assertTrue(results.get(7).get(5, TimeUnit.SECONDS));
            assertEquals(2, forces.get());
            GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(history.paths().dimensionRoot());
            assertEquals(5, loaded.recordCount());
            assertEquals(claim(stages.getFirst()), loaded.get(1, 0).orElseThrow());
            assertTrue(loaded.get(4, 0).isEmpty());
        } finally {
            drain(executor);
            for (GenerationHistory.GenerationStage stage : stages) {
                stage.close();
            }
            runtime.close();
        }
    }

    @Test
    public void fullClaimQueueRetainsEveryAdmittedRequest() throws Exception {
        GenerationHistory history = history();
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        List<GenerationHistory.GenerationStage> stages = new ArrayList<>();
        List<Future<Boolean>> results = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch started = new CountDownLatch(132);
        try {
            synchronized (history) {
                for (int x = 0; x < 132; x++) {
                    GenerationHistory.GenerationStage stage = history.openStage(x, 0);
                    stages.add(stage);
                    results.add(executor.submit(() -> {
                        started.countDown();
                        return history.claimGeneratedSemantics(stage, claim(stage));
                    }));
                }
                assertTrue(started.await(5, TimeUnit.SECONDS));
                Field queueField = GenerationHistory.class.getDeclaredField("pendingSemanticClaims");
                queueField.setAccessible(true);
                ArrayBlockingQueue<?> queue = (ArrayBlockingQueue<?>) queueField.get(history);
                Await.reached("the claim queue to reach capacity", Duration.ofSeconds(5L),
                        () -> queue.remainingCapacity() == 0);
                assertEquals(0, queue.remainingCapacity());
                assertEquals(128, queue.size());
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get(10, TimeUnit.SECONDS));
            }
            assertEquals(132, GenerationSemanticIndex.loadRequired(history.paths().dimensionRoot()).recordCount());
        } finally {
            drain(executor);
            for (GenerationHistory.GenerationStage stage : stages) {
                stage.close();
            }
            runtime.close();
        }
    }

    @Test
    public void unexpectedLaterBatchFailureReachesWaitersAndKeepsEarlierSuccess() throws Exception {
        GenerationHistory history = history();
        GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
        List<GenerationHistory.GenerationStage> stages = new ArrayList<>();
        for (int x : new int[]{0, 32, 32, 64}) {
            stages.add(history.openStage(x, 0));
        }
        List<Thread> workers = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(stages.size(), task -> {
            Thread thread = new Thread(task);
            workers.add(thread);
            return thread;
        });
        IllegalStateException failure = new IllegalStateException("Unexpected second journal failure");
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            synchronized (history) {
                for (int index = 0; index < stages.size(); index++) {
                    GenerationHistory.GenerationStage stage = stages.get(index);
                    results.add(executor.submit(() -> persistFailingRegion(history, stage, failure)));
                    awaitBlockedOnHistory(history, workers, index + 1);
                }
            }
            assertTrue(results.getFirst().get(5, TimeUnit.SECONDS));
            for (int index = 1; index < results.size(); index++) {
                Future<Boolean> result = results.get(index);
                assertSame(failure, assertThrows(ExecutionException.class,
                        () -> result.get(5, TimeUnit.SECONDS)).getCause());
            }
            assertThrows(IllegalStateException.class, () -> history.semantics(0, 0));
            GenerationSemanticIndex loaded = GenerationSemanticIndex.loadRequired(history.paths().dimensionRoot());
            assertEquals(1, loaded.recordCount());
            assertEquals(claim(stages.getFirst()), loaded.get(0, 0).orElseThrow());
        } finally {
            drain(executor);
            for (GenerationHistory.GenerationStage stage : stages) {
                stage.close();
            }
            runtime.close();
        }
    }

    private static boolean persistFailingRegion(GenerationHistory history, GenerationHistory.GenerationStage stage,
                                               IllegalStateException failure) throws Exception {
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2
                    || !path.getFileName().toString().equals("r.1.0.iswal")) {
                return source;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            doAnswer(write -> {
                throw failure;
            }).when(intercepted).write(any(ByteBuffer.class));
            return intercepted;
        })) {
            return history.claimGeneratedSemantics(stage, claim(stage));
        }
    }

    private static boolean persistCountingForces(GenerationHistory history, GenerationHistory.GenerationStage stage,
                                                ChunkGenerationSemantics claim, AtomicInteger forces) throws Exception {
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2
                    || !path.getFileName().toString().endsWith(".iswal")) {
                return source;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            doAnswer(force -> {
                forces.incrementAndGet();
                source.force(true);
                return null;
            }).when(intercepted).force(true);
            return intercepted;
        })) {
            return history.claimGeneratedSemantics(stage, claim);
        }
    }

    private static void awaitBlockedOnHistory(GenerationHistory history, List<Thread> workers, int count) {
        Await.until("semantic claim callers to queue behind the History monitor", Duration.ofSeconds(5L),
                () -> blockedOnHistory(history, workers) == count);
    }

    private static int blockedOnHistory(GenerationHistory history, List<Thread> workers) {
        int blocked = 0;
        for (Thread worker : workers) {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(worker.threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(history)) {
                blocked++;
            }
        }
        return blocked;
    }

    private static boolean persistPaused(GenerationHistory history, GenerationHistory.GenerationStage stage,
                                         ChunkGenerationSemantics claim, CountDownLatch entered, CountDownLatch release) throws Exception {
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            FileChannel channel = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (invocation.getMethod().getParameterCount() != 2
                    || !path.getFileName().toString().endsWith(".iswal")) {
                return channel;
            }
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(channel));
            doAnswer(force -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to resume journal persistence");
                }
                channel.force(true);
                return null;
            }).when(intercepted).force(true);
            return intercepted;
        })) {
            return history.claimGeneratedSemantics(stage, claim);
        }
    }

    private GenerationHistory history() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        Path pack = pack("initial", "alpha");
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld_type", "NORMAL", "OVERWORLD", 127, -64, 384, 384, 1D,
                false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
        return GenerationHistory.create(world, pack, fingerprint(pack), 42L, contract, GenerationRegistryContract.empty());
    }

    private Path pack(String name, String content) throws Exception {
        Path pack = temporaryFolder.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), content);
        return pack;
    }

    private static String fingerprint(Path pack) throws IOException {
        return GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
    }

    private static ChunkGenerationSemantics claim(GenerationHistory.GenerationStage stage) {
        return ChunkGenerationSemantics.builder(stage.chunkX(), stage.chunkZ(), stage.activation().activationId())
                .addSurfaceBiome("iris:forest").seal().build();
    }

    private static void drain(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
}
