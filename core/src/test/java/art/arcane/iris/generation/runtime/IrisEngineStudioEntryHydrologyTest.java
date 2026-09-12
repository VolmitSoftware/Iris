package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.HydrologyPlanner;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTile;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.iris.generation.context.IrisContext;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisEngineStudioEntryHydrologyTest {
    @Test
    @SuppressWarnings("unchecked")
    public void queuesScopedEntryDemandWithoutBlockingInitialization() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        IrisEngine engine = engine(tasks);
        IrisEngine.GenerationRuntimeBinding binding = mock(IrisEngine.GenerationRuntimeBinding.class);
        GenerationSessionLease lease = mock(GenerationSessionLease.class);
        IrisHydrologyRuntime hydrology = hydrology(engine);
        when(engine.getActiveGenerationRuntimeBinding()).thenReturn(binding);
        when(engine.acquireGenerationLease("studio_entry_hydrology")).thenReturn(lease);
        when(lease.sessionId()).thenReturn(93L);
        doAnswer(invocation -> {
            assertSame(engine, IrisContext.require().getEngine());
            assertEquals(93L, IrisContext.require().getGenerationSessionId());
            assertSame(binding, engine.generationRuntimeScopes.current());
            return null;
        }).when(hydrology).prepareChunkColumns(0, 0);
        AtomicReference<Callable<Void>> queued = new AtomicReference<>();
        try (MockedStatic<J> scheduler = scheduler(queued)) {
            engine.startStudioEntryHydrology(0, 0);
            assertNotNull(queued.get());
            verifyNoInteractions(hydrology, lease);
            verify(engine, never()).acquireGenerationLease(any());
            queued.get().call();
            verify(hydrology).prepareChunkColumns(0, 0);
            verify(lease).close();
            assertNull(IrisContext.get());
            assertNull(engine.generationRuntimeScopes.current());
            tasks.drainBackgroundTasks("test").requireComplete("test");
        }
    }

    @Test
    public void planningFailureStillLogsIfTransitionStartsDuringPlanning() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        IrisEngine engine = engine(tasks);
        GenerationSessionLease lease = mock(GenerationSessionLease.class);
        when(engine.acquireGenerationLease("studio_entry_hydrology")).thenReturn(lease);
        IrisHydrologyRuntime hydrology = hydrology(engine);
        IllegalStateException cause = new IllegalStateException("planning failed");
        doAnswer(invocation -> {
            closing(engine).set(true);
            throw cause;
        }).when(hydrology).prepareChunkColumns(0, 0);
        AtomicReference<Callable<Void>> queued = new AtomicReference<>();
        try (MockedStatic<J> scheduler = scheduler(queued);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            engine.startStudioEntryHydrology(0, 0);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> queued.get().call());
            assertSame(cause, failure.getCause());
            assertTrue(failure.getMessage().contains("Studio entry hydrology preparation failed at 0,0"));
            logging.verify(() -> IrisLogging.reportError(failure));
            verify(lease).close();
        }
    }

    @Test
    public void closedBackgroundAdmissionAndSchedulerFailureAcquireNoLease() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        IrisEngine engine = engine(tasks);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> engine.startStudioEntryHydrology(0, 0));
        assertTrue(failure.getMessage().contains("admission closed"));
        tasks.openBackgroundTaskAdmission();
        RejectedExecutionException rejected = new RejectedExecutionException("scheduler closed");
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.a(any(Callable.class))).thenThrow(rejected);
            assertSame(rejected, assertThrows(RejectedExecutionException.class,
                    () -> engine.startStudioEntryHydrology(0, 0)));
        }
        verify(engine, never()).acquireGenerationLease(any());
    }

    @Test
    public void queuedEntrySkipsClosedOrHotloadingSessionAndDrainsWithoutErrors() throws Exception {
        for (boolean teardown : new boolean[]{false, true}) {
            EngineBackgroundTasks tasks = new EngineBackgroundTasks();
            tasks.openBackgroundTaskAdmission();
            IrisEngine engine = engine(tasks);
            GenerationSessionManager sessions = new GenerationSessionManager();
            when(engine.acquireGenerationLease(any())).thenAnswer(invocation -> sessions.acquire("entry"));
            AtomicReference<Callable<Void>> queued = new AtomicReference<>();
            try (MockedStatic<J> scheduler = scheduler(queued);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                engine.startStudioEntryHydrology(0, 0);
                assertEquals(0, sessions.activeLeases());
                closing(engine).set(true);
                tasks.closeBackgroundTaskAdmission();
                sessions.sealAndAwait("transition", 0L, teardown);
                queued.get().call();
                tasks.drainBackgroundTasks("transition").requireComplete("transition");
                assertEquals(0, sessions.activeLeases());
                verify(engine, never()).acquireGenerationLease(any());
                logging.verifyNoInteractions();
            }
        }
    }

    @Test
    public void admissionRaceCancelsOnlyWhenClosing() throws Exception {
        for (boolean transition : new boolean[]{false, true}) {
            EngineBackgroundTasks tasks = new EngineBackgroundTasks();
            tasks.openBackgroundTaskAdmission();
            IrisEngine engine = engine(tasks);
            GenerationSessionException rejected = new GenerationSessionException("sealed", false);
            when(engine.acquireGenerationLease(any())).thenAnswer(invocation -> {
                closing(engine).set(transition);
                throw rejected;
            });
            AtomicReference<Callable<Void>> queued = new AtomicReference<>();
            try (MockedStatic<J> scheduler = scheduler(queued);
                 MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
                engine.startStudioEntryHydrology(0, 0);
                if (transition) {
                    queued.get().call();
                    logging.verifyNoInteractions();
                } else {
                    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> queued.get().call());
                    assertSame(rejected, failure.getCause());
                    logging.verify(() -> IrisLogging.reportError(failure));
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void canceledFutureBeforeStartOwnsNoGenerationLease() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        IrisEngine engine = engine(tasks);
        GenerationSessionManager sessions = new GenerationSessionManager();
        when(engine.acquireGenerationLease(any())).thenAnswer(invocation -> sessions.acquire("entry"));
        AtomicReference<FutureTask<Void>> queued = new AtomicReference<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
                FutureTask<Void> future = new FutureTask<>(invocation.<Callable<Void>>getArgument(0));
                queued.set(future);
                return future;
            });
            engine.startStudioEntryHydrology(0, 0);
            assertTrue(queued.get().cancel(false));
            queued.get().run();
            sessions.sealAndAwait("close", 0L, true);
            assertEquals(0, sessions.activeLeases());
            verify(engine, never()).acquireGenerationLease(any());
        }
    }

    @Test
    public void activeLeaseFinishesWhileTransitionHoldsLifecycleLockAndDrains() throws Exception {
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        IrisEngine engine = engine(tasks);
        IrisHydrologyRuntime hydrology = hydrology(engine);
        GenerationSessionManager sessions = new GenerationSessionManager();
        CountDownLatch admitted = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        when(engine.acquireGenerationLease(any())).thenAnswer(invocation -> {
            GenerationSessionLease lease = sessions.acquire("entry");
            admitted.countDown();
            assertTrue(proceed.await(5, TimeUnit.SECONDS));
            return lease;
        });
        AtomicReference<Callable<Void>> queued = new AtomicReference<>();
        try (MockedStatic<J> scheduler = scheduler(queued);
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            engine.startStudioEntryHydrology(0, 0);
            Future<Void> worker = executor.submit(queued.get());
            assertTrue(admitted.await(5, TimeUnit.SECONDS));
            Future<?> drain = executor.submit(() -> {
                synchronized (engine.lifecycleLock) {
                    closing(engine).set(true);
                    tasks.closeBackgroundTaskAdmission();
                    sessions.sealAndAwait("hotload", 5000L);
                    tasks.drainBackgroundTasks("hotload").requireComplete("hotload");
                }
                return null;
            });
            try {
                awaitSealed(sessions);
                assertFalse(drain.isDone());
            } finally {
                proceed.countDown();
            }
            worker.get(5, TimeUnit.SECONDS);
            drain.get(5, TimeUnit.SECONDS);
            verify(hydrology).prepareChunkColumns(0, 0);
            assertEquals(0, sessions.activeLeases());
        }
    }

    private void awaitSealed(GenerationSessionManager sessions) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (GenerationSessionLease probe = sessions.acquire("seal probe")) {
                Thread.yield();
            } catch (GenerationSessionException sealed) {
                return;
            }
        }
        throw new AssertionError("Session admission did not seal");
    }

    @SuppressWarnings("unchecked")
    private MockedStatic<J> scheduler(AtomicReference<Callable<Void>> queued) {
        MockedStatic<J> scheduler = mockStatic(J.class);
        scheduler.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
            queued.set(invocation.getArgument(0));
            return new CompletableFuture<Void>();
        });
        return scheduler;
    }

    private IrisHydrologyRuntime hydrology(IrisEngine engine) {
        IrisComplex complex = mock(IrisComplex.class);
        IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        return hydrology;
    }

    private AtomicBoolean closing(IrisEngine engine) throws Exception {
        Field field = IrisEngine.class.getDeclaredField("closing");
        field.setAccessible(true);
        return (AtomicBoolean) field.get(engine);
    }

    @Test
    public void exactOriginChunkDemandsAllFourTilesConcurrently() throws Exception {
        HydrologyPlanner planner = mock(HydrologyPlanner.class);
        HydrologyPlannerSettings settings = mock(HydrologyPlannerSettings.class);
        when(settings.routing()).thenReturn(HydrologyPlannerSettings.defaults().routing());
        when(settings.publicationRadius()).thenReturn(64);
        when(planner.settings()).thenReturn(settings);
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        Set<HydrologyTileKey> keys = ConcurrentHashMap.newKeySet();
        when(planner.plan(any())).thenAnswer(invocation -> {
            assertTrue(keys.add(invocation.getArgument(0)));
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            HydrologyTile tile = mock(HydrologyTile.class);
            when(tile.columnAt(anyInt(), anyInt())).thenReturn(Optional.empty());
            return tile;
        });
        try (ExecutorService planning = Executors.newFixedThreadPool(4);
             ExecutorService caller = Executors.newSingleThreadExecutor()) {
            HydrologyTileCache cache = new HydrologyTileCache(planner, 16, planning);
            cache.setNeighbourPrefetchEnabled(false);
            Future<?> preparation = caller.submit(() -> cache.prepareChunkColumns(0, 0));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertEquals(Set.of(new HydrologyTileKey(-1, -1), new HydrologyTileKey(0, -1),
                        new HydrologyTileKey(-1, 0), new HydrologyTileKey(0, 0)), keys);
            } finally {
                release.countDown();
            }
            preparation.get(5, TimeUnit.SECONDS);
            cache.prepareChunkColumns(0, 0);
            assertEquals(4, keys.size());
            cache.close();
        }
    }

    private IrisEngine engine(EngineBackgroundTasks tasks) throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        Field field = IrisEngine.class.getDeclaredField("backgroundTasks");
        field.setAccessible(true);
        field.set(engine, tasks);
        Field closing = IrisEngine.class.getDeclaredField("closing");
        closing.setAccessible(true);
        closing.set(engine, new AtomicBoolean());
        Field scopes = IrisEngine.class.getDeclaredField("generationRuntimeScopes");
        scopes.setAccessible(true);
        scopes.set(engine, new GenerationRuntimeScopeState());
        Field lifecycle = IrisEngine.class.getDeclaredField("lifecycleLock");
        lifecycle.setAccessible(true);
        lifecycle.set(engine, new Object());
        doCallRealMethod().when(engine).startStudioEntryHydrology(anyInt(), anyInt());
        return engine;
    }
}
