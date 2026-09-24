package art.arcane.iris.probe;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class RegionGenerationWindowTest {
    @Test
    public void retainsWorkersAndThreadLocalScratchAcrossBoundedBatches() throws Exception {
        Set<Thread> firstWorkers = ConcurrentHashMap.newKeySet();
        AtomicInteger allocations = new AtomicInteger();
        ThreadLocal<Integer> scratch = ThreadLocal.withInitial(allocations::incrementAndGet);
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        RegionGenerationWindow workers = new RegionGenerationWindow(2, RegionGenerationWindow.Policy.EMBEDDED);
        try (workers) {
            workers.process(new RegionGenerationWindow.Request<>(2, 2, index -> {
                firstWorkers.add(Thread.currentThread());
                scratch.get();
                bothStarted.countDown();
                assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
                return index;
            }, (index, value) -> { }));
            assertEquals(2, allocations.get());
            workers.process(new RegionGenerationWindow.Request<>(16, 1, index -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    assertTrue(firstWorkers.contains(Thread.currentThread()));
                    return scratch.get();
                } finally {
                    active.decrementAndGet();
                }
            }, (index, value) -> assertTrue(value > 0)));
            assertEquals(2, allocations.get());
            assertEquals(1, maximum.get());
        }
        for (Thread worker : firstWorkers) {
            worker.join(1000L);
            assertFalse(worker.isAlive());
        }
        workers.close();
        assertThrows(IllegalStateException.class, () -> workers.process(
                new RegionGenerationWindow.Request<>(1, 1, index -> index, (index, value) -> { })));
    }

    @Test(timeout = 5000)
    public void sinkFailureDrainsWorkersAndPermanentlyClosesSession() {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger exited = new AtomicInteger();
        try (RegionGenerationWindow workers = new RegionGenerationWindow(2, RegionGenerationWindow.Policy.EMBEDDED)) {
            assertThrows(IllegalStateException.class, () -> workers.process(
                    new RegionGenerationWindow.Request<>(2, 2, index -> {
                        if (index == 0) {
                            assertTrue(entered.await(2, TimeUnit.SECONDS));
                            return index;
                        }
                        entered.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } finally {
                            exited.incrementAndGet();
                        }
                        return index;
                    }, (index, value) -> {
                        throw new IllegalStateException("Expected sink failure");
                    })));
            assertEquals(1, exited.get());
            assertThrows(IllegalStateException.class, workers::requireOpen);
            assertThrows(IllegalStateException.class, () -> workers.process(
                    new RegionGenerationWindow.Request<>(1, 1, index -> index, (index, value) -> { })));
        }
    }

    @Test
    public void replenishesWorkersWhileEarlierChunksRemainBlocked() throws Exception {
        CountDownLatch replacementStarted = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        List<Integer> completed = new ArrayList<>();
        try (RegionGenerationWindow workers = new RegionGenerationWindow(2, RegionGenerationWindow.Policy.EMBEDDED)) {
            workers.process(new RegionGenerationWindow.Request<>(16, 2, index -> {
                int running = active.incrementAndGet();
                maximum.accumulateAndGet(running, Math::max);
                try {
                    if (index == 0 && !replacementStarted.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("A completed worker was not replenished");
                    }
                    if (index == 2) {
                        replacementStarted.countDown();
                    }
                    return index;
                } finally {
                    active.decrementAndGet();
                }
            }, (index, value) -> completed.add(value)));
        }
        assertEquals(16, completed.size());
        assertTrue(completed.indexOf(1) < completed.indexOf(0));
        assertTrue(maximum.get() <= 2);
        assertEquals(0, active.get());
    }

    @Test
    public void thirtyTwoWorkersRemainBoundedWhileReplenishing() throws Exception {
        CountDownLatch initialWorkers = new CountDownLatch(32);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        try (RegionGenerationWindow workers = new RegionGenerationWindow(32, RegionGenerationWindow.Policy.EMBEDDED)) {
            workers.process(new RegionGenerationWindow.Request<>(97, 32, index -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    if (index < 32) {
                        initialWorkers.countDown();
                        assertTrue(initialWorkers.await(5, TimeUnit.SECONDS));
                    }
                    return index;
                } finally {
                    active.decrementAndGet();
                }
            }, (index, value) -> completed.incrementAndGet()));
        }
        assertEquals(32, maximum.get());
        assertEquals(97, completed.get());
        assertEquals(0, active.get());
        assertThrows(IllegalArgumentException.class,
                () -> new RegionGenerationWindow.Request<>(97, 33, index -> index, (index, value) -> { }));
    }

    @Test(timeout = 5000)
    public void interruptsAndDrainsOtherWorkersAfterFailure() {
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        try (RegionGenerationWindow workers = new RegionGenerationWindow(2, RegionGenerationWindow.Policy.EMBEDDED)) {
            assertThrows(Exception.class, () -> workers.process(
                    new RegionGenerationWindow.Request<>(2, 2, index -> {
                        if (index == 0) {
                            assertTrue(blocked.await(5, TimeUnit.SECONDS));
                            throw new IllegalStateException("Expected fixture failure");
                        }
                        blocked.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException failure) {
                            interrupted.incrementAndGet();
                            Thread.currentThread().interrupt();
                        }
                        return index;
                    }, (index, value) -> { })));
        }
        assertEquals(1, interrupted.get());
    }

    @Test
    public void drainsStartedWorkBeforeReportingFailure() {
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger finished = new AtomicInteger();
        try (RegionGenerationWindow workers = new RegionGenerationWindow(2, RegionGenerationWindow.Policy.EMBEDDED)) {
            assertThrows(Exception.class, () -> workers.process(
                    new RegionGenerationWindow.Request<>(4, 2, index -> {
                        if (index == 0) {
                            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                            throw new IllegalStateException("Expected fixture failure");
                        }
                        secondStarted.countDown();
                        finished.incrementAndGet();
                        return index;
                    }, (index, value) -> { })));
        }
        assertTrue(finished.get() >= 1);
    }

    @Test(timeout = 5000)
    public void completionTimeoutInterruptsAndDrainsActiveWork() {
        AtomicInteger completed = new AtomicInteger();
        RegionGenerationWindow.Policy policy = new RegionGenerationWindow.Policy(
                Duration.ofMillis(50), Duration.ofSeconds(1), false);
        try (RegionGenerationWindow workers = new RegionGenerationWindow(1, policy)) {
            assertThrows(TimeoutException.class, () -> workers.process(
                    new RegionGenerationWindow.Request<>(1, 1, index -> {
                        try {
                            new CountDownLatch(1).await();
                        } finally {
                            completed.incrementAndGet();
                        }
                        return index;
                    }, (index, value) -> { })));
        }
        assertEquals(1, completed.get());
    }

    @Test(timeout = 10000)
    public void standaloneStalledWorkerStopsProcessBeforeResourceCleanup() throws Exception {
        Path directory = Files.createTempDirectory("iris-worker-shutdown-");
        Path cleanup = directory.resolve("cleanup");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classpath = Path.of(RegionGenerationWindowTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator
                + Path.of(RegionGenerationWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", classpath, UnresponsiveProcess.class.getName(), cleanup.toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue("Offline failure did not terminate", process.waitFor(5, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(output, 70, process.exitValue());
            assertTrue(output, output.contains("Generation workers did not stop"));
            assertTrue(output, output.contains("Expected process fixture failure"));
            assertFalse("Resources were closed while generation was active", Files.exists(cleanup));
        } finally {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            Files.deleteIfExists(cleanup);
            Files.delete(directory);
        }
    }

    public static final class UnresponsiveProcess {
        public static void main(String[] arguments) throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch never = new CountDownLatch(1);
            RegionGenerationWindow.Policy policy = new RegionGenerationWindow.Policy(
                    Duration.ofSeconds(1), Duration.ofMillis(25), true);
            try (RegionGenerationWindow workers = new RegionGenerationWindow(2, policy)) {
                workers.process(new RegionGenerationWindow.Request<>(2, 2, index -> {
                    if (index == 0) {
                        if (!entered.await(1, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Worker did not start");
                        }
                        throw new IllegalStateException("Expected process fixture failure");
                    }
                    entered.countDown();
                    while (true) {
                        try {
                            never.await();
                        } catch (InterruptedException ignored) {
                            continue;
                        }
                        return index;
                    }
                }, (index, value) -> { }));
            } finally {
                Files.writeString(Path.of(arguments[0]), "closed");
            }
        }
    }
}
