package art.arcane.iris.generation.runtime;

import art.arcane.iris.probe.StubPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.IrisRuntimeState;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ProbeBackgroundTasksTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Before
    public void bindRuntime() {
        IrisPlatforms.bind(new StubPlatform(temporary.getRoot()));
    }

    @After
    public void resetRuntime() {
        IrisRuntimeState.reset();
        StubPlatform.errorSink(null);
    }

    @Test(timeout = 15_000L)
    public void waitsForTrackedContinuationBeforeReturning() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch continuationEntered = new CountDownLatch(1);
        CountDownLatch continuationRelease = new CountDownLatch(1);
        try (ExecutorService closer = Executors.newSingleThreadExecutor()) {
            try {
                tasks.scheduleTrackedTask(() -> {
                    awaitRelease(firstEntered, firstRelease);
                    tasks.scheduleTrackedTask(() -> awaitRelease(continuationEntered, continuationRelease));
                });
                assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
                Future<?> completion = startDrain(closer, tasks);
                assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
                firstRelease.countDown();
                assertTrue(continuationEntered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
                continuationRelease.countDown();
                completion.get(5, TimeUnit.SECONDS);
            } finally {
                firstRelease.countDown();
                continuationRelease.countDown();
            }
        }
    }

    @Test(timeout = 15_000L)
    public void propagatesAnAsynchronousTrackedFailure() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("tracked generation failure");
        CountDownLatch reported = new CountDownLatch(1);
        StubPlatform.errorSink(error -> reported.countDown());
        try (ExecutorService closer = Executors.newSingleThreadExecutor()) {
            try {
                tasks.scheduleTrackedTask(() -> {
                    awaitRelease(entered, release);
                    throw failure;
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                Future<?> completion = startDrain(closer, tasks);
                assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                ExecutionException observed = assertThrows(ExecutionException.class,
                        () -> completion.get(5, TimeUnit.SECONDS));
                assertSame(failure, observed.getCause().getCause().getCause());
                assertTrue(reported.await(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private static Future<?> startDrain(ExecutorService closer, EngineBackgroundTasks tasks) {
        AtomicReference<Thread> drainingThread = new AtomicReference<>();
        Future<?> completion = closer.submit(() -> {
            drainingThread.set(Thread.currentThread());
            ProbeBackgroundTasks.await(tasks);
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = drainingThread.get();
            if (thread != null && thread.getState() == Thread.State.TIMED_WAITING) {
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getClassName().equals(EngineBackgroundTasks.class.getName())
                            && frame.getMethodName().equals("drainBackgroundTasks")) {
                        return completion;
                    }
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Probe barrier did not await its initial task");
    }

    private static void awaitRelease(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }

}
