package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class EngineBackgroundTasksAdmissionTest {
    @Test
    public void preliminaryDrainWaitsForSaveAdmittedAfterItsInitialSnapshot() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch saveRelease = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2);
             ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = scheduler(workers, new AtomicReference<>())) {
            try {
                fixture.tasks().scheduleTrackedTask(() -> waitForRelease(firstEntered, firstRelease));
                assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
                fixture.tasks().closeBackgroundTaskAdmission();
                Future<EngineBackgroundTasks.BackgroundTaskDrain> drain = startDrain(closer, fixture.tasks());
                assertTrue(EngineLifecycleTasks.run(fixture.engine(), "world_save", () ->
                        fixture.tasks().scheduleAdmittedTask(fixture.engine(),
                                () -> waitForRelease(saveEntered, saveRelease))));
                assertTrue(saveEntered.await(5, TimeUnit.SECONDS));
                firstRelease.countDown();
                assertThrows(TimeoutException.class, () -> drain.get(100, TimeUnit.MILLISECONDS));
                saveRelease.countDown();
                drain.get(5, TimeUnit.SECONDS).requireComplete("Studio cutover");
            } finally {
                firstRelease.countDown();
                saveRelease.countDown();
            }
        }
    }

    @Test
    public void newlyAdmittedFailureSurvivesCompletionAndLaterTaskSubmission() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("ownership write failed");
        AtomicReference<Throwable> logged = new AtomicReference<>();
        try (ExecutorService workers = Executors.newFixedThreadPool(2);
             ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = scheduler(workers, logged)) {
            try {
                fixture.tasks().scheduleTrackedTask(() -> waitForRelease(firstEntered, firstRelease));
                assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
                fixture.tasks().closeBackgroundTaskAdmission();
                Future<EngineBackgroundTasks.BackgroundTaskDrain> drain = startDrain(closer, fixture.tasks());
                assertTrue(EngineLifecycleTasks.run(fixture.engine(), "world_save", () ->
                        fixture.tasks().scheduleAdmittedTask(fixture.engine(), () -> {
                            failed.countDown();
                            throw failure;
                        })));
                assertTrue(failed.await(5, TimeUnit.SECONDS));
                awaitLogged(logged, failure);
                assertTrue(EngineLifecycleTasks.run(fixture.engine(), "world_save", () ->
                        fixture.tasks().scheduleAdmittedTask(fixture.engine(), () -> {})));
                firstRelease.countDown();
                EngineBackgroundTasks.BackgroundTaskDrain result = drain.get(5, TimeUnit.SECONDS);
                assertTrue(result.complete());
                assertNotNull(result.failure());
                assertSame(failure, result.failure().getCause());
                assertThrows(IllegalStateException.class, () -> result.requireComplete("Studio cutover"));
            } finally {
                firstRelease.countDown();
            }
        }
    }

    @Test
    public void failureSettledBeforeDrainDoesNotPoisonTheNextTransition() throws Exception {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("previous storage failure");
        AtomicReference<Throwable> logged = new AtomicReference<>();
        try (ExecutorService workers = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = scheduler(workers, logged)) {
            fixture.tasks().scheduleTrackedTask(() -> {
                throw failure;
            });
            workers.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertSame(failure, logged.get());
            fixture.tasks().closeBackgroundTaskAdmission();
            fixture.tasks().drainBackgroundTasks("next transition").requireComplete("next transition");
            assertFalse(fixture.tasks().scheduleTrackedTask(() -> {}));
        }
    }

    @Test
    public void continuationsShareTheOriginalDrainDeadline() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch saveRelease = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2);
             ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = scheduler(workers, new AtomicReference<>())) {
            try {
                fixture.tasks().scheduleTrackedTask(() -> waitForRelease(firstEntered, firstRelease));
                assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
                fixture.tasks().closeBackgroundTaskAdmission();
                Future<EngineBackgroundTasks.BackgroundTaskDrain> drain = startDrain(closer, fixture.tasks());
                assertTrue(EngineLifecycleTasks.run(fixture.engine(), "world_save", () ->
                        fixture.tasks().scheduleAdmittedTask(fixture.engine(),
                                () -> waitForRelease(saveEntered, saveRelease))));
                assertTrue(saveEntered.await(5, TimeUnit.SECONDS));
                TimeUnit.SECONDS.sleep(7);
                firstRelease.countDown();
                EngineBackgroundTasks.BackgroundTaskDrain result = drain.get(10, TimeUnit.SECONDS);
                assertTrue(result.failure() instanceof TimeoutException);
                assertThrows(IllegalStateException.class, () -> result.requireComplete("Studio cutover"));
            } finally {
                firstRelease.countDown();
                saveRelease.countDown();
            }
        }
    }

    @Test
    public void cancellationClaimsAQueuedTaskBeforeItCanStart() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch gate = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            // Mirrors the ForkJoinPool behind J.a: cancel(true) reports success yet cannot stop a
            // callable the pool has already dequeued, so the claim must be decided by the task itself.
            scheduler.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
                Callable<Void> task = invocation.getArgument(0);
                CompletableFuture<Void> handle = new CompletableFuture<>();
                worker.execute(() -> {
                    try {
                        assertTrue(gate.await(5, TimeUnit.SECONDS));
                        task.call();
                        handle.complete(null);
                    } catch (Throwable failure) {
                        handle.completeExceptionally(failure);
                    }
                });
                return handle;
            });
            assertTrue(fixture.tasks().scheduleTrackedTask(() -> ran.set(true)));
            fixture.tasks().cancelBackgroundTasks("close");
            gate.countDown();
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertFalse(ran.get());
            fixture.tasks().closeBackgroundTaskAdmission();
            EngineBackgroundTasks.BackgroundTaskDrain drain = fixture.tasks().drainBackgroundTasks("close");
            assertTrue(drain.complete());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    public void cancellationOfARunningTaskLeavesItsCompletionToTheTask() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = scheduler(workers, new AtomicReference<>())) {
            assertTrue(fixture.tasks().scheduleTrackedTask(() -> waitForReleaseIgnoringInterrupts(entered, release)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fixture.tasks().cancelBackgroundTasks("close");
            fixture.tasks().closeBackgroundTaskAdmission();
            ExecutorService closer = Executors.newSingleThreadExecutor();
            try {
                Future<EngineBackgroundTasks.BackgroundTaskDrain> drain = closer.submit(
                        () -> fixture.tasks().drainBackgroundTasks("close"));
                assertThrows(TimeoutException.class, () -> drain.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                EngineBackgroundTasks.BackgroundTaskDrain result = drain.get(5, TimeUnit.SECONDS);
                assertTrue(result.complete());
                result.requireComplete("close");
            } finally {
                release.countDown();
                closer.shutdownNow();
            }
        }
    }

    private static Future<EngineBackgroundTasks.BackgroundTaskDrain> startDrain(
            ExecutorService closer, EngineBackgroundTasks tasks) {
        AtomicReference<Thread> drainingThread = new AtomicReference<>();
        Future<EngineBackgroundTasks.BackgroundTaskDrain> drain = closer.submit(() -> {
            drainingThread.set(Thread.currentThread());
            return tasks.drainBackgroundTasks("Studio cutover");
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = drainingThread.get();
            if (thread != null && thread.getState() == Thread.State.TIMED_WAITING) {
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getClassName().equals(EngineBackgroundTasks.class.getName())
                            && frame.getMethodName().equals("drainBackgroundTasks")) {
                        return drain;
                    }
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Background drain did not await the initial task");
    }

    private static void awaitLogged(AtomicReference<Throwable> logged, Throwable failure) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (logged.get() == failure) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Background task did not log its failure");
    }

    private static void waitForReleaseIgnoringInterrupts(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                assertTrue(release.await(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                break;
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitForRelease(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            assertTrue(release.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }

    @SuppressWarnings("unchecked")
    private static MockedStatic<J> scheduler(ExecutorService workers, AtomicReference<Throwable> logged) {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Void> task = invocation.getArgument(0);
            return workers.submit(() -> {
                try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                    logging.when(() -> IrisLogging.reportError(any(Throwable.class))).thenAnswer(call -> {
                        logged.set(call.getArgument(0));
                        return null;
                    });
                    return task.call();
                }
            });
        });
        return scheduler;
    }

    private static Fixture fixture() {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessions()).thenReturn(sessions);
        when(engine.getGenerationSessionId()).thenAnswer(invocation -> sessions.currentSessionId());
        return new Fixture(tasks, engine);
    }

    private record Fixture(EngineBackgroundTasks tasks, Engine engine) {
    }
}
