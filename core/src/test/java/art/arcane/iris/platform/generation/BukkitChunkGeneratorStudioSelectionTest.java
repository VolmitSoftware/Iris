package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.studio.StudioMode;
import art.arcane.iris.studio.generation.StudioGenerator;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorStudioSelectionTest {
    @Test
    public void concurrentInitialSelectionWaitsForPublishedGenerator() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch injecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        doAnswer(invocation -> {
            injecting.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            fixture.generator.setStudioGenerator(fixture.selected);
            return null;
        }).when(fixture.mode).inject(fixture.generator);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<StudioGenerator> first = workers.submit(() -> resolve(fixture.generator));
            assertTrue(injecting.await(5L, TimeUnit.SECONDS));
            Future<StudioGenerator> second = workers.submit(() -> {
                secondAttempting.countDown();
                return resolve(fixture.generator);
            });
            assertTrue(secondAttempting.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(150L, TimeUnit.MILLISECONDS));
            release.countDown();

            assertSame(fixture.selected, first.get(5L, TimeUnit.SECONDS));
            assertSame(fixture.selected, second.get(5L, TimeUnit.SECONDS));
            verify(fixture.mode).inject(fixture.generator);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedSelectionDoesNotPublishTheModeOrLoseThePreviousGenerator() throws Exception {
        Fixture fixture = new Fixture();
        StudioGenerator previous = mock(StudioGenerator.class);
        fixture.generator.setStudioGenerator(previous);
        IllegalStateException failure = new IllegalStateException("studio selection failed");
        doAnswer(invocation -> {
            throw failure;
        }).doAnswer(invocation -> {
            fixture.generator.setStudioGenerator(fixture.selected);
            return null;
        }).when(fixture.mode).inject(fixture.generator);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> resolve(fixture.generator)));
        assertSame(StudioMode.NORMAL, fixture.generator.getLastMode());
        assertSame(previous, fixture.generator.getStudioGenerator());
        assertSame(fixture.selected, resolve(fixture.generator));
        assertSame(fixture.mode, fixture.generator.getLastMode());
        verify(fixture.mode, times(2)).inject(fixture.generator);
    }

    @Test
    public void initialWorldTouchSelectsStudioBeforePublishingSetup() throws Exception {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            assertFalse(fixture.setup.get());
            fixture.generator.setStudioGenerator(fixture.selected);
            return null;
        }).when(fixture.mode).inject(fixture.generator);
        try (MockedStatic<BukkitChunkGenerator> methods = mockStatic(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
             MockedConstruction<IrisEngine> engines = mockConstruction(IrisEngine.class,
                     (created, context) -> when(created.getDimension()).thenReturn(fixture.dimension))) {
            methods.when(() -> BukkitChunkGenerator.shouldRunStudioHotload(true, false, false)).thenReturn(false);

            fixture.generator.touch(mock(World.class));

            assertTrue(fixture.setup.get());
            assertSame(fixture.selected, fixture.generator.getStudioGenerator());
            assertSame(fixture.mode, fixture.generator.getLastMode());
            assertEquals(1, engines.constructed().size());
            verify(fixture.mode).inject(fixture.generator);
        }
    }

    @Test
    public void failedInitialSelectionClosesItsEngineAndRejectsSubsequentTouches() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("initial studio selection failed");
        IllegalStateException closeFailure = new IllegalStateException("engine close failed");
        doAnswer(invocation -> {
            throw failure;
        }).when(fixture.mode).inject(fixture.generator);
        try (MockedStatic<BukkitChunkGenerator> methods = mockStatic(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
             MockedConstruction<IrisEngine> engines = mockConstruction(IrisEngine.class, (created, context) -> {
                 when(created.getDimension()).thenReturn(fixture.dimension);
                 doAnswer(invocation -> {
                     throw closeFailure;
                 }).when(created).close();
             })) {
            methods.when(() -> BukkitChunkGenerator.shouldRunStudioHotload(true, false, false)).thenReturn(false);

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.generator.touch(mock(World.class))));
            assertFalse(fixture.setup.get());
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.generator.touch(mock(World.class))));
            assertEquals(1, engines.constructed().size());
            assertEquals(1, failure.getSuppressed().length);
            assertSame(closeFailure, failure.getSuppressed()[0]);
            verify(engines.constructed().getFirst()).close();
        }
    }

    @Test
    public void productionTerrainIgnoresPackStudioMode() throws Exception {
        Fixture fixture = new Fixture();
        field(fixture.generator, "studio", false);

        assertNull(resolve(fixture.generator));
        assertSame(StudioMode.NORMAL, fixture.generator.getLastMode());
        verify(fixture.mode, never()).inject(fixture.generator);
    }

    private static StudioGenerator resolve(BukkitChunkGenerator generator) throws Exception {
        Method method = BukkitChunkGenerator.class.getDeclaredMethod("computeStudioGenerator");
        method.setAccessible(true);
        try {
            method.invoke(generator);
            return generator.getStudioGenerator();
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw failure;
        }
    }

    private static void field(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final IrisDimension dimension = mock(IrisDimension.class);
        private final StudioMode mode = mock(StudioMode.class);
        private final StudioGenerator selected = mock(StudioGenerator.class);
        private final AtomicBoolean setup = new AtomicBoolean();

        private Fixture() throws Exception {
            Engine engine = mock(Engine.class);
            EngineTarget target = mock(EngineTarget.class);
            IrisWorld world = mock(IrisWorld.class);
            when(world.hasPlatformWorld()).thenReturn(true);
            when(engine.getDimension()).thenReturn(dimension);
            when(target.getDimension()).thenReturn(dimension);
            when(dimension.getLoadKey()).thenReturn("studio-selection-test");
            when(dimension.getStudioMode()).thenReturn(mode);
            doReturn(target).when(generator).getTarget();
            field(generator, "studio", true);
            field(generator, "world", world);
            field(generator, "setup", setup);
            field(generator, "startupContentReady", CompletableFuture.completedFuture(null));
            field(generator, "startupReady", CompletableFuture.completedFuture(null));
            field(generator, "lock", new ReentrantLock());
            field(generator, "targetCache", new AtomicCache<EngineTarget>());
            field(generator, "populators", new KList<>());
            field(generator, "studioEntryBootstrapActive", new AtomicBoolean(true));
            field(generator, "validatedDimension", dimension);
            field(generator, "validatedWorldContract", new IrisDimensionRuntimeContract("iris:test", 0, 256, 256));
            generator.setEngine(engine);
            generator.setLastMode(StudioMode.NORMAL);
        }
    }
}
