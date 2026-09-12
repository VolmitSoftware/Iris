package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.context.IrisContext;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EngineLifecycleTasksTest {
    @Test
    public void activeLifecycleTaskHoldsGenerationDrainUntilCompletion() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessions()).thenReturn(manager);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> taskFailure = new AtomicReference<>();
        AtomicReference<Engine> contextEngine = new AtomicReference<>();
        Thread task = new Thread(() -> {
            try {
                EngineLifecycleTasks.run(engine, "world_manager_test", () -> {
                    contextEngine.set(IrisContext.get().getEngine());
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Throwable exception) {
                taskFailure.set(exception);
            }
        });
        task.start();

        assertTrue(entered.await(1L, TimeUnit.SECONDS));
        CountDownLatch drained = new CountDownLatch(1);
        Thread sealer = new Thread(() -> {
            try {
                manager.sealAndAwait("hotload", 1_000L);
                drained.countDown();
            } catch (GenerationSessionException exception) {
                taskFailure.set(exception);
            }
        });
        sealer.start();

        waitForSeal(manager);
        assertFalse(drained.await(50L, TimeUnit.MILLISECONDS));
        release.countDown();
        assertTrue(drained.await(1L, TimeUnit.SECONDS));
        task.join(1_000L);
        sealer.join(1_000L);
        assertSame(engine, contextEngine.get());
        assertTrue(taskFailure.get() == null);
    }

    @Test
    public void sealedLifecycleRejectsQueuedManagerTask() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager();
        manager.sealAndAwait("hotload", 1_000L);
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessions()).thenReturn(manager);
        when(engine.isClosing()).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean();

        boolean accepted = EngineLifecycleTasks.run(engine, "queued_world_manager_test", () -> ran.set(true));

        assertFalse(accepted);
        assertFalse(ran.get());
    }

    @Test
    public void queuedLifecycleWorkCannotBlockTheOwnerThreadCheckpointDuringCutover() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager(true);
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessions()).thenReturn(manager);
        ExecutorService owner = Executors.newSingleThreadExecutor();
        ExecutorService reloader = Executors.newSingleThreadExecutor();
        AtomicBoolean lifecycleRan = new AtomicBoolean();
        AtomicBoolean checkpointRan = new AtomicBoolean();
        try {
            CompletableFuture<Void> cutover = CompletableFuture.runAsync(() -> {
                try (GenerationTransitionGate.Transition ignored = manager.transitionGate().beginTransition(2_000L)) {
                    owner.submit(() -> {
                        assertFalse(EngineLifecycleTasks.run(engine, "world_manager", () -> lifecycleRan.set(true)));
                        assertEquals("unavailable", EngineLifecycleTasks.call(engine, "manager_query",
                                () -> "unexpected", "unavailable"));
                        assertNull(IrisContext.get());
                    }).get(1L, TimeUnit.SECONDS);
                    owner.submit(() -> checkpointRan.set(true)).get(1L, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }, reloader);
            cutover.get(3L, TimeUnit.SECONDS);
            assertFalse(lifecycleRan.get());
            assertTrue(checkpointRan.get());
            assertEquals(0, manager.activeLeases());
            assertTrue(owner.submit(() -> EngineLifecycleTasks.run(engine, "next_tick",
                    () -> lifecycleRan.set(true))).get(1L, TimeUnit.SECONDS));
            assertTrue(lifecycleRan.get());
            assertEquals(0, manager.activeLeases());
        } finally {
            owner.shutdownNow();
            reloader.shutdownNow();
            assertTrue(owner.awaitTermination(2L, TimeUnit.SECONDS));
            assertTrue(reloader.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void nestedLifecycleWorkKeepsItsAlreadyAdmittedSession() throws Exception {
        GenerationSessionManager manager = new GenerationSessionManager(true);
        Engine engine = mock(Engine.class);
        when(engine.getGenerationSessions()).thenReturn(manager);
        try (GenerationSessionLease outer = manager.acquire("outer");
             IrisContext.Scope ignored = IrisContext.open(engine, outer.sessionId(), null)) {
            assertTrue(EngineLifecycleTasks.run(engine, "nested", () -> assertEquals(2, manager.activeLeases())));
            assertEquals(1, manager.activeLeases());
        }
        assertEquals(0, manager.activeLeases());
    }

    private void waitForSeal(GenerationSessionManager manager) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (System.nanoTime() < deadline) {
            try (GenerationSessionLease ignored = manager.acquire("seal_probe")) {
                Thread.onSpinWait();
            } catch (GenerationSessionException expected) {
                return;
            }
        }
        throw new AssertionError("Generation session did not seal within one second.");
    }
}
