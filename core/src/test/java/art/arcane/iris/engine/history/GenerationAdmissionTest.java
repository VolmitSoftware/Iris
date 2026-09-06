package art.arcane.iris.engine.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationAdmissionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cutoverWaitsForEveryOpenGenerationStageAcrossInstances() throws Exception {
        GenerationAdmission first = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission second = new GenerationAdmission(temporaryFolder.getRoot().toPath().resolve(".").normalize());
        GenerationAdmission.StageLease stage = first.enterStage();
        CountDownLatch requested = new CountDownLatch(1);
        CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
            requested.countDown();
            try (GenerationAdmission.CutoverLease ignored = second.beginCutover()) {
            }
        });

        assertTrue(requested.await(5L, TimeUnit.SECONDS));
        Thread.sleep(100L);
        assertFalse(cutover.isDone());
        stage.close();
        cutover.get(5L, TimeUnit.SECONDS);
    }

    @Test
    public void aWaitingCutoverPreventsLaterStagesFromOvertakingIt() throws Exception {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.StageLease firstStage = admission.enterStage();
        CountDownLatch cutoverEntered = new CountDownLatch(1);
        CountDownLatch releaseCutover = new CountDownLatch(1);
        FutureTask<Void> cutover = new FutureTask<>(() -> {
            try (GenerationAdmission.CutoverLease ignored = admission.beginCutover()) {
                cutoverEntered.countDown();
                await(releaseCutover);
            }
        }, null);
        FutureTask<Void> laterStage = new FutureTask<>(() -> {
            try (GenerationAdmission.StageLease ignored = admission.enterStage()) {
            }
        }, null);
        Thread cutoverThread = Thread.ofPlatform().daemon().unstarted(cutover);
        Thread laterStageThread = Thread.ofPlatform().daemon().unstarted(laterStage);

        try {
            cutoverThread.start();
            awaitWaiting(cutoverThread);
            laterStageThread.start();
            awaitWaiting(laterStageThread);

            firstStage.close();
            assertTrue(cutoverEntered.await(5L, TimeUnit.SECONDS));
            assertFalse(laterStage.isDone());
            releaseCutover.countDown();
            cutover.get(5L, TimeUnit.SECONDS);
            laterStage.get(5L, TimeUnit.SECONDS);
        } finally {
            firstStage.close();
            releaseCutover.countDown();
            cutoverThread.join(5_000L);
            laterStageThread.join(5_000L);
        }
    }

    @Test
    public void cutoverDrainsContinuouslyArrivingStages() throws Exception {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.RuntimeLease runtime = admission.retainRuntime();
        ExecutorService executor = Executors.newFixedThreadPool(9, Thread.ofPlatform().daemon().factory());
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch ready = new CountDownLatch(8);
        List<Future<?>> stages = new ArrayList<>(8);
        try {
            for (int i = 0; i < 8; i++) {
                stages.add(executor.submit(() -> enterStagesUntilCutover(admission, running, ready)));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            Future<?> cutover = executor.submit(() -> {
                try (GenerationAdmission.CutoverLease ignored = admission.beginCutover()) {
                    running.set(false);
                }
            });
            cutover.get(5, TimeUnit.SECONDS);
            for (Future<?> stage : stages) {
                stage.get(5, TimeUnit.SECONDS);
            }
        } finally {
            running.set(false);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            runtime.close();
        }
        assertThrows(IllegalStateException.class, admission::enterStage);
    }

    @Test
    public void aStageMayCompleteOnAThreadOtherThanItsCaller() throws Exception {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.StageLease stage = admission.enterStage();
        CompletableFuture<Void> close = CompletableFuture.runAsync(stage::close);
        close.get(5L, TimeUnit.SECONDS);

        CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
            try (GenerationAdmission.CutoverLease ignored = admission.beginCutover()) {
            }
        });
        cutover.get(5L, TimeUnit.SECONDS);
    }

    @Test
    public void startupCutoverIsPermanentlyClosedByFirstStageAdmission() {
        GenerationAdmission admission = new GenerationAdmission(
                temporaryFolder.getRoot().toPath().resolve("startup-only")
        );
        try (GenerationAdmission.CutoverLease ignored = admission.beginStartupCutover()) {
        }
        try (GenerationAdmission.StageLease ignored = admission.enterStage()) {
        }

        assertThrows(IllegalStateException.class, admission::beginStartupCutover);
    }

    @Test
    public void lastRuntimeReleaseAllowsStartupOnAFreshAdmissionOnly() {
        Path root = temporaryFolder.getRoot().toPath();
        GenerationAdmission admission = new GenerationAdmission(root);
        GenerationAdmission stale = new GenerationAdmission(root);
        GenerationAdmission.RuntimeLease runtime = admission.retainRuntime();
        try (GenerationAdmission.StageLease ignored = admission.enterStage()) {
        }
        runtime.close();
        GenerationAdmission reopened = new GenerationAdmission(root);
        try (GenerationAdmission.CutoverLease ignored = reopened.beginStartupCutover()) {
        }
        assertThrows(IllegalStateException.class, stale::enterStage);
        assertThrows(IllegalStateException.class, stale::beginCutover);
        assertThrows(IllegalStateException.class, stale::retainRuntime);
        runtime.close();
        try (GenerationAdmission.StageLease ignored = reopened.enterStage()) {
        }
    }

    @Test
    public void everyRuntimeMustReleaseBeforeAdmissionCanRetire() {
        Path root = temporaryFolder.getRoot().toPath();
        GenerationAdmission first = new GenerationAdmission(root);
        GenerationAdmission second = new GenerationAdmission(root);
        GenerationAdmission.RuntimeLease firstRuntime = first.retainRuntime();
        GenerationAdmission.RuntimeLease secondRuntime = second.retainRuntime();
        try (GenerationAdmission.StageLease ignored = first.enterStage()) {
        }
        firstRuntime.close();
        assertThrows(IllegalStateException.class, new GenerationAdmission(root)::beginStartupCutover);
        try (GenerationAdmission.StageLease ignored = second.enterStage()) {
        }
        secondRuntime.close();
        try (GenerationAdmission.CutoverLease ignored = new GenerationAdmission(root).beginStartupCutover()) {
        }
    }

    @Test
    public void failedRetirementRetainsTheLeaseUntilActiveStagesFinish() {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.RuntimeLease runtime = admission.retainRuntime();
        GenerationAdmission.StageLease stage = admission.enterStage();
        assertThrows(IllegalStateException.class, runtime::close);
        assertThrows(IllegalStateException.class, admission::beginStartupCutover);
        stage.close();
        runtime.close();
        assertThrows(IllegalStateException.class, admission::enterStage);
    }

    @Test
    public void failedRetirementRetainsTheLeaseUntilActiveCutoversFinish() {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.RuntimeLease runtime = admission.retainRuntime();
        GenerationAdmission.CutoverLease cutover = admission.beginCutover();
        assertThrows(IllegalStateException.class, runtime::close);
        cutover.close();
        runtime.close();
        assertThrows(IllegalStateException.class, admission::beginCutover);
    }

    @Test
    public void waitingCutoversKeepAdmissionOwnedUntilTheirWorkCompletes() throws Exception {
        GenerationAdmission admission = new GenerationAdmission(temporaryFolder.getRoot().toPath());
        GenerationAdmission.RuntimeLease runtime = admission.retainRuntime();
        GenerationAdmission.StageLease stage = admission.enterStage();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FutureTask<Void> cutover = new FutureTask<>(() -> {
            try (GenerationAdmission.CutoverLease ignored = admission.beginCutover()) {
                entered.countDown();
                await(release);
            }
        }, null);
        Thread thread = Thread.ofPlatform().daemon().unstarted(cutover);
        try {
            thread.start();
            awaitWaiting(thread);
            assertThrows(IllegalStateException.class, runtime::close);
            stage.close();
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, runtime::close);
            release.countDown();
            cutover.get(5L, TimeUnit.SECONDS);
            runtime.close();
            assertThrows(IllegalStateException.class, admission::beginCutover);
        } finally {
            stage.close();
            release.countDown();
            thread.join(5_000L);
        }
    }

    private static void enterStagesUntilCutover(GenerationAdmission admission, AtomicBoolean running, CountDownLatch ready) {
        boolean started = false;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try (GenerationAdmission.StageLease ignored = admission.enterStage()) {
                if (!started) {
                    started = true;
                    ready.countDown();
                }
            }
        }
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            assertTrue("Generation operation completed before reaching the admission gate.", thread.isAlive());
            Thread.sleep(1L);
        }
        assertEquals("Generation operation did not reach the admission gate.", Thread.State.WAITING, thread.getState());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch.", exception);
        }
    }
}
