package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.testsupport.Await;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationStartupPreparationTest extends GenerationHistoryRuntimeRouterSupport {
    @Test
    public void preparationAttachesThePreparedActivationOnlyOnce() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-startup-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-startup-pack", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.StartupPreparation preparation = history.prepareStartup(64);
             GenerationHistoryRuntimeRouter router = attach(engine, preparation, runtimes)) {
            assertSame(history, router.history());
            assertSame(engine, router.engine());
            assertEquals(1L, history.activeActivation().activationId());
            assertEquals(0, runtimes.loadCount(1L));
            verify(engine).attachGenerationHistoryRuntimeRouter(router);
            assertThrows(IllegalStateException.class, () -> attach(engine, preparation, runtimes));
        }
    }

    @Test
    public void preparationPromotesPendingStateBeforeEngineAttachment() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-pending-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-pending-a", "alpha"));
        stage(history, createPack("prepared-pending-b", "beta"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.StartupPreparation preparation = history.prepareStartup(64)) {
            assertEquals(2L, history.activeActivation().activationId());
            assertFalse(history.pendingActivation().isPresent());
            IrisEngine engine = preparedEngine(history, runtimes);
            try (GenerationHistoryRuntimeRouter router = attach(engine, preparation, runtimes)) {
                assertEquals(2L, router.history().activeActivation().activationId());
                assertEquals(0, runtimes.loadCount(2L));
            }
        }
    }

    @Test
    public void changedManifestRejectsPreparationBeforeAttachingAnEngine() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-stale-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-stale-a", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.StartupPreparation preparation = history.prepareStartup(64)) {
            stage(history, createPack("prepared-stale-b", "beta"));
            assertThrows(IllegalStateException.class, () -> attach(engine, preparation, runtimes));
            verify(engine, never()).attachGenerationHistoryRuntimeRouter(any());
            assertEquals(1L, history.activeActivation().activationId());
            assertEquals(2L, history.pendingActivation().orElseThrow().activationId());
        }
    }

    @Test
    public void closedPreparationCannotAttach() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-closed-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-closed-pack", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime()) {
            GenerationHistory.StartupPreparation preparation = history.prepareStartup(64);
            preparation.close();
            assertThrows(IllegalStateException.class, () -> attach(engine, preparation, runtimes));
            verify(engine, never()).attachGenerationHistoryRuntimeRouter(any());
        }
    }

    @Test
    public void anotherThreadCannotConsumePreparation() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-thread-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-thread-pack", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime();
             GenerationHistory.StartupPreparation preparation = history.prepareStartup(64)) {
            FutureTask<Void> attempt = new FutureTask<>(() -> {
                assertThrows(IllegalStateException.class, () -> attach(engine, preparation, runtimes));
                return null;
            });
            Thread thread = Thread.ofPlatform().daemon().start(attempt);
            try {
                attempt.get(5L, TimeUnit.SECONDS);
                verify(engine, never()).attachGenerationHistoryRuntimeRouter(any());
                try (GenerationHistoryRuntimeRouter router = attach(engine, preparation, runtimes)) {
                    assertSame(history, router.history());
                }
            } finally {
                thread.join(5_000L);
            }
        }
    }

    @Test
    public void failedAttachmentConsumesPreparationAndFreshPreparationCanRetry() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-retry-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-retry-pack", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine failedEngine = preparedEngine(history, runtimes);
        IllegalStateException failure = new IllegalStateException("Engine attachment failed");
        doThrow(failure).when(failedEngine).attachGenerationHistoryRuntimeRouter(any());

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime()) {
            try (GenerationHistory.StartupPreparation preparation = history.prepareStartup(64)) {
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> attach(failedEngine, preparation, runtimes)));
                IrisEngine replacement = preparedEngine(history, runtimes);
                assertThrows(IllegalStateException.class, () -> attach(replacement, preparation, runtimes));
                verify(replacement, never()).attachGenerationHistoryRuntimeRouter(any());
            }
            IrisEngine replacement = preparedEngine(history, runtimes);
            try (GenerationHistory.StartupPreparation retry = history.prepareStartup(64);
                 GenerationHistoryRuntimeRouter router = attach(replacement, retry, runtimes)) {
                assertSame(history, router.history());
                verify(replacement).attachGenerationHistoryRuntimeRouter(router);
            }
        }
    }

    @Test(timeout = 10_000L)
    public void failedPreparationReleasesAdmissionForARetry() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-invalid-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-invalid-pack", "alpha"));
        Path dimension = history.activePackRoot().resolve("dimensions/main.json");
        byte[] original = Files.readAllBytes(dimension);
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime()) {
            Files.writeString(dimension, "changed");
            assertThrows(IOException.class, () -> history.prepareStartup(64));
            Files.write(dimension, original);
            try (GenerationHistory.StartupPreparation preparation = history.prepareStartup(64);
                 GenerationHistoryRuntimeRouter router = attach(engine, preparation, runtimes)) {
                assertSame(history, router.history());
            }
        }
    }

    @Test
    public void generationAdmissionWaitsUntilPreparedAttachmentScopeCloses() throws Exception {
        Path world = temporaryFolder.newFolder("prepared-admission-world").toPath();
        GenerationHistory history = createHistory(world, createPack("prepared-admission-pack", "alpha"));
        FakeRuntimeFactory runtimes = new FakeRuntimeFactory();
        IrisEngine engine = preparedEngine(history, runtimes);
        GenerationAdmission admission = new GenerationAdmission(world);
        FutureTask<Void> stage = new FutureTask<>(() -> {
            try (GenerationAdmission.StageLease ignored = admission.enterStage()) {
            }
            return null;
        });
        Thread thread = Thread.ofPlatform().daemon().unstarted(stage);

        try (GenerationAdmission.RuntimeLease runtime = history.retainRuntime()) {
            try {
                try (GenerationHistory.StartupPreparation preparation = history.prepareStartup(64);
                     GenerationHistoryRuntimeRouter router = attach(engine, preparation, runtimes)) {
                    thread.start();
                    Await.reached("generation to wait for startup preparation", Duration.ofSeconds(5L),
                            () -> thread.getState() == Thread.State.WAITING || stage.isDone());
                    assertFalse(stage.isDone());
                    assertSame(history, router.history());
                }
                stage.get(5L, TimeUnit.SECONDS);
                assertThrows(IllegalStateException.class, () -> history.prepareStartup(64));
            } finally {
                thread.join(5_000L);
            }
        }
    }

    private static IrisEngine preparedEngine(GenerationHistory history, FakeRuntimeFactory runtimes) throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        IrisEngine.GenerationRuntimeBinding binding = runtimes.binding(history, history.activeActivation());
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(binding);
        return engine;
    }

    private static GenerationHistoryRuntimeRouter attach(
            IrisEngine engine,
            GenerationHistory.StartupPreparation preparation,
            FakeRuntimeFactory runtimes
    ) throws Exception {
        return GenerationHistoryRuntimeRouter.attachPrepared(engine, preparation,
                (ignored, blockX, blockZ) -> signature(blockX, blockZ), runtimes);
    }
}
