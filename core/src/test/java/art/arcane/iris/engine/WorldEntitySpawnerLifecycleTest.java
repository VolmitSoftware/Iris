package art.arcane.iris.engine;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedWorldManager;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldEntitySpawnerLifecycleTest {
    @Test
    public void queuedSpawnIsDiscardedWhenTheManagerCloses() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Future<Boolean> waiting = fixture.spawn(false, true);
            Runnable task = fixture.nextTask();
            fixture.manager.close();

            task.run();

            assertFalse(waiting.get(1L, TimeUnit.SECONDS));
            verify(fixture.world, never()).isChunkLoaded(anyInt(), anyInt());
            assertEquals(0, fixture.sessions.activeLeases());
        }
    }

    @Test
    public void queuedSpawnIsDiscardedWhenEngineDrainBegins() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Future<Boolean> waiting = fixture.spawn(false, true);
            Runnable task = fixture.nextTask();
            when(fixture.engine.isClosing()).thenReturn(true);
            fixture.sessions.sealAndAwait("closing", 1_000L);

            task.run();

            assertFalse(waiting.get(1L, TimeUnit.SECONDS));
            verify(fixture.world, never()).isChunkLoaded(anyInt(), anyInt());
        }
    }

    @Test
    public void interruptedSpawnWaitDiscardsItsQueuedWork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Future<Boolean> waiting = fixture.spawn(true, true);
            Runnable task = fixture.nextTask();
            assertFalse(waiting.get(1L, TimeUnit.SECONDS));
            assertTrue(fixture.interrupted.get());

            task.run();

            verify(fixture.world, never()).isChunkLoaded(anyInt(), anyInt());
        }
    }

    @Test
    public void rejectedSpawnDoesNotLeaveExecutableWork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Future<Boolean> waiting = fixture.spawn(false, false);
            Runnable task = fixture.nextTask();
            assertFalse(waiting.get(1L, TimeUnit.SECONDS));

            task.run();

            verify(fixture.world, never()).isChunkLoaded(anyInt(), anyInt());
        }
    }

    @Test
    public void admittedSpawnHoldsTheEngineDrainOnItsOwnerThread() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Thread> observed = new AtomicReference<>();
            when(fixture.world.isChunkLoaded(7, -4)).thenAnswer(invocation -> {
                observed.set(Thread.currentThread());
                assertSame(fixture.engine, IrisContext.get().getEngine());
                assertEquals(1, fixture.sessions.activeLeases());
                entered.countDown();
                assertTrue(release.await(2L, TimeUnit.SECONDS));
                return false;
            });
            Future<Boolean> waiting = fixture.spawn(false, true);
            Runnable task = fixture.nextTask();
            ExecutorService owner = Executors.newSingleThreadExecutor();
            ExecutorService closer = Executors.newSingleThreadExecutor();
            try {
                Future<?> running = owner.submit(task);
                assertTrue(entered.await(1L, TimeUnit.SECONDS));
                CountDownLatch closing = new CountDownLatch(1);
                Future<?> drained = closer.submit(() -> {
                    closing.countDown();
                    fixture.sessions.sealAndAwait("closing", 1_500L);
                    return null;
                });
                assertTrue(closing.await(1L, TimeUnit.SECONDS));
                waitForSeal(fixture.sessions);
                assertFalse(drained.isDone());
                assertEquals(1, fixture.sessions.activeLeases());
                release.countDown();
                running.get(1L, TimeUnit.SECONDS);
                drained.get(1L, TimeUnit.SECONDS);
                assertTrue(waiting.get(1L, TimeUnit.SECONDS));
                assertNotNull(observed.get());
                assertFalse(observed.get() == fixture.callerThread.get());
                assertEquals(0, fixture.sessions.activeLeases());
            } finally {
                release.countDown();
                owner.shutdownNow();
                closer.shutdownNow();
                assertTrue(owner.awaitTermination(2L, TimeUnit.SECONDS));
                assertTrue(closer.awaitTermination(2L, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void unexpectedSpawnFailureIsReportedWithItsCause() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("unexpected chunk failure");
            when(fixture.world.isChunkLoaded(7, -4)).thenThrow(failure);
            Future<Boolean> waiting = fixture.spawn(false, true);
            Runnable task = fixture.nextTask();

            try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                task.run();
                logging.verify(() -> IrisLogging.reportError(
                        eq("Failed to spawn Iris entities in chunk 7,-4."), same(failure)));
            }

            assertFalse(waiting.get(1L, TimeUnit.SECONDS));
            assertEquals(0, fixture.sessions.activeLeases());
        }
    }

    @Test
    public void queuedCountIsDiscardedWithoutPublishingAfterClose() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.manager.entityCount = 19;
            fixture.manager.entityCountValid = true;
            Future<Boolean> waiting = fixture.count();
            Runnable task = fixture.nextTask();
            fixture.manager.close();

            task.run();

            assertFalse(waiting.get(1L, TimeUnit.SECONDS));
            assertFalse(fixture.manager.entityCountValid);
            assertEquals(19, fixture.manager.entityCount);
            verify(fixture.world, never()).getLivingEntities();
        }
    }

    @Test
    public void timedOutCountCannotRunLaterAndANewCountRecovers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Future<Boolean> waiting = fixture.count();
            Runnable expired = fixture.nextTask();
            assertFalse(waiting.get(3L, TimeUnit.SECONDS));
            assertFalse(fixture.manager.entityCountValid);

            expired.run();
            verify(fixture.world, never()).getLivingEntities();
            when(fixture.world.getLivingEntities()).thenReturn(List.of(mock(LivingEntity.class)));
            setField(IrisWorldManager.class, fixture.manager, "cl", new ChronoLatch(1L));
            Future<Boolean> retry = fixture.count();
            fixture.nextTask().run();

            assertFalse(retry.get(1L, TimeUnit.SECONDS));
            assertTrue(fixture.manager.entityCountValid);
            assertEquals(1, fixture.manager.entityCount);
            assertEquals(0, fixture.sessions.activeLeases());
        }
    }

    private static void waitForSeal(GenerationSessionManager sessions) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while (System.nanoTime() < deadline) {
            try (GenerationSessionLease ignored = sessions.acquire("seal_probe")) {
                Thread.onSpinWait();
            } catch (GenerationSessionException expected) {
                return;
            }
        }
        throw new AssertionError("Generation session did not seal within one second.");
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final World world = mock(World.class);
        private final IrisWorld irisWorld = mock(IrisWorld.class);
        private final GenerationSessionManager sessions = new GenerationSessionManager();
        private final IrisWorldManager manager;
        private final WorldEntitySpawner spawner;
        private final ExecutorService caller = Executors.newSingleThreadExecutor();
        private final BlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
        private final AtomicBoolean interrupted = new AtomicBoolean();
        private final AtomicReference<Thread> callerThread = new AtomicReference<>();

        private Fixture() throws Exception {
            EngineTarget target = mock(EngineTarget.class);
            when(irisWorld.name()).thenReturn("spawner-lifecycle");
            when(irisWorld.hasPlatformWorld()).thenReturn(true);
            when(target.getWorld()).thenReturn(irisWorld);
            when(engine.getTarget()).thenReturn(target);
            when(engine.getWorld()).thenReturn(irisWorld);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            manager = new IrisWorldManager(engine);
            setField(EngineAssignedWorldManager.class, manager, "started", new AtomicBoolean(true));
            spawner = manager.entitySpawner;
        }

        private Future<Boolean> spawn(boolean interrupt, boolean accepted) {
            return caller.submit(() -> {
                callerThread.set(Thread.currentThread());
                try (MockedStatic<J> scheduler = mockStatic(J.class)) {
                    scheduler.when(() -> J.runRegion(eq(world), eq(7), eq(-4), any(Runnable.class)))
                            .thenAnswer(invocation -> {
                                queued.add(invocation.getArgument(3));
                                return accepted;
                            });
                    if (interrupt) {
                        Thread.currentThread().interrupt();
                    }
                    Method method = WorldEntitySpawner.class.getDeclaredMethod(
                            "spawnChunkSafely", World.class, int.class, int.class, boolean.class);
                    method.setAccessible(true);
                    try {
                        return (Boolean) method.invoke(spawner, world, 7, -4, false);
                    } catch (InvocationTargetException failure) {
                        throw new IllegalStateException(failure.getCause());
                    } finally {
                        interrupted.set(Thread.interrupted());
                    }
                }
            });
        }

        private Future<Boolean> count() {
            return caller.submit(() -> {
                IrisSettings settings = new IrisSettings();
                settings.getWorld().setTargetSpawnEntitiesPerChunk(0.0D);
                try (MockedStatic<J> scheduler = mockStatic(J.class);
                     MockedStatic<BukkitWorldBinding> bindings = mockStatic(BukkitWorldBinding.class);
                     MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
                     MockedStatic<PregeneratorJob> pregenerator = mockStatic(PregeneratorJob.class);
                     MockedStatic<IrisSettings> configured = mockStatic(IrisSettings.class);
                     MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                    bindings.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
                    configured.when(IrisSettings::get).thenReturn(settings);
                    scheduler.when(() -> J.runGlobal(any(Runnable.class))).thenAnswer(invocation -> {
                        queued.add(invocation.getArgument(0));
                        return true;
                    });
                    return spawner.onAsyncTick();
                }
            });
        }

        private Runnable nextTask() throws InterruptedException {
            Runnable task = queued.poll(5L, TimeUnit.SECONDS);
            assertNotNull("Owner callback was not scheduled", task);
            return task;
        }

        @Override
        public void close() throws Exception {
            caller.shutdownNow();
            assertTrue(caller.awaitTermination(2L, TimeUnit.SECONDS));
            manager.close();
        }
    }
}
