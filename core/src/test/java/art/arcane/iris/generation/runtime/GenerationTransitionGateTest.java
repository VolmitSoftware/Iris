package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class GenerationTransitionGateTest {
    @Test
    public void bothSessionAndRouteNestingOrdersDrainBeforeNewWorkEnters() throws Exception {
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        GenerationTransitionGate gate = sessions.transitionGate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch requested = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GenerationSessionLease outer = sessions.acquire("manager");
        try {
            CompletableFuture<Void> transition = CompletableFuture.runAsync(() -> {
                requested.countDown();
                try (GenerationTransitionGate.Transition ignored = gate.beginTransition(5_000L)) {
                    acquired.countDown();
                    await(release);
                }
            }, executor);
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
            try (GenerationTransitionGate.Participation route = gate.enter();
                 GenerationSessionLease nested = sessions.acquire("nested terrain")) {
                assertTrue(nested.sessionId() == outer.sessionId());
            }
            outer.close();
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            CompletableFuture<GenerationSessionLease> fresh = CompletableFuture.supplyAsync(() -> {
                try {
                    return sessions.acquire("fresh terrain");
                } catch (GenerationSessionException failure) {
                    throw new IllegalStateException(failure);
                }
            }, executor);
            assertThrows(TimeoutException.class, () -> fresh.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            transition.get(5, TimeUnit.SECONDS);
            fresh.get(5, TimeUnit.SECONDS).close();
        } finally {
            outer.close();
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void detachedWorkCanAttachOnAnotherThreadAndCloseAfterHandoff() throws Exception {
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        GenerationTransitionGate gate = sessions.transitionGate();
        GenerationTransitionGate.Participation route = gate.enter();
        GenerationSessionLease lease = sessions.acquire("async pipeline");
        route.detachThread();
        lease.detachThread();
        IllegalStateException timedOut = assertThrows(IllegalStateException.class, () -> gate.beginTransition(20L));
        assertTrue(timedOut.getMessage().startsWith("Timed out"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch requested = new CountDownLatch(1);
        try {
            CompletableFuture<Void> transition = CompletableFuture.runAsync(() -> {
                requested.countDown();
                try (GenerationTransitionGate.Transition ignored = gate.beginTransition(5_000L)) {
                }
            }, executor);
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> callback = CompletableFuture.runAsync(() -> {
                try (GenerationTransitionGate.Participation attached = route.attach();
                     GenerationSessionLease nested = sessions.acquire("async continuation")) {
                    assertTrue(nested.sessionId() == lease.sessionId());
                } catch (GenerationSessionException failure) {
                    throw new IllegalStateException(failure);
                } finally {
                    lease.close();
                    route.close();
                }
            }, executor);
            callback.get(5, TimeUnit.SECONDS);
            transition.get(5, TimeUnit.SECONDS);
            lease.detachThread();
            route.detachThread();
            try (GenerationTransitionGate.Transition ignored = gate.beginTransition(5_000L)) {
            }
        } finally {
            lease.close();
            route.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void interruptedTransitionRestoresAdmission() throws Exception {
        GenerationTransitionGate gate = new GenerationTransitionGate(true);
        GenerationTransitionGate.Participation operation = gate.enter();
        CountDownLatch requested = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            requested.countDown();
            try (GenerationTransitionGate.Transition ignored = gate.beginTransition(5_000L)) {
                failure.set(new AssertionError("Transition entered while an operation was still active."));
            } catch (IllegalStateException expected) {
                if (!Thread.currentThread().isInterrupted()) {
                    failure.set(new AssertionError("Interrupted status was lost."));
                } else {
                    failure.set(expected);
                }
            }
        });
        try {
            worker.start();
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(5_000L);
            assertFalse(worker.isAlive());
            assertNotNull(failure.get());
            assertTrue(failure.get() instanceof IllegalStateException);
        } finally {
            operation.close();
            worker.interrupt();
            worker.join(5_000L);
        }
        try (GenerationTransitionGate.Transition ignored = gate.beginTransition(5_000L)) {
        }
    }

    @Test
    public void productionGateReusesNoopParticipation() {
        GenerationTransitionGate gate = new GenerationSessionManager().transitionGate();
        GenerationTransitionGate.Participation first = gate.enter();
        assertSame(first, gate.enter());
        assertSame(first, first.attach());
        first.detachThread();
        first.close();
        assertSame(first, gate.enter());
        assertThrows(IllegalStateException.class, () -> gate.beginTransition(100L));
    }

    @Test
    public void interruptedManagerExitsWithoutRunningWhileTransitionRemainsExclusive() throws Exception {
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        Engine engine = mock(Engine.class);
        when(engine.isClosing()).thenReturn(true);
        CountDownLatch requested = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean taskRan = new AtomicBoolean();
        Thread manager = new Thread(() -> {
            requested.countDown();
            try (GenerationSessionLease ignored = sessions.acquireForEngine(engine, "world manager loop")) {
                taskRan.set(true);
            } catch (GenerationSessionException expected) {
                if (!expected.isExpectedTeardown() || !Thread.currentThread().isInterrupted()) {
                    failure.set(new AssertionError("Manager cancellation lost its teardown or interruption state."));
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });
        try (GenerationTransitionGate.Transition ignored = sessions.transitionGate().beginTransition(5_000L)) {
            manager.start();
            assertTrue(requested.await(5, TimeUnit.SECONDS));
            manager.interrupt();
            manager.join(1_000L);
            assertFalse("Manager must stop without waiting for the transition to finish", manager.isAlive());
            assertFalse(taskRan.get());
            assertNull(failure.get());
            assertEquals(0, sessions.activeLeases());
        } finally {
            manager.interrupt();
            manager.join(5_000L);
        }
        try (GenerationSessionLease ignored = sessions.acquire("next runtime")) {
            assertEquals(1, sessions.activeLeases());
        }
    }

    @Test
    public void nonblockingAdmissionRejectsPendingCutoverWithoutLeakingParticipation() throws Exception {
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        Engine engine = mock(Engine.class);
        GenerationSessionLease existing = sessions.acquire("existing");
        ExecutorService freshWorker = Executors.newSingleThreadExecutor();
        CountDownLatch transitioned = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reloader = new Thread(() -> {
            try (GenerationTransitionGate.Transition ignored = sessions.transitionGate().beginTransition(2_000L)) {
                transitioned.countDown();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        try {
            reloader.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
            while (reloader.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.TIMED_WAITING, reloader.getState());
            assertTrue(freshWorker.submit(() -> sessions.tryAcquireForEngine(engine, "new tick").isEmpty())
                    .get(1L, TimeUnit.SECONDS));
            assertEquals(1, sessions.activeLeases());
            try (GenerationSessionLease nested = sessions.tryAcquireForEngine(engine, "existing nested").orElseThrow()) {
                assertEquals(existing.sessionId(), nested.sessionId());
            }
            existing.close();
            assertTrue(transitioned.await(1L, TimeUnit.SECONDS));
            reloader.join(1_000L);
            assertNull(failure.get());
            assertEquals(0, sessions.activeLeases());
            Optional<GenerationSessionLease> next = sessions.tryAcquireForEngine(engine, "next tick");
            assertTrue(next.isPresent());
            next.orElseThrow().close();
            assertEquals(0, sessions.activeLeases());
        } finally {
            existing.close();
            reloader.interrupt();
            reloader.join(2_000L);
            freshWorker.shutdownNow();
            assertTrue(freshWorker.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test transition to finish.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
