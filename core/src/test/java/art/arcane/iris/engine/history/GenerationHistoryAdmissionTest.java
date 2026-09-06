package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
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
            Future<?> observed = executor.submit(() -> history.semantics(1, 2));
            assertThrows(TimeoutException.class, () -> observed.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            observed.get(5, TimeUnit.SECONDS);
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

    private static boolean persistPaused(GenerationHistory history, GenerationHistory.GenerationStage stage,
                                         ChunkGenerationSemantics claim, CountDownLatch entered, CountDownLatch release) throws Exception {
        try (MockedStatic<FileChannel> ignored = mockStatic(FileChannel.class, invocation -> {
            FileChannel channel = (FileChannel) invocation.callRealMethod();
            Path path = invocation.getArgument(0);
            if (!path.getFileName().toString().endsWith(".iswal")) {
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
