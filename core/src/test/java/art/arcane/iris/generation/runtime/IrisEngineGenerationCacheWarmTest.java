package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.runtime.EngineBackgroundTasks.BackgroundTaskDrain;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class IrisEngineGenerationCacheWarmTest {
    @Test
    public void newWorldAndStudioReturnBeforeWarmButReadinessWaits() throws Exception {
        for (IrisEngine.InitializationMode mode : new IrisEngine.InitializationMode[]{
                IrisEngine.InitializationMode.WORLD_CREATION, IrisEngine.InitializationMode.STUDIO}) {
            Fixture fixture = fixture();
            AtomicReference<Callable<Void>> queued = new AtomicReference<>();
            try (MockedStatic<J> scheduling = scheduler(queued);
                 MockedStatic<GenerationCacheWarmer> warmer = mockStatic(GenerationCacheWarmer.class)) {
                fixture.engine().prepareGenerationCaches(mode, System.nanoTime());
                assertNotNull(queued.get());
                assertFalse(fixture.ready().isDone());
                warmer.verifyNoInteractions();
                queued.get().call();
                fixture.engine().awaitGenerationCacheWarm();
                warmer.verify(() -> GenerationCacheWarmer.warm(fixture.engine()));
                fixture.tasks().drainBackgroundTasks("test").requireComplete("test");
            }
        }
    }

    @Test
    public void restoredRuntimeStillWarmsSynchronously() throws Exception {
        Fixture fixture = fixture();
        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<GenerationCacheWarmer> warmer = mockStatic(GenerationCacheWarmer.class)) {
            fixture.engine().prepareGenerationCaches(IrisEngine.InitializationMode.RUNTIME, System.nanoTime());
            assertTrue(fixture.ready().isDone());
            scheduling.verifyNoInteractions();
            warmer.verify(() -> GenerationCacheWarmer.warm(fixture.engine()));
        }
    }

    @Test
    public void failedWarmIsLoggedAndBothGenerationPathsRejectItsCause() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<Callable<Void>> queued = new AtomicReference<>();
        IllegalStateException expected = new IllegalStateException("cache warm failed");
        IrisData data = mock(IrisData.class);
        IrisOreGenerator ore = mock(IrisOreGenerator.class);
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey("failing-ore");
        biome.setOres(new KList<>(ore));
        when(fixture.engine().getData()).thenReturn(data);
        when(fixture.engine().getSeedManager()).thenReturn(mock(SeedManager.class));
        when(fixture.engine().getAllBiomes()).thenReturn(new KList<>(biome));
        doThrow(expected).when(ore).warm(any(RNG.class), same(data));
        try (MockedStatic<J> scheduling = scheduler(queued);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            fixture.engine().prepareGenerationCaches(IrisEngine.InitializationMode.WORLD_CREATION, System.nanoTime());
            assertSame(expected, assertThrows(IllegalStateException.class, () -> queued.get().call()));
            assertTrue(fixture.ready().isCompletedExceptionally());
            logging.verify(() -> IrisLogging.reportError(expected));
            assertSame(expected, assertThrows(IllegalStateException.class,
                    () -> fixture.engine().generate(0, 0, null, null, false)).getCause());
            assertSame(expected, assertThrows(IllegalStateException.class,
                    () -> fixture.engine().generateMatter(0, 0, false, null)).getCause());
            fixture.tasks().drainBackgroundTasks("test").requireComplete("test");
        }
    }

    @Test
    public void runningWarmRemainsOwnedUntilShutdownDrainCompletes() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<Callable<Void>> queued = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (MockedStatic<J> scheduling = scheduler(queued)) {
            fixture.engine().prepareGenerationCaches(IrisEngine.InitializationMode.WORLD_CREATION, System.nanoTime());
            Future<Void> warming = workers.submit(() -> {
                try (MockedStatic<GenerationCacheWarmer> warmer = mockStatic(GenerationCacheWarmer.class)) {
                    warmer.when(() -> GenerationCacheWarmer.warm(fixture.engine())).thenAnswer(invocation -> {
                        started.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                        return null;
                    });
                    return queued.get().call();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            fixture.tasks().closeBackgroundTaskAdmission();
            Future<BackgroundTaskDrain> drain = workers.submit(() -> fixture.tasks().drainBackgroundTasks("close"));
            assertThrows(TimeoutException.class, () -> drain.get(100, TimeUnit.MILLISECONDS));
            assertFalse(fixture.ready().isDone());
            release.countDown();
            warming.get(5, TimeUnit.SECONDS);
            drain.get(5, TimeUnit.SECONDS).requireComplete("close");
            fixture.engine().awaitGenerationCacheWarm();
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void closedTaskAdmissionRejectsNewWorldWarmSynchronously() throws Exception {
        Fixture fixture = fixture();
        fixture.tasks().closeBackgroundTaskAdmission();
        assertThrows(IllegalStateException.class,
                () -> fixture.engine().prepareGenerationCaches(IrisEngine.InitializationMode.WORLD_CREATION, System.nanoTime()));
    }

    private static Fixture fixture() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        EngineBackgroundTasks tasks = new EngineBackgroundTasks();
        tasks.openBackgroundTaskAdmission();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        set(engine, "backgroundTasks", tasks);
        set(engine, "generationCacheWarm", ready);
        set(engine, "diagnostics", mock(EngineDiagnostics.class));
        doCallRealMethod().when(engine).prepareGenerationCaches(any(), anyLong());
        doCallRealMethod().when(engine).awaitGenerationCacheWarm();
        doCallRealMethod().when(engine).generate(0, 0, null, null, false);
        doCallRealMethod().when(engine).generateMatter(0, 0, false, null);
        return new Fixture(engine, tasks, ready);
    }

    @SuppressWarnings("unchecked")
    private static MockedStatic<J> scheduler(AtomicReference<Callable<Void>> queued) {
        MockedStatic<J> scheduling = mockStatic(J.class);
        scheduling.when(() -> J.a(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Void> task = invocation.getArgument(0);
            queued.set(task);
            return new FutureTask<>(task);
        });
        return scheduling;
    }

    private static void set(IrisEngine engine, String name, Object value) throws Exception {
        Field field = IrisEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private record Fixture(IrisEngine engine, EngineBackgroundTasks tasks, CompletableFuture<Void> ready) {
    }
}
