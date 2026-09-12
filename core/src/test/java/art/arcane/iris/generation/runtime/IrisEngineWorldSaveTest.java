package art.arcane.iris.generation.runtime;

import art.arcane.iris.structure.nativegen.NativeStructureOwnershipStore;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisEngineWorldSaveTest {
    @Test
    public void ownerReturnsWhileStorageFlushesAndShutdownDrainWaits() throws Exception {
        Fixture fixture = fixture();
        Thread owner = Thread.currentThread();
        EngineMantle mantle = fixture.engine().getMantle();
        EngineWorldManager worldManager = fixture.engine().getWorldManager();
        doAnswer(invocation -> {
            assertSame(owner, Thread.currentThread());
            return null;
        }).when(mantle).save();
        doAnswer(invocation -> {
            assertSame(owner, Thread.currentThread());
            return null;
        }).when(worldManager).onSave();
        doAnswer(invocation -> {
            assertSame(owner, Thread.currentThread());
            return null;
        }).when(fixture.engine()).saveEngineData();
        try (ExecutorService io = Executors.newSingleThreadExecutor();
             ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = schedule(fixture, io)) {
            try {
                assertTrue(fixture.engine().requestSave());
                assertTrue(fixture.entered().await(5, TimeUnit.SECONDS));
                assertFalse(owner == fixture.flushThread().get());
                verify(fixture.engine().getWorldManager()).onSave();
                verify(fixture.engine()).saveEngineData();
                fixture.tasks().closeBackgroundTaskAdmission();
                fixture.sessions().sealAndAwait("close", 1_000L, true);
                Future<EngineBackgroundTasks.BackgroundTaskDrain> close = closer.submit(
                        () -> fixture.tasks().drainBackgroundTasks("close"));
                assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
                fixture.release().countDown();
                close.get(5, TimeUnit.SECONDS).requireComplete("close");
                assertEquals(0, fixture.sessions().activeLeases());
            } finally {
                fixture.release().countDown();
            }
        }
    }

    @Test
    public void alreadyAdmittedSaveSurvivesConcurrentAdmissionClosure() throws Exception {
        Fixture fixture = fixture();
        try (ExecutorService io = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = schedule(fixture, io)) {
            try {
                assertTrue(EngineLifecycleTasks.run(fixture.engine(), "world_save_event", () -> {
                    fixture.tasks().closeBackgroundTaskAdmission();
                    when(fixture.engine().isClosing()).thenReturn(true);
                    assertTrue(fixture.engine().requestSave());
                }));
                assertTrue(fixture.entered().await(5, TimeUnit.SECONDS));
                assertFalse(fixture.tasks().scheduleTrackedTask(() -> {
                    throw new AssertionError("New background task entered after admission closed");
                }));
                fixture.release().countDown();
                fixture.tasks().drainBackgroundTasks("close").requireComplete("close");
            } finally {
                fixture.release().countDown();
            }
        }
    }

    @Test
    public void storageFailureLogsOriginalCauseAndFailsAnActiveShutdownDrain() throws Exception {
        Fixture fixture = fixture();
        IllegalStateException failure = new IllegalStateException("ownership storage write failed");
        fixture.failure().set(failure);
        AtomicReference<Thread> closeThread = new AtomicReference<>();
        try (ExecutorService io = Executors.newSingleThreadExecutor();
             ExecutorService closer = Executors.newSingleThreadExecutor();
             MockedStatic<J> scheduler = schedule(fixture, io)) {
            try {
                assertTrue(fixture.engine().requestSave());
                assertTrue(fixture.entered().await(5, TimeUnit.SECONDS));
                Future<EngineBackgroundTasks.BackgroundTaskDrain> close = closer.submit(() -> {
                    closeThread.set(Thread.currentThread());
                    return fixture.tasks().drainBackgroundTasks("close");
                });
                awaitDrainWaiter(closeThread);
                fixture.release().countDown();
                EngineBackgroundTasks.BackgroundTaskDrain drain = close.get(5, TimeUnit.SECONDS);
                assertNotNull(drain.failure());
                assertSame(failure, drain.failure().getCause());
                assertThrows(IllegalStateException.class, () -> drain.requireComplete("close"));
                io.submit(() -> {}).get(5, TimeUnit.SECONDS);
                assertSame(failure, fixture.logged().get());
            } finally {
                fixture.release().countDown();
            }
        }
    }

    @Test
    public void continuationRequiresAnActiveLifecycleLease() throws Exception {
        Fixture fixture = fixture();
        assertThrows(IllegalStateException.class,
                () -> fixture.tasks().scheduleAdmittedTask(fixture.engine(), () -> {}));
        fixture.sessions().sealAndAwait("close", 1_000L, true);
        when(fixture.engine().isClosing()).thenReturn(true);
        assertFalse(fixture.engine().requestSave());
    }

    @SuppressWarnings("unchecked")
    private static MockedStatic<J> schedule(Fixture fixture, ExecutorService io) {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Void> task = invocation.getArgument(0);
            return io.submit(() -> {
                try (MockedStatic<NativeStructureOwnershipStore> storage = mockStatic(NativeStructureOwnershipStore.class);
                     MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                    storage.when(() -> NativeStructureOwnershipStore.flush(fixture.engine())).thenAnswer(call -> {
                        fixture.flushThread().set(Thread.currentThread());
                        fixture.entered().countDown();
                        assertTrue(fixture.release().await(5, TimeUnit.SECONDS));
                        if (fixture.failure().get() != null) {
                            throw fixture.failure().get();
                        }
                        return null;
                    });
                    logging.when(() -> IrisLogging.reportError(any(Throwable.class))).thenAnswer(call -> {
                        fixture.logged().set(call.getArgument(0));
                        return null;
                    });
                    return task.call();
                }
            });
        });
        return scheduler;
    }

    private static Fixture fixture() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        GenerationSessionManager sessions = new GenerationSessionManager(true);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getGenerationSessions()).thenReturn(sessions);
        when(engine.getGenerationSessionId()).thenAnswer(invocation -> sessions.currentSessionId());
        when(engine.getMantle()).thenReturn(mock(EngineMantle.class));
        when(engine.getWorldManager()).thenReturn(mock(EngineWorldManager.class));
        Field background = IrisEngine.class.getDeclaredField("backgroundTasks");
        background.setAccessible(true);
        background.set(engine, tasks);
        doCallRealMethod().when(engine).requestSave();
        return new Fixture(engine, tasks, sessions, new CountDownLatch(1), new CountDownLatch(1),
                new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
    }

    private static void awaitDrainWaiter(AtomicReference<Thread> reference) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = reference.get();
            if (thread != null && thread.getState() == Thread.State.TIMED_WAITING) {
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getClassName().equals(EngineBackgroundTasks.class.getName())
                            && frame.getMethodName().equals("drainBackgroundTasks")) {
                        return;
                    }
                }
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        throw new AssertionError("Shutdown did not wait for its ownership storage flush");
    }

    private record Fixture(IrisEngine engine, EngineBackgroundTasks tasks, GenerationSessionManager sessions,
                           CountDownLatch entered, CountDownLatch release, AtomicReference<Thread> flushThread,
                           AtomicReference<IllegalStateException> failure, AtomicReference<Throwable> logged) {
    }
}
