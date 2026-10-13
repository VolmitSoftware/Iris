package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.runtime.EngineEffectsProvider;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineWorldManagerProvider;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.IrisRuntimeState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.task.J;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class RealPackProbeSupportBarrierTest {
    @AfterClass
    public static void resetRuntime() {
        IrisRuntimeState.reset();
    }

    @Test
    public void closesOwnedRuntimeServicesAndPreservesUnrelatedServices() throws Exception {
        URL resource = RealPackProbeSupportBarrierTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        IrisServices.register(String.class, "unrelated");
        try {
            try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                    new File(resource.toURI()), "cohesion", "[workspace-cleanup]")) {
                assertNotNull(IrisServices.getOrNull(EngineEffectsProvider.class));
            }
            assertFalse(IrisPlatforms.isBound());
            assertNull(IrisServices.getOrNull(PreservationRegistry.class));
            assertNull(IrisServices.getOrNull(EngineWorldManagerProvider.class));
            assertNull(IrisServices.getOrNull(EngineEffectsProvider.class));
            assertNull(IrisServices.getOrNull(EnginePlatformHooks.class));
            assertEquals("unrelated", IrisServices.get(String.class));
        } finally {
            IrisServices.remove(String.class);
        }
    }

    @Test(timeout = 30_000L)
    public void waitsForCrossPoolContinuationsAndCollectsTheirErrors() throws Exception {
        URL resource = RealPackProbeSupportBarrierTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                new File(resource.toURI()), "cohesion", "[worker-barrier]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337L, false, "barrier")) {
            for (int repeat = 0; repeat < 40; repeat++) {
                verifyCrossPoolContinuation(session, MultiBurst.ioBurst, MultiBurst.hydrology);
                verifyCrossPoolContinuation(session, MultiBurst.hydrology, MultiBurst.ioBurst);
            }
        }
        PlatformLeakGuard.requireClean("closed real-pack workspace", "end");
    }

    @Test(timeout = 10_000L)
    public void waitsForActiveWorkWithinItsDeadline() throws Exception {
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> task = pool.submit(() -> {
                entered.countDown();
                Thread.sleep(100L);
                return null;
            });
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            RealPackProbeSupport.awaitGenerationWorkers(List.of(pool),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            assertTrue(task.isDone());
            task.get();
        }
    }

    @Test(timeout = 10_000L)
    public void rejectsUnsettledWorkAtItsDeadlineWithoutCancellingIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> task = pool.submit(() -> {
                entered.countDown();
                release.await();
                return null;
            });
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> RealPackProbeSupport.awaitGenerationWorkers(List.of(pool),
                                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50)));
                assertTrue(failure.getMessage().contains("pool 1/1"));
                assertTrue(failure.getMessage().contains("active ="));
                assertFalse(task.isDone());
            } finally {
                release.countDown();
            }
            RealPackProbeSupport.awaitGenerationWorkers(List.of(pool),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
            task.get();
        }
    }

    @Test(timeout = 10_000L)
    public void interruptionStopsAnActiveWorkerWait() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> task = pool.submit(() -> {
                entered.countDown();
                release.await();
                return null;
            });
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch waiting = new CountDownLatch(1);
            Thread waiter = new Thread(() -> {
                waiting.countDown();
                try {
                    RealPackProbeSupport.awaitGenerationWorkers(List.of(pool),
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
                } catch (Throwable interrupted) {
                    failure.set(interrupted);
                }
            });
            try {
                assertTrue(entered.await(5L, TimeUnit.SECONDS));
                waiter.start();
                assertTrue(waiting.await(5L, TimeUnit.SECONDS));
                waiter.interrupt();
                waiter.join(5_000L);
                assertFalse(waiter.isAlive());
                assertTrue(failure.get() instanceof InterruptedException);
                assertFalse(task.isDone());
            } finally {
                release.countDown();
                waiter.interrupt();
                waiter.join(5_000L);
            }
            task.get();
        }
    }

    private static void verifyCrossPoolContinuation(RealPackProbeSupport.EngineSession session,
                                                     MultiBurst first, MultiBurst second) throws Exception {
        IllegalStateException failure = new IllegalStateException("nested generation failure");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            first.lazy(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interruption);
                }
                Runnable failedTask = () -> {
                    throw failure;
                };
                second.lazy(() -> first.lazy(() -> second.lazy(() -> J.a(failedTask))));
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain(session.engine());
            assertEquals(1, reports.size());
            assertSame(failure, reports.getFirst());
            assertTrue(RealPackProbeSupport.drainReported().isEmpty());
        } finally {
            release.countDown();
        }
    }

}
